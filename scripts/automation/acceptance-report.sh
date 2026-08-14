#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

task_id="${1:-}"
[[ "$#" -eq 1 ]] || { printf 'Usage: %s TASK-ID\n' "$0" >&2; exit 2; }
automation_validate_task_id "$task_id"
[[ "$(automation_read_state "$task_id")" == "AWAITING_HUMAN" ]] || automation_die "$task_id is not AWAITING_HUMAN"

contract="$(automation_contract_path "$task_id")"
workspace_file="$(automation_workspace_path "$task_id")"
origin_file="$(automation_origin_path "$task_id")"
evidence_dir="$(automation_evidence_path "$task_id")"
ready_file="$evidence_dir/ready.json"
review_file="$evidence_dir/review.json"
[[ -f "$workspace_file" && -f "$origin_file" && -f "$ready_file" && -f "$review_file" ]] || \
    automation_die "acceptance evidence is incomplete"
[[ "$(jq -er '.decision' "$review_file")" == "APPROVED" ]] || automation_die "latest independent review is not approved"

recorded_worktree="$(jq -er '.taskWorktree' "$workspace_file")"
[[ "$(cd "$recorded_worktree" && pwd)" == "$AUTOMATION_ROOT" ]] || automation_die "acceptance report must run in the recorded task worktree"
current_diff_sha="$(automation_worktree_diff_sha)"
[[ "$current_diff_sha" == "$(jq -er '.diffSha256' "$ready_file")" ]] || automation_die "sealed diff changed after the quality gate"
[[ "$current_diff_sha" == "$(jq -er '.diffSha256' "$review_file")" ]] || automation_die "sealed diff changed after independent review"

"$SCRIPT_DIR/scope-gate.sh" "$task_id" >/dev/null
changed_paths=()
while IFS= read -r path; do
    [[ -n "$path" ]] && changed_paths+=("$path")
done < <(automation_changed_paths)
changed_paths_json="$(printf '%s\n' "${changed_paths[@]}" | jq -Rsc 'split("\n") | map(select(length > 0))')"
diff_stat="$(git -C "$AUTOMATION_ROOT" diff --stat HEAD --)"
untracked_paths="$(git -C "$AUTOMATION_ROOT" ls-files --others --exclude-standard | LC_ALL=C sort)"

sealed_diff="$evidence_dir/sealed.diff"
{
    git -C "$AUTOMATION_ROOT" diff --binary HEAD --
    while IFS= read -r path; do
        [[ -n "$path" ]] || continue
        git -C "$AUTOMATION_ROOT" diff --binary --no-index -- /dev/null "$AUTOMATION_ROOT/$path" || [[ "$?" -eq 1 ]]
    done <<< "$untracked_paths"
} > "$sealed_diff"

report_file="$evidence_dir/acceptance-report.json"
jq -n \
    --arg taskId "$task_id" \
    --arg title "$(jq -er '.title' "$contract")" \
    --arg generatedAt "$(automation_now)" \
    --arg originalBranch "$(jq -er '.originalBranch' "$origin_file")" \
    --arg originalHeadBeforeContract "$(jq -er '.originalHeadBeforeContract' "$origin_file")" \
    --arg baselineHead "$(jq -er '.baselineHead' "$workspace_file")" \
    --arg taskBranch "$(jq -er '.taskBranch' "$workspace_file")" \
    --arg taskWorktree "$AUTOMATION_ROOT" \
    --arg sealedDiffSha256 "$current_diff_sha" \
    --arg diffStat "$diff_stat" \
    --arg sealedDiffPath "$sealed_diff" \
    --arg reviewSummary "$(jq -er '.summary' "$review_file")" \
    --argjson changedPaths "$changed_paths_json" \
    --argjson acceptanceCriteria "$(jq -c '.acceptanceCriteria' "$contract")" \
    '{taskId: $taskId, title: $title, state: "AWAITING_HUMAN",
      generatedAt: $generatedAt, originalBranch: $originalBranch,
      originalHeadBeforeContract: $originalHeadBeforeContract,
      baselineHead: $baselineHead, taskBranch: $taskBranch,
      taskWorktree: $taskWorktree, sealedDiffSha256: $sealedDiffSha256,
      changedPaths: $changedPaths, diffStat: $diffStat,
      sealedDiffPath: $sealedDiffPath, acceptanceCriteria: $acceptanceCriteria,
      reviewSummary: $reviewSummary, pushed: false}' \
    | automation_record_json "$report_file"

jq . "$report_file"
