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

[[ -f "$contract" ]] || automation_die "contract not found: $contract"
jq -n \
    --slurpfile contract "$contract" \
    --argjson state "$(if [[ -f "$state_file" ]]; then jq -c . "$state_file"; else printf 'null'; fi)" \
    --argjson workspace "$(if [[ -f "$workspace_file" ]]; then jq -c . "$workspace_file"; else printf 'null'; fi)" \
    --argjson origin "$(if [[ -f "$origin_file" ]]; then jq -c . "$origin_file"; else printf 'null'; fi)" \
    --arg evidence "$evidence_dir" \
    '{contract: $contract[0], state: $state, workspace: $workspace, origin: $origin, evidenceDirectory: $evidence}'
