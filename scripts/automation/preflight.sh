#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

shadow=0
task_id=""
for arg in "$@"; do
    case "$arg" in
        --shadow) shadow=1 ;;
        TASK-*) task_id="$arg" ;;
        *) printf 'Usage: %s [--shadow] [TASK-ID]\n' "$0" >&2; exit 2 ;;
    esac
done

fail_or_warn() {
    if [[ "$shadow" == "1" ]]; then
        automation_warn "$1"
        return 0
    fi
    automation_die "$1"
}

automation_require_layout
automation_require_command git
automation_require_command jq
automation_require_command rg
automation_require_command shasum
automation_require_command opencode
[[ -x "$AUTOMATION_ROOT/gradlew" ]] || automation_die "gradlew is missing or not executable"

jq -e '
    .schemaVersion == 1 and
    (.enabled | type == "boolean") and
    (.mode == "shadow" or .mode == "active") and
    (.requireDedicatedWorktree | type == "boolean") and
    (.dedicatedWorktree | type == "string") and
    .maxFixLoops == 1 and
    (.requiredSkills | type == "array" and length >= 5) and
    (.protectedPaths | type == "array" and length > 0)
' "$AUTOMATION_CONFIG" >/dev/null || automation_die "automation/config.json is invalid"

enabled="$(automation_config_value '.enabled')"
mode="$(automation_config_value '.mode')"
dedicated="$(jq -r '.dedicatedWorktree' "$AUTOMATION_CONFIG")"
require_dedicated="$(automation_config_value '.requireDedicatedWorktree')"

[[ "$enabled" == "true" ]] || fail_or_warn "automation is disabled"
[[ "$mode" == "active" ]] || fail_or_warn "automation mode is $mode, not active"

if [[ "$require_dedicated" == "true" ]]; then
    [[ -n "$dedicated" ]] || fail_or_warn "dedicatedWorktree is not configured"
    if [[ -n "$dedicated" ]]; then
        resolved_dedicated="$(cd "$dedicated" 2>/dev/null && pwd || true)"
        [[ "$resolved_dedicated" == "$AUTOMATION_ROOT" ]] || fail_or_warn "run must occur in configured dedicated worktree: $dedicated"
    fi
fi

if ! automation_worktree_is_clean; then
    fail_or_warn "Git worktree is dirty"
fi

if [[ -n "$task_id" ]]; then
    automation_validate_task_id "$task_id"
    "$SCRIPT_DIR/validate-contract.sh" "$task_id"
fi

if [[ "${AUTOMATION_TEST_MODE:-0}" == "1" ]]; then
    automation_info "test mode: OpenCode discovery checks skipped"
else
    discovery_dir="$(mktemp -d "${TMPDIR:-/tmp}/cctest-preflight.XXXXXX")"
    trap 'rm -rf "$discovery_dir"' EXIT

    if ! opencode debug config > "$discovery_dir/config.json" 2> "$discovery_dir/config.err"; then
        automation_die "OpenCode resolved config failed; see $discovery_dir/config.err"
    fi
    scheduler_pin="$(jq -r '.plugins.scheduler' "$AUTOMATION_CONFIG")"
    superpowers_pin="$(jq -r '.plugins.superpowers' "$AUTOMATION_CONFIG")"
    rg -F "$scheduler_pin" "$discovery_dir/config.json" >/dev/null || automation_die "resolved OpenCode config is missing $scheduler_pin"
    rg -F "$superpowers_pin" "$discovery_dir/config.json" >/dev/null || automation_die "resolved OpenCode config is missing pinned Superpowers"

    if ! opencode debug skill > "$discovery_dir/skills.txt" 2> "$discovery_dir/skills.err"; then
        automation_die "OpenCode skill discovery failed; see $discovery_dir/skills.err"
    fi
    while IFS= read -r skill; do
        rg -F "\"$skill\"" "$discovery_dir/skills.txt" >/dev/null || \
            rg -F "$skill" "$discovery_dir/skills.txt" >/dev/null || \
            automation_die "required skill is not discoverable: $skill"
    done < <(jq -r '.requiredSkills[]' "$AUTOMATION_CONFIG")

    opencode debug agent scheduled-coder > "$discovery_dir/coder-agent.json" 2> "$discovery_dir/coder-agent.err" || \
        automation_die "scheduled-coder agent is not discoverable"
    opencode debug agent scheduled-reviewer > "$discovery_dir/reviewer-agent.json" 2> "$discovery_dir/reviewer-agent.err" || \
        automation_die "scheduled-reviewer agent is not discoverable"
    opencode debug agent scheduled-planner > "$discovery_dir/planner-agent.json" 2> "$discovery_dir/planner-agent.err" || \
        automation_die "scheduled-planner agent is not discoverable"

    jq -e '
        def last_rule($permission; $pattern):
            [.permission[] | select(.permission == $permission and .pattern == $pattern) | .action][-1];
        (last_rule("*"; "*") == "deny") and
        (last_rule("edit"; "docs/plans/**") == "allow") and
        (last_rule("edit"; "automation/tasks/**") == "allow") and
        (last_rule("edit"; "app/**") == "deny") and
        (last_rule("bash"; "./scripts/automation/queue-task.sh *") == "deny") and
        (last_rule("schedule_job"; "*") == "deny") and
        (last_rule("task"; "*") == "deny") and
        (.tools.question == true) and
        (.tools.schedule_job == false) and
        (.tools.task == false)
    ' "$discovery_dir/planner-agent.json" >/dev/null || automation_die "scheduled-planner resolved permissions are unsafe"

    jq -e '
        def last_rule($permission; $pattern):
            [.permission[] | select(.permission == $permission and .pattern == $pattern) | .action][-1];
        (last_rule("*"; "*") == "deny") and
        (last_rule("edit"; "app/src/main/**") == "allow") and
        (last_rule("edit"; ".opencode/skills/**") == "deny") and
        (last_rule("schedule_job"; "*") == "deny") and
        (last_rule("task"; "*") == "deny") and
        (.tools.schedule_job == false) and
        (.tools.delete_job == false) and
        (.tools.task == false)
    ' "$discovery_dir/coder-agent.json" >/dev/null || automation_die "scheduled-coder resolved permissions are unsafe"

    jq -e '
        def last_rule($permission; $pattern):
            [.permission[] | select(.permission == $permission and .pattern == $pattern) | .action][-1];
        (last_rule("*"; "*") == "deny") and
        (last_rule("edit"; "*") == "deny") and
        (last_rule("schedule_job"; "*") == "deny") and
        (last_rule("task"; "*") == "deny") and
        (.tools.schedule_job == false) and
        (.tools.delete_job == false) and
        (.tools.task == false)
    ' "$discovery_dir/reviewer-agent.json" >/dev/null || automation_die "scheduled-reviewer resolved permissions are unsafe"
fi

automation_info "preflight completed (mode=$mode, shadow=$shadow${task_id:+, task=$task_id})"
