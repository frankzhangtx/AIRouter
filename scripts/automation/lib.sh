#!/usr/bin/env bash

# Shared helpers for the scheduled coding quality gate. This file is sourced by
# command scripts; it intentionally does not enable set -e for its callers.

AUTOMATION_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -n "${AUTOMATION_PROJECT_ROOT:-}" ]]; then
    if [[ "${AUTOMATION_TEST_MODE:-0}" != "1" ]]; then
        printf 'ERROR: AUTOMATION_PROJECT_ROOT is reserved for the test suite.\n' >&2
        return 1 2>/dev/null || exit 1
    fi
    AUTOMATION_ROOT="$(cd "$AUTOMATION_PROJECT_ROOT" && pwd)"
else
    AUTOMATION_ROOT="$(git -C "$AUTOMATION_SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)" || {
        printf 'ERROR: scripts must run inside a Git worktree.\n' >&2
        return 1 2>/dev/null || exit 1
    }
fi

AUTOMATION_DIR="$AUTOMATION_ROOT/automation"
AUTOMATION_CONFIG="$AUTOMATION_DIR/config.json"
AUTOMATION_TASKS_DIR="$AUTOMATION_DIR/tasks"
AUTOMATION_STATE_DIR="$AUTOMATION_DIR/state"
AUTOMATION_EVIDENCE_DIR="$AUTOMATION_DIR/evidence"
AUTOMATION_LOCKS_DIR="$AUTOMATION_DIR/locks"

automation_info() {
    printf '[automation] %s\n' "$*"
}

automation_warn() {
    printf '[automation] WARN: %s\n' "$*" >&2
}

automation_die() {
    printf '[automation] ERROR: %s\n' "$*" >&2
    return 1
}

automation_now() {
    date -u '+%Y-%m-%dT%H:%M:%SZ'
}

automation_require_command() {
    command -v "$1" >/dev/null 2>&1 || automation_die "required command is missing: $1"
}

automation_require_layout() {
    [[ -f "$AUTOMATION_CONFIG" ]] || automation_die "missing $AUTOMATION_CONFIG"
    [[ -d "$AUTOMATION_TASKS_DIR" ]] || automation_die "missing $AUTOMATION_TASKS_DIR"
    [[ -d "$AUTOMATION_STATE_DIR" ]] || automation_die "missing $AUTOMATION_STATE_DIR"
    [[ -d "$AUTOMATION_EVIDENCE_DIR" ]] || automation_die "missing $AUTOMATION_EVIDENCE_DIR"
    [[ -d "$AUTOMATION_LOCKS_DIR" ]] || automation_die "missing $AUTOMATION_LOCKS_DIR"
}

automation_validate_task_id() {
    local task_id="${1:-}"
    [[ "$task_id" =~ ^TASK-[A-Z0-9-]+$ ]] || automation_die "invalid task ID: ${task_id:-<empty>}"
}

automation_contract_path() {
    local task_id="$1"
    printf '%s/%s.json\n' "$AUTOMATION_TASKS_DIR" "$task_id"
}

automation_state_path() {
    local task_id="$1"
    printf '%s/%s.json\n' "$AUTOMATION_STATE_DIR" "$task_id"
}

automation_evidence_path() {
    local task_id="$1"
    printf '%s/%s\n' "$AUTOMATION_EVIDENCE_DIR" "$task_id"
}

automation_read_state() {
    local task_id="$1"
    local state_file
    state_file="$(automation_state_path "$task_id")"
    [[ -f "$state_file" ]] || automation_die "state does not exist for $task_id"
    jq -er '.state' "$state_file"
}

automation_config_value() {
    local query="$1"
    jq -r "$query" "$AUTOMATION_CONFIG"
}

automation_path_matches() {
    local path="$1"
    local pattern="$2"
    local prefix

    if [[ "$pattern" == */ ]]; then
        [[ "$path" == "$pattern"* ]]
        return
    fi

    if [[ "$pattern" == *'/**' ]]; then
        prefix="${pattern:0:${#pattern}-3}"
        [[ "$path" == "$prefix" || "$path" == "$prefix/"* ]]
        return
    fi

    case "$path" in
        $pattern) return 0 ;;
        *) return 1 ;;
    esac
}

automation_array_matches_path() {
    local json_file="$1"
    local query="$2"
    local path="$3"
    local pattern

    while IFS= read -r pattern; do
        if automation_path_matches "$path" "$pattern"; then
            return 0
        fi
    done < <(jq -r "$query[]" "$json_file")

    return 1
}

automation_changed_paths() {
    {
        git -C "$AUTOMATION_ROOT" diff --name-only --diff-filter=ACMRTUXB HEAD --
        git -C "$AUTOMATION_ROOT" ls-files --others --exclude-standard
    } | LC_ALL=C sort -u
}

automation_worktree_is_clean() {
    [[ -z "$(git -C "$AUTOMATION_ROOT" status --porcelain --untracked-files=all)" ]]
}

automation_acquire_lock() {
    local task_id="$1"
    local lock_dir="$AUTOMATION_LOCKS_DIR/$task_id.lock"
    if ! mkdir "$lock_dir" 2>/dev/null; then
        automation_die "task lock is already held: $task_id"
        return 1
    fi
    printf '%s\n' "$$" > "$lock_dir/pid"
    printf '%s\n' "$(automation_now)" > "$lock_dir/acquired-at"
    AUTOMATION_HELD_LOCK="$lock_dir"
}

automation_release_lock() {
    if [[ -n "${AUTOMATION_HELD_LOCK:-}" && -d "$AUTOMATION_HELD_LOCK" ]]; then
        rm -f "$AUTOMATION_HELD_LOCK/pid" "$AUTOMATION_HELD_LOCK/acquired-at"
        rmdir "$AUTOMATION_HELD_LOCK" 2>/dev/null || true
    fi
    AUTOMATION_HELD_LOCK=""
}

automation_transition_allowed() {
    local from="$1"
    local to="$2"
    local actor="$3"

    case "$from:$to:$actor" in
        APPROVED_CONTRACT:PENDING:human) return 0 ;;
        TEST_FAILED:PENDING:human) return 0 ;;
        BLOCKED:PENDING:human) return 0 ;;
        CHANGES_REQUESTED:PENDING:human) return 0 ;;
        PENDING:CODING:coder-launcher) return 0 ;;
        PENDING:BLOCKED:preflight) return 0 ;;
        PENDING:BLOCKED:coder) return 0 ;;
        CODING:BLOCKED:preflight) return 0 ;;
        CODING:BLOCKED:coder) return 0 ;;
        CODING:READY_FOR_REVIEW:quality-gate) return 0 ;;
        CODING:TEST_FAILED:quality-gate) return 0 ;;
        CODING:BLOCKED:quality-gate) return 0 ;;
        READY_FOR_REVIEW:AWAITING_HUMAN:reviewer) return 0 ;;
        READY_FOR_REVIEW:CHANGES_REQUESTED:reviewer) return 0 ;;
        *) return 1 ;;
    esac
}

automation_initialize_state() {
    local task_id="$1"
    local actor="$2"
    local note="$3"
    local state_file evidence_dir tmp now

    state_file="$(automation_state_path "$task_id")"
    [[ ! -e "$state_file" ]] || automation_die "state already exists for $task_id"
    evidence_dir="$(automation_evidence_path "$task_id")"
    mkdir -p "$evidence_dir"
    now="$(automation_now)"
    tmp="$(mktemp "$AUTOMATION_STATE_DIR/.${task_id}.XXXXXX")"
    jq -n \
        --arg taskId "$task_id" \
        --arg state "APPROVED_CONTRACT" \
        --arg updatedAt "$now" \
        --arg updatedBy "$actor" \
        --arg note "$note" \
        '{taskId: $taskId, state: $state, revision: 1, updatedAt: $updatedAt, updatedBy: $updatedBy, note: $note}' \
        > "$tmp"
    mv "$tmp" "$state_file"
    jq -nc \
        --arg taskId "$task_id" \
        --arg from "" \
        --arg to "APPROVED_CONTRACT" \
        --arg actor "$actor" \
        --arg at "$now" \
        --arg note "$note" \
        '{taskId: $taskId, from: $from, to: $to, actor: $actor, at: $at, note: $note}' \
        >> "$evidence_dir/transitions.jsonl"
}

automation_transition_state() {
    local task_id="$1"
    local expected="$2"
    local next="$3"
    local actor="$4"
    local note="$5"
    local state_file evidence_dir actual revision now tmp

    automation_acquire_lock "$task_id" || return 1
    state_file="$(automation_state_path "$task_id")"
    evidence_dir="$(automation_evidence_path "$task_id")"
    mkdir -p "$evidence_dir"

    if [[ ! -f "$state_file" ]]; then
        automation_release_lock
        automation_die "state does not exist for $task_id"
        return 1
    fi

    actual="$(jq -er '.state' "$state_file")" || {
        automation_release_lock
        automation_die "invalid state file for $task_id"
        return 1
    }
    if [[ "$actual" != "$expected" ]]; then
        automation_release_lock
        automation_die "state mismatch for $task_id: expected $expected, found $actual"
        return 1
    fi
    if ! automation_transition_allowed "$expected" "$next" "$actor"; then
        automation_release_lock
        automation_die "forbidden transition: $expected -> $next by $actor"
        return 1
    fi

    revision="$(jq -er '.revision' "$state_file")"
    revision=$((revision + 1))
    now="$(automation_now)"
    tmp="$(mktemp "$AUTOMATION_STATE_DIR/.${task_id}.XXXXXX")"
    jq \
        --arg state "$next" \
        --argjson revision "$revision" \
        --arg updatedAt "$now" \
        --arg updatedBy "$actor" \
        --arg note "$note" \
        '.state = $state | .revision = $revision | .updatedAt = $updatedAt | .updatedBy = $updatedBy | .note = $note' \
        "$state_file" > "$tmp"
    mv "$tmp" "$state_file"
    jq -nc \
        --arg taskId "$task_id" \
        --arg from "$expected" \
        --arg to "$next" \
        --arg actor "$actor" \
        --arg at "$now" \
        --arg note "$note" \
        '{taskId: $taskId, from: $from, to: $to, actor: $actor, at: $at, note: $note}' \
        >> "$evidence_dir/transitions.jsonl"
    automation_release_lock
    automation_info "$task_id: $expected -> $next"
}

automation_record_json() {
    local destination="$1"
    local tmp
    mkdir -p "$(dirname "$destination")"
    tmp="$(mktemp "$(dirname "$destination")/.record.XXXXXX")"
    cat > "$tmp"
    mv "$tmp" "$destination"
}
