#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

task_id="${1:-}"
[[ -n "$task_id" ]] || { printf 'Usage: %s TASK-ID\n' "$0" >&2; exit 2; }
automation_validate_task_id "$task_id"
automation_require_layout

contract="$(automation_contract_path "$task_id")"
state_file="$(automation_state_path "$task_id")"
workspace_file="$(automation_workspace_path "$task_id")"
origin_file="$(automation_origin_path "$task_id")"
evidence_dir="$(automation_evidence_path "$task_id")"

state_json="$(if [[ -f "$state_file" ]]; then jq -c . "$state_file"; else printf 'null'; fi)"
workspace_json="$(if [[ -f "$workspace_file" ]]; then jq -c . "$workspace_file"; else printf 'null'; fi)"
origin_json="$(if [[ -f "$origin_file" ]]; then jq -c . "$origin_file"; else printf 'null'; fi)"
baseline_json="$(if [[ -f "$evidence_dir/baseline.json" ]]; then jq -c . "$evidence_dir/baseline.json"; else printf 'null'; fi)"
red_json="$(if [[ -f "$evidence_dir/red.json" ]]; then jq -c . "$evidence_dir/red.json"; else printf 'null'; fi)"
ready_json="$(if [[ -f "$evidence_dir/ready.json" ]]; then jq -c . "$evidence_dir/ready.json"; else printf 'null'; fi)"
review_json="$(if [[ -f "$evidence_dir/review.json" ]]; then jq -c . "$evidence_dir/review.json"; else printf 'null'; fi)"
gate_json=null
if [[ "$ready_json" != "null" ]]; then
    coding_cycle="$(jq -r '.codingCycle // 0' <<< "$ready_json")"
    gate_file="$evidence_dir/gate-attempts-cycle-$coding_cycle.json"
    if [[ -f "$gate_file" ]]; then
        gate_json="$(jq -c . "$gate_file")"
    fi
fi

current_diff_sha=""
if [[ "$workspace_json" != "null" ]]; then
    task_root="$(jq -r '.taskWorktree // empty' <<< "$workspace_json")"
    if [[ -n "$task_root" && -d "$task_root" ]]; then
        current_diff_sha="$(automation_worktree_diff_sha "$task_root")"
    fi
fi

[[ -f "$contract" ]] || automation_die "contract not found: $contract"
jq -n \
    --slurpfile contract "$contract" \
    --argjson state "$state_json" \
    --argjson workspace "$workspace_json" \
    --argjson origin "$origin_json" \
    --argjson baseline "$baseline_json" \
    --argjson red "$red_json" \
    --argjson ready "$ready_json" \
    --argjson gate "$gate_json" \
    --argjson review "$review_json" \
    --arg evidence "$evidence_dir" \
    --arg currentDiffSha256 "$current_diff_sha" \
    '{contract: $contract[0], state: $state, workspace: $workspace, origin: $origin,
      evidenceDirectory: $evidence,
      evidence: {
        directory: $evidence,
        baseline: $baseline,
        red: $red,
        ready: $ready,
        latestGate: $gate,
        review: $review,
        currentDiffSha256: ($currentDiffSha256 | if length == 0 then null else . end),
        sealedDiffMatches: (if ($ready == null or ($currentDiffSha256 | length) == 0)
                            then null
                            else $ready.diffSha256 == $currentDiffSha256
                            end)
      }}'
