#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

task_id="${1:-}"
approval="${2:-}"
if [[ "$#" -ne 2 ]]; then
    printf 'Usage: %s TASK-ID "FINAL-ACCEPTANCE"\n' "$0" >&2
    exit 2
fi

automation_validate_task_id "$task_id"
automation_require_orchestrated
automation_require_approval acceptance "$approval"
[[ "$(automation_read_state "$task_id")" == "AWAITING_HUMAN" ]] || automation_die "$task_id is not awaiting human acceptance"
[[ "$(automation_config_value '.pushAfterAcceptance')" == "false" ]] || automation_die "automatic push is forbidden"

origin_file="$(automation_origin_path "$task_id")"
workspace_file="$(automation_workspace_path "$task_id")"
evidence_dir="$(automation_evidence_path "$task_id")"
ready_file="$evidence_dir/ready.json"
review_file="$evidence_dir/review.json"
[[ -f "$origin_file" && -f "$workspace_file" && -f "$ready_file" && -f "$review_file" ]] || automation_die "integration evidence is incomplete"

source_root="$(jq -er '.sourceRoot' "$origin_file")"
original_branch="$(jq -er '.originalBranch' "$origin_file")"
baseline_head="$(jq -er '.baselineHead' "$workspace_file")"
task_worktree="$(jq -er '.taskWorktree' "$workspace_file")"
task_branch="$(jq -er '.taskBranch' "$workspace_file")"
[[ "$source_root" == "$AUTOMATION_ROOT" ]] || automation_die "final acceptance must run from the recorded source worktree"
[[ -d "$task_worktree" ]] || automation_die "task worktree is missing: $task_worktree"
[[ "$(automation_current_branch "$source_root")" == "$original_branch" ]] || automation_die "source worktree is no longer on $original_branch"
[[ "$(automation_current_branch "$task_worktree")" == "$task_branch" ]] || automation_die "task worktree branch identity changed"
[[ "$(git -C "$task_worktree" rev-parse HEAD)" == "$baseline_head" ]] || automation_die "task branch HEAD changed before deterministic commit"
automation_worktree_is_clean "$source_root" || automation_die "source worktree must be clean before integration"
git -C "$source_root" merge-base --is-ancestor "$baseline_head" HEAD || automation_die "original branch history no longer contains the approved contract baseline"

sealed_diff_sha="$(automation_worktree_diff_sha "$task_worktree")"
[[ "$sealed_diff_sha" == "$(jq -er '.diffSha256' "$ready_file")" ]] || automation_die "task diff changed after quality gate"
[[ "$sealed_diff_sha" == "$(jq -er '.diffSha256' "$review_file")" ]] || automation_die "task diff changed after review"
[[ "$(jq -er '.decision' "$review_file")" == "APPROVED" ]] || automation_die "latest review is not approved"

(
    cd "$task_worktree"
    ./scripts/automation/scope-gate.sh "$task_id" >/dev/null
    ./scripts/automation/acceptance-report.sh "$task_id" >/dev/null
)
report_file="$evidence_dir/acceptance-report.json"
[[ "$(jq -er '.sealedDiffSha256' "$report_file")" == "$sealed_diff_sha" ]] || automation_die "acceptance report is stale"

source_head="$(git -C "$source_root" rev-parse HEAD)"
integration_branch="automation/integrate-$(tr '[:upper:]' '[:lower:]' <<< "$task_id")"
integration_worktree="$(jq -er '.worktreeBase' "$workspace_file")/$(tr '[:upper:]' '[:lower:]' <<< "$task_id")-integration"
[[ ! -e "$integration_worktree" ]] || automation_die "integration worktree path already exists: $integration_worktree"
if git -C "$source_root" show-ref --verify --quiet "refs/heads/$integration_branch"; then
    automation_die "integration branch already exists: $integration_branch"
fi

automation_acquire_run_lock "$task_id"
integration_complete=0
integration_exit() {
    local exit_code=$?
    trap - EXIT
    if [[ "$integration_complete" != "1" ]] && [[ -f "$(automation_state_path "$task_id")" ]] && \
       [[ "$(automation_read_state "$task_id" 2>/dev/null || true)" == "INTEGRATING" ]]; then
        jq -n \
            --arg taskId "$task_id" \
            --arg failedAt "$(automation_now)" \
            --argjson exitCode "$exit_code" \
            '{taskId: $taskId, failedAt: $failedAt, exitCode: $exitCode,
              message: "Integration stopped; original branch was not advanced unless integration.json exists."}' \
            | automation_record_json "$evidence_dir/integration-failure.json" || true
        automation_transition_state "$task_id" "INTEGRATING" "INTEGRATION_BLOCKED" "integrator" "candidate integration or verification failed" || true
    fi
    automation_release_run_lock
    exit "$exit_code"
}
trap integration_exit EXIT

jq -nc \
    --arg taskId "$task_id" \
    --arg kind "acceptance" \
    --arg at "$(automation_now)" \
    --arg originalBranch "$original_branch" \
    --arg sealedDiffSha256 "$sealed_diff_sha" \
    '{taskId: $taskId, kind: $kind, at: $at,
      originalBranch: $originalBranch, sealedDiffSha256: $sealedDiffSha256}' \
    | automation_append_json "$(automation_approvals_path "$task_id")"
jq -n \
    --arg taskId "$task_id" \
    --arg acceptedAt "$(automation_now)" \
    --arg originalBranch "$original_branch" \
    --arg sealedDiffSha256 "$sealed_diff_sha" \
    '{taskId: $taskId, acceptedAt: $acceptedAt,
      originalBranch: $originalBranch, sealedDiffSha256: $sealedDiffSha256}' \
    | automation_record_json "$evidence_dir/acceptance.json"

automation_transition_state "$task_id" "AWAITING_HUMAN" "INTEGRATING" "integrator" "human accepted the sealed diff for the recorded original branch"

product_paths=()
while IFS= read -r path; do
    [[ -n "$path" ]] && product_paths+=("$path")
done < <(automation_changed_paths_at "$task_worktree")
[[ "${#product_paths[@]}" -gt 0 ]] || automation_die "no product paths remain to commit"
git -C "$task_worktree" add -- "${product_paths[@]}"
title="$(jq -er '.title' "$task_worktree/automation/tasks/$task_id.json")"
git -C "$task_worktree" commit -m "Implement $title ($task_id)"
product_commit="$(git -C "$task_worktree" rev-parse HEAD)"
automation_worktree_is_clean "$task_worktree" || automation_die "task worktree is dirty after product commit"

jq \
    --arg productCommit "$product_commit" \
    --arg sourceHeadAtIntegration "$source_head" \
    --arg integrationBranch "$integration_branch" \
    --arg integrationWorktree "$integration_worktree" \
    --arg updatedAt "$(automation_now)" \
    '.productCommit = $productCommit |
     .sourceHeadAtIntegration = $sourceHeadAtIntegration |
     .integrationBranch = $integrationBranch |
     .integrationWorktree = $integrationWorktree |
     .updatedAt = $updatedAt' \
    "$workspace_file" | automation_record_json "$workspace_file"

git -C "$source_root" worktree add "$integration_worktree" -b "$integration_branch" "$source_head"
if [[ "$source_head" == "$baseline_head" ]]; then
    git -C "$integration_worktree" merge --ff-only "$product_commit"
    integration_method="fast-forward-product-commit"
else
    git -C "$integration_worktree" cherry-pick "$product_commit"
    integration_method="cherry-pick-onto-advanced-original"
fi
candidate_head="$(git -C "$integration_worktree" rev-parse HEAD)"

[[ "$(automation_file_sha256 "$integration_worktree/automation/tasks/$task_id.json")" == "$(jq -er '.contractSha256' "$origin_file")" ]] || \
    automation_die "approved contract changed on the integration candidate"
set +e
(
    cd "$integration_worktree"
    ./scripts/automation/verify-integration.sh "$task_id" "$source_head" "$candidate_head"
) 2>&1 | tee "$evidence_dir/integration-verification.log"
verification_status=${PIPESTATUS[0]}
set -e
[[ "$verification_status" -eq 0 ]] || automation_die "integration verification failed with exit $verification_status"

[[ "$(automation_current_branch "$source_root")" == "$original_branch" ]] || automation_die "original branch changed during candidate verification"
[[ "$(git -C "$source_root" rev-parse HEAD)" == "$source_head" ]] || automation_die "original branch advanced during candidate verification"
automation_worktree_is_clean "$source_root" || automation_die "source worktree became dirty during candidate verification"
git -C "$source_root" merge --ff-only "$integration_branch"
integrated_head="$(git -C "$source_root" rev-parse HEAD)"
[[ "$integrated_head" == "$candidate_head" ]] || automation_die "original branch did not reach the verified candidate"

jq -n \
    --arg taskId "$task_id" \
    --arg integratedAt "$(automation_now)" \
    --arg originalBranch "$original_branch" \
    --arg sourceHeadBeforeIntegration "$source_head" \
    --arg productCommit "$product_commit" \
    --arg integratedHead "$integrated_head" \
    --arg method "$integration_method" \
    --argjson verificationExitCode "$verification_status" \
    '{taskId: $taskId, integratedAt: $integratedAt,
      originalBranch: $originalBranch,
      sourceHeadBeforeIntegration: $sourceHeadBeforeIntegration,
      productCommit: $productCommit, integratedHead: $integratedHead,
      method: $method, verificationExitCode: $verificationExitCode,
      pushed: false}' \
    | automation_record_json "$evidence_dir/integration.json"

automation_transition_state "$task_id" "INTEGRATING" "COMPLETED" "integrator" "verified candidate fast-forwarded into the recorded original branch; not pushed"
integration_complete=1

if [[ "$(automation_config_value '.autoCleanupWorktrees')" == "true" ]]; then
    cleanup_log="$evidence_dir/cleanup.log"
    {
        if git -C "$source_root" worktree remove "$integration_worktree"; then
            printf '%s removed integration worktree %s\n' "$(automation_now)" "$integration_worktree"
        else
            printf '%s WARN could not remove integration worktree %s\n' "$(automation_now)" "$integration_worktree"
        fi
        if git -C "$source_root" worktree remove "$task_worktree"; then
            printf '%s removed task worktree %s\n' "$(automation_now)" "$task_worktree"
        else
            printf '%s WARN could not remove task worktree %s\n' "$(automation_now)" "$task_worktree"
        fi
    } >> "$cleanup_log" 2>&1
fi

automation_release_run_lock
trap - EXIT
jq . "$evidence_dir/integration.json"
