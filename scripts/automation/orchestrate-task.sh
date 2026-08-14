#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

task_id="${1:-}"
[[ "$#" -eq 1 ]] || { printf 'Usage: %s TASK-ID\n' "$0" >&2; exit 2; }
automation_validate_task_id "$task_id"
automation_require_orchestrated

workspace_file="$(automation_workspace_path "$task_id")"
[[ -f "$workspace_file" ]] || automation_die "workspace metadata is missing for $task_id"
task_root="$(jq -er '.taskWorktree' "$workspace_file")"
[[ -d "$task_root" ]] || automation_die "task worktree is missing: $task_root"

automation_acquire_run_lock "$task_id"
trap 'automation_release_run_lock' EXIT

if [[ "${AUTOMATION_SKIP_AGENT_RUN:-0}" == "1" ]]; then
    [[ "${AUTOMATION_TEST_MODE:-0}" == "1" ]] || automation_die "AUTOMATION_SKIP_AGENT_RUN is reserved for tests"
    automation_info "test mode: task is prepared; agent launch skipped"
    exit 0
fi
automation_require_command opencode

run_agent() {
    local role="$1"
    local prompt="$2"
    local cycle="$3"
    local log_file exit_code
    log_file="$(automation_evidence_path "$task_id")/${role}-cycle-${cycle}.log"
    automation_info "starting $role for $task_id in $task_root"
    set +e
    (
        cd "$task_root"
        opencode run --agent "$role" -- "$prompt"
    ) 2>&1 | tee "$log_file"
    exit_code=${PIPESTATUS[0]}
    set -e
    jq -nc \
        --arg taskId "$task_id" \
        --arg role "$role" \
        --argjson cycle "$cycle" \
        --arg at "$(automation_now)" \
        --argjson exitCode "$exit_code" \
        '{taskId: $taskId, role: $role, cycle: $cycle, at: $at, exitCode: $exitCode}' \
        | automation_append_json "$(automation_evidence_path "$task_id")/agent-runs.jsonl"
    return "$exit_code"
}

for _step in 1 2 3 4 5 6 7 8; do
    state="$(automation_read_state "$task_id")"
    case "$state" in
        PENDING|CODING)
            coding_cycle="$(jq -er '.codingCycle // 0' "$workspace_file")"
            coder_prompt="Use \$scheduled-quality-coder with $task_id. This is an orchestrated non-interactive run. Follow the deterministic state and evidence scripts. Never commit, merge, create worktrees, or push."
            run_agent scheduled-coder "$coder_prompt" "$coding_cycle" || true
            next_state="$(automation_read_state "$task_id")"
            if [[ "$next_state" == "PENDING" || "$next_state" == "CODING" ]]; then
                automation_transition_state "$task_id" "$next_state" "BLOCKED" "orchestrator" "coder exited without reaching a terminal gate state"
            fi
            ;;
        READY_FOR_REVIEW)
            (
                cd "$task_root"
                ./scripts/automation/begin-review.sh "$task_id"
            )
            ;;
        REVIEWING)
            review_cycle="$(jq -er '.reviewCycles // 0' "$workspace_file")"
            reviewer_prompt="Use \$scheduled-quality-reviewer with $task_id. This is a fresh, non-interactive, read-only review. Submit exactly one evidence-backed decision. Never edit, commit, merge, or push."
            run_agent scheduled-reviewer "$reviewer_prompt" "$review_cycle" || true
            if [[ "$(automation_read_state "$task_id")" == "REVIEWING" ]]; then
                automation_transition_state "$task_id" "REVIEWING" "BLOCKED" "orchestrator" "reviewer exited without submitting a decision"
            fi
            ;;
        CHANGES_REQUESTED)
            (
                cd "$task_root"
                ./scripts/automation/resume-review-fix.sh "$task_id"
            ) || true
            ;;
        AWAITING_HUMAN)
            automation_info "$task_id reached AWAITING_HUMAN"
            (
                cd "$task_root"
                ./scripts/automation/acceptance-report.sh "$task_id"
            )
            exit 0
            ;;
        BLOCKED|TEST_FAILED|NEEDS_HUMAN|INTEGRATION_BLOCKED)
            automation_die "$task_id stopped in $state; inspect $(automation_evidence_path "$task_id")"
            ;;
        *)
            automation_die "cannot orchestrate $task_id from state $state"
            ;;
    esac
done

automation_die "$task_id exceeded the deterministic orchestration step bound"
