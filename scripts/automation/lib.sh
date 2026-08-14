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

automation_git_common_dir="$(git -C "$AUTOMATION_ROOT" rev-parse --git-common-dir 2>/dev/null)" || {
    printf 'ERROR: unable to resolve Git common directory.\n' >&2
    return 1 2>/dev/null || exit 1
}
if [[ "$automation_git_common_dir" != /* ]]; then
    automation_git_common_dir="$AUTOMATION_ROOT/$automation_git_common_dir"
fi
AUTOMATION_GIT_COMMON_DIR="$(cd "$automation_git_common_dir" && pwd)"
unset automation_git_common_dir

if [[ -n "${AUTOMATION_RUNTIME_ROOT:-}" ]]; then
    if [[ "${AUTOMATION_TEST_MODE:-0}" != "1" ]]; then
        printf 'ERROR: AUTOMATION_RUNTIME_ROOT is reserved for the test suite.\n' >&2
        return 1 2>/dev/null || exit 1
    fi
elif [[ "${AUTOMATION_TEST_MODE:-0}" == "1" ]]; then
    AUTOMATION_RUNTIME_ROOT="$AUTOMATION_DIR"
else
    AUTOMATION_RUNTIME_ROOT="$AUTOMATION_GIT_COMMON_DIR/automation-runtime"
fi

AUTOMATION_STATE_DIR="$AUTOMATION_RUNTIME_ROOT/state"
AUTOMATION_EVIDENCE_DIR="$AUTOMATION_RUNTIME_ROOT/evidence"
AUTOMATION_LOCKS_DIR="$AUTOMATION_RUNTIME_ROOT/locks"
AUTOMATION_WORKSPACES_DIR="$AUTOMATION_RUNTIME_ROOT/workspaces"

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
}

automation_validate_config() {
    automation_require_layout
    jq -e '
        .schemaVersion == 2 and
        (.enabled | type == "boolean") and
        (.mode == "shadow" or .mode == "orchestrated") and
        .requireDedicatedWorktree == true and
        (.worktreeBase | type == "string") and
        (.maxFixLoops | type == "number" and . >= 0 and . <= 1 and floor == .) and
        (.maxReviewCycles | type == "number" and . >= 0 and . <= 2 and floor == .) and
        (.autoCleanupWorktrees | type == "boolean") and
        .pushAfterAcceptance == false and
        (.approvalPhrases.proposal | type == "string" and length >= 4) and
        (.approvalPhrases.contract | type == "string" and length >= 4) and
        (.approvalPhrases.acceptance | type == "string" and length >= 4) and
        (.plugins.scheduler | type == "string" and length > 0) and
        (.plugins.superpowers | type == "string" and length > 0) and
        (.requiredSkills | type == "array" and length >= 6) and
        (.protectedPaths | type == "array" and length > 0)
    ' "$AUTOMATION_CONFIG" >/dev/null || automation_die "automation/config.json is invalid"
}

automation_require_orchestrated() {
    automation_validate_config
    [[ "$(automation_config_value '.enabled')" == "true" ]] || automation_die "automation is disabled"
    [[ "$(automation_config_value '.mode')" == "orchestrated" ]] || automation_die "automation mode is not orchestrated"
}

automation_ensure_runtime_layout() {
    mkdir -p \
        "$AUTOMATION_STATE_DIR" \
        "$AUTOMATION_EVIDENCE_DIR" \
        "$AUTOMATION_LOCKS_DIR" \
        "$AUTOMATION_WORKSPACES_DIR"
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

automation_workspace_path() {
    local task_id="$1"
    printf '%s/%s.json\n' "$AUTOMATION_WORKSPACES_DIR" "$task_id"
}

automation_origin_path() {
    local task_id="$1"
    printf '%s/origin.json\n' "$(automation_evidence_path "$task_id")"
}

automation_approvals_path() {
    local task_id="$1"
    printf '%s/approvals.jsonl\n' "$(automation_evidence_path "$task_id")"
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

automation_require_approval() {
    local kind="$1"
    local supplied="$2"
    local expected
    expected="$(jq -er --arg kind "$kind" '.approvalPhrases[$kind]' "$AUTOMATION_CONFIG")" || {
        automation_die "unknown approval kind: $kind"
        return 1
    }
    [[ "$supplied" == "$expected" ]] || automation_die "approval text does not match the configured $kind confirmation"
}

automation_file_sha256() {
    local file="$1"
    [[ -f "$file" ]] || automation_die "file does not exist: $file"
    shasum -a 256 "$file" | awk '{print $1}'
}

automation_current_branch() {
    local root="${1:-$AUTOMATION_ROOT}"
    local branch
    branch="$(git -C "$root" symbolic-ref --quiet --short HEAD 2>/dev/null)" || {
        automation_die "detached HEAD is not supported: $root"
        return 1
    }
    printf '%s\n' "$branch"
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

automation_changed_paths_at() {
    local root="$1"
    {
        git -C "$root" diff --name-only HEAD --
        git -C "$root" ls-files --others --exclude-standard
    } | LC_ALL=C sort -u
}

automation_changed_paths() {
    automation_changed_paths_at "$AUTOMATION_ROOT"
}

automation_changed_paths_between() {
    local base="$1"
    local head="$2"
    git -C "$AUTOMATION_ROOT" diff --name-only "$base" "$head" -- | LC_ALL=C sort -u
}

automation_worktree_diff_sha() {
    local root="${1:-$AUTOMATION_ROOT}"
    local path
    {
        git -C "$root" diff --binary HEAD --
        while IFS= read -r path; do
            [[ -n "$path" ]] || continue
            printf 'UNTRACKED %s\0' "$path"
            git -C "$root" hash-object -- "$path"
        done < <(git -C "$root" ls-files --others --exclude-standard | LC_ALL=C sort)
    } | shasum -a 256 | awk '{print $1}'
}

automation_worktree_is_clean() {
    local root="${1:-$AUTOMATION_ROOT}"
    [[ -z "$(git -C "$root" status --porcelain --untracked-files=all)" ]]
}

automation_create_lock_dir() {
    local lock_dir="$1"
    local label="$2"
    local owner_pid=""
    if mkdir "$lock_dir" 2>/dev/null; then
        return 0
    fi

    if [[ -f "$lock_dir/pid" ]]; then
        owner_pid="$(cat "$lock_dir/pid" 2>/dev/null || true)"
    fi
    if [[ "$owner_pid" =~ ^[0-9]+$ ]] && ! kill -0 "$owner_pid" 2>/dev/null; then
        automation_warn "removing stale $label lock owned by stopped PID $owner_pid"
        rm -f "$lock_dir/pid" "$lock_dir/acquired-at"
        rmdir "$lock_dir" 2>/dev/null || true
        if mkdir "$lock_dir" 2>/dev/null; then
            return 0
        fi
    fi

    automation_die "$label lock is already held${owner_pid:+ by PID $owner_pid}"
}

automation_acquire_lock() {
    local task_id="$1"
    local lock_dir="$AUTOMATION_LOCKS_DIR/$task_id.state.lock"
    automation_ensure_runtime_layout
    automation_create_lock_dir "$lock_dir" "task state $task_id" || return 1
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

automation_acquire_run_lock() {
    local task_id="$1"
    local lock_dir="$AUTOMATION_LOCKS_DIR/$task_id.run.lock"
    automation_ensure_runtime_layout
    automation_create_lock_dir "$lock_dir" "task orchestration $task_id" || return 1
    printf '%s\n' "$$" > "$lock_dir/pid"
    printf '%s\n' "$(automation_now)" > "$lock_dir/acquired-at"
    AUTOMATION_HELD_RUN_LOCK="$lock_dir"
}

automation_release_run_lock() {
    if [[ -n "${AUTOMATION_HELD_RUN_LOCK:-}" && -d "$AUTOMATION_HELD_RUN_LOCK" ]]; then
        rm -f "$AUTOMATION_HELD_RUN_LOCK/pid" "$AUTOMATION_HELD_RUN_LOCK/acquired-at"
        rmdir "$AUTOMATION_HELD_RUN_LOCK" 2>/dev/null || true
    fi
    AUTOMATION_HELD_RUN_LOCK=""
}

automation_transition_allowed() {
    local from="$1"
    local to="$2"
    local actor="$3"

    case "$from:$to:$actor" in
        CONTRACT_REVIEW:APPROVED_CONTRACT:human) return 0 ;;
        APPROVED_CONTRACT:PREPARING:orchestrator) return 0 ;;
        PREPARING:PENDING:orchestrator) return 0 ;;
        PREPARING:BLOCKED:orchestrator) return 0 ;;
        APPROVED_CONTRACT:PENDING:human) return 0 ;;
        TEST_FAILED:PENDING:human) return 0 ;;
        BLOCKED:PENDING:human) return 0 ;;
        CHANGES_REQUESTED:PENDING:human) return 0 ;;
        PENDING:CODING:coder-launcher) return 0 ;;
        PENDING:BLOCKED:preflight) return 0 ;;
        PENDING:BLOCKED:coder) return 0 ;;
        PENDING:BLOCKED:orchestrator) return 0 ;;
        CODING:BLOCKED:preflight) return 0 ;;
        CODING:BLOCKED:coder) return 0 ;;
        CODING:BLOCKED:orchestrator) return 0 ;;
        CODING:READY_FOR_REVIEW:quality-gate) return 0 ;;
        CODING:TEST_FAILED:quality-gate) return 0 ;;
        CODING:BLOCKED:quality-gate) return 0 ;;
        READY_FOR_REVIEW:REVIEWING:orchestrator) return 0 ;;
        READY_FOR_REVIEW:BLOCKED:orchestrator) return 0 ;;
        REVIEWING:AWAITING_HUMAN:reviewer) return 0 ;;
        REVIEWING:CHANGES_REQUESTED:reviewer) return 0 ;;
        REVIEWING:BLOCKED:orchestrator) return 0 ;;
        CHANGES_REQUESTED:CODING:orchestrator) return 0 ;;
        CHANGES_REQUESTED:NEEDS_HUMAN:orchestrator) return 0 ;;
        AWAITING_HUMAN:INTEGRATING:integrator) return 0 ;;
        INTEGRATING:COMPLETED:integrator) return 0 ;;
        INTEGRATING:INTEGRATION_BLOCKED:integrator) return 0 ;;
        *) return 1 ;;
    esac
}

automation_initialize_state() {
    local task_id="$1"
    local initial_state="$2"
    local actor="$3"
    local note="$4"
    local state_file evidence_dir tmp now

    automation_ensure_runtime_layout
    state_file="$(automation_state_path "$task_id")"
    [[ ! -e "$state_file" ]] || automation_die "state already exists for $task_id"
    evidence_dir="$(automation_evidence_path "$task_id")"
    mkdir -p "$evidence_dir"
    now="$(automation_now)"
    tmp="$(mktemp "$AUTOMATION_STATE_DIR/.${task_id}.XXXXXX")"
    jq -n \
        --arg taskId "$task_id" \
        --arg state "$initial_state" \
        --arg updatedAt "$now" \
        --arg updatedBy "$actor" \
        --arg note "$note" \
        '{taskId: $taskId, state: $state, revision: 1, updatedAt: $updatedAt, updatedBy: $updatedBy, note: $note}' \
        > "$tmp"
    mv "$tmp" "$state_file"
    jq -nc \
        --arg taskId "$task_id" \
        --arg from "" \
        --arg to "$initial_state" \
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

automation_append_json() {
    local destination="$1"
    local value
    mkdir -p "$(dirname "$destination")"
    value="$(cat)"
    jq -ce . <<< "$value" >> "$destination"
}

automation_worktree_base() {
    local configured repo_parent repo_name
    configured="$(jq -r '.worktreeBase // ""' "$AUTOMATION_CONFIG")"
    if [[ -n "${AUTOMATION_WORKTREE_BASE:-}" ]]; then
        if [[ "${AUTOMATION_TEST_MODE:-0}" != "1" ]]; then
            automation_die "AUTOMATION_WORKTREE_BASE is reserved for the test suite"
            return 1
        fi
        configured="$AUTOMATION_WORKTREE_BASE"
    fi
    if [[ -z "$configured" ]]; then
        repo_parent="$(dirname "$AUTOMATION_ROOT")"
        repo_name="$(basename "$AUTOMATION_ROOT")"
        configured="$repo_parent/$repo_name-worktrees"
    elif [[ "$configured" != /* ]]; then
        configured="$AUTOMATION_ROOT/$configured"
    fi
    case "$configured/" in
        "$AUTOMATION_ROOT/"*)
            automation_die "worktree base must be outside the source repository: $configured"
            return 1
            ;;
    esac
    printf '%s\n' "$configured"
}

automation_task_branch() {
    local task_id="$1"
    printf 'automation/%s\n' "$(tr '[:upper:]' '[:lower:]' <<< "$task_id")"
}
