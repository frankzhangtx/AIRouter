#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

task_id="${1:-}"
approval="${2:-}"
if [[ "$#" -ne 2 ]]; then
    printf 'Usage: %s TASK-ID "CONTRACT-APPROVAL"\n' "$0" >&2
    exit 2
fi

automation_validate_task_id "$task_id"
automation_require_orchestrated
automation_require_approval contract "$approval"
"$SCRIPT_DIR/validate-contract.sh" "$task_id" >/dev/null
[[ "$(automation_read_state "$task_id")" == "CONTRACT_REVIEW" ]] || automation_die "$task_id is not awaiting contract review"

origin_file="$(automation_origin_path "$task_id")"
[[ -f "$origin_file" ]] || automation_die "origin evidence is missing"
source_root="$(jq -er '.sourceRoot' "$origin_file")"
original_branch="$(jq -er '.originalBranch' "$origin_file")"
original_head="$(jq -er '.originalHeadBeforeContract' "$origin_file")"
contract_rel="$(jq -er '.contractPath' "$origin_file")"
plan_rel="$(jq -er '.planPath' "$origin_file")"
contract="$source_root/$contract_rel"
plan="$source_root/$plan_rel"

[[ "$source_root" == "$AUTOMATION_ROOT" ]] || automation_die "approval must run from the recorded source worktree"
[[ "$(automation_current_branch "$source_root")" == "$original_branch" ]] || automation_die "original branch changed after contract review began"
[[ "$(git -C "$source_root" rev-parse HEAD)" == "$original_head" ]] || automation_die "original HEAD changed after contract review began"
[[ "$(automation_file_sha256 "$contract")" == "$(jq -er '.contractSha256' "$origin_file")" ]] || automation_die "contract changed after proposal approval"
[[ "$(automation_file_sha256 "$plan")" == "$(jq -er '.planSha256' "$origin_file")" ]] || automation_die "plan changed after proposal approval"

changed_paths=()
while IFS= read -r path; do
    [[ -n "$path" ]] && changed_paths+=("$path")
done < <(automation_changed_paths_at "$source_root")
if [[ "${#changed_paths[@]}" -ne 2 ]] || \
   [[ " ${changed_paths[*]} " != *" $contract_rel "* ]] || \
   [[ " ${changed_paths[*]} " != *" $plan_rel "* ]]; then
    automation_die "contract approval requires exactly the sealed plan and contract changes"
fi

automation_require_command git
automation_require_command jq
automation_require_command shasum
if [[ "${AUTOMATION_TEST_MODE:-0}" != "1" ]]; then
    automation_require_command opencode
    [[ -n "${ANDROID_HOME:-}" ]] || automation_die "ANDROID_HOME is not set; export it before approving the contract"
    [[ -d "$ANDROID_HOME" ]] || automation_die "ANDROID_HOME does not exist: $ANDROID_HOME"
fi
git -C "$source_root" config user.name >/dev/null || automation_die "Git user.name is not configured"
git -C "$source_root" config user.email >/dev/null || automation_die "Git user.email is not configured"

worktree_base="$(automation_worktree_base)"
task_slug="$(tr '[:upper:]' '[:lower:]' <<< "$task_id")"
task_worktree="$worktree_base/$task_slug"
task_branch="$(automation_task_branch "$task_id")"
[[ ! -e "$task_worktree" ]] || automation_die "task worktree path already exists: $task_worktree"
if git -C "$source_root" show-ref --verify --quiet "refs/heads/$task_branch"; then
    automation_die "task branch already exists: $task_branch"
fi

automation_acquire_run_lock "$task_id"
preparation_complete=0
approval_exit() {
    local exit_code=$?
    trap - EXIT
    if [[ "$preparation_complete" != "1" ]] && [[ -f "$(automation_state_path "$task_id")" ]] && \
       [[ "$(automation_read_state "$task_id" 2>/dev/null || true)" == "PREPARING" ]]; then
        automation_transition_state "$task_id" "PREPARING" "BLOCKED" "orchestrator" "isolated worktree preparation failed" || true
    fi
    automation_release_run_lock
    exit "$exit_code"
}
trap approval_exit EXIT

jq -nc \
    --arg taskId "$task_id" \
    --arg kind "contract" \
    --arg at "$(automation_now)" \
    --arg originalBranch "$original_branch" \
    --arg originalHead "$original_head" \
    --arg contractSha256 "$(automation_file_sha256 "$contract")" \
    '{taskId: $taskId, kind: $kind, at: $at, originalBranch: $originalBranch,
      originalHead: $originalHead, contractSha256: $contractSha256}' \
    | automation_append_json "$(automation_approvals_path "$task_id")"

git -C "$source_root" add -- "$plan_rel" "$contract_rel"
git -C "$source_root" commit -m "Add approved contract for $task_id" -- "$plan_rel" "$contract_rel"
baseline_head="$(git -C "$source_root" rev-parse HEAD)"
automation_worktree_is_clean "$source_root" || automation_die "source worktree is not clean after committing the approved artifacts"

jq \
    --arg approvedAt "$(automation_now)" \
    --arg contractCommit "$baseline_head" \
    --arg baselineHead "$baseline_head" \
    '.approvedAt = $approvedAt | .contractCommit = $contractCommit | .baselineHead = $baselineHead' \
    "$origin_file" | automation_record_json "$origin_file"

automation_transition_state "$task_id" "CONTRACT_REVIEW" "APPROVED_CONTRACT" "human" "sealed contract explicitly approved"
automation_transition_state "$task_id" "APPROVED_CONTRACT" "PREPARING" "orchestrator" "creating isolated task workspace"

mkdir -p "$worktree_base"
git -C "$source_root" worktree add "$task_worktree" -b "$task_branch" "$baseline_head"
workspace_file="$(automation_workspace_path "$task_id")"
jq -n \
    --arg taskId "$task_id" \
    --arg sourceRoot "$source_root" \
    --arg originalBranch "$original_branch" \
    --arg baselineHead "$baseline_head" \
    --arg taskWorktree "$task_worktree" \
    --arg taskBranch "$task_branch" \
    --arg worktreeBase "$worktree_base" \
    --arg createdAt "$(automation_now)" \
    '{taskId: $taskId, sourceRoot: $sourceRoot, originalBranch: $originalBranch,
      baselineHead: $baselineHead, taskWorktree: $taskWorktree,
      taskBranch: $taskBranch, worktreeBase: $worktreeBase,
      codingCycle: 0, reviewCycles: 0, createdAt: $createdAt}' \
    | automation_record_json "$workspace_file"

automation_transition_state "$task_id" "PREPARING" "PENDING" "orchestrator" "isolated worktree created and task queued"
preparation_complete=1
automation_release_run_lock
trap - EXIT

"$SCRIPT_DIR/orchestrate-task.sh" "$task_id"
