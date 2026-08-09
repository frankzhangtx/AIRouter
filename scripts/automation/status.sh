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

[[ -f "$contract" ]] || automation_die "contract not found: $contract"
if [[ -f "$state_file" ]]; then
    jq -n \
        --slurpfile contract "$contract" \
        --slurpfile state "$state_file" \
        --arg evidence "$(automation_evidence_path "$task_id")" \
        '{contract: $contract[0], state: $state[0], evidenceDirectory: $evidence}'
else
    jq -n \
        --slurpfile contract "$contract" \
        --arg evidence "$(automation_evidence_path "$task_id")" \
        '{contract: $contract[0], state: null, evidenceDirectory: $evidence}'
fi
