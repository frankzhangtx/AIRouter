#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

task_id="${1:-}"
base="${2:-}"
head="${3:-}"
if [[ "$#" -ne 3 ]]; then
    printf 'Usage: %s TASK-ID BASE-COMMIT HEAD-COMMIT\n' "$0" >&2
    exit 2
fi
automation_validate_task_id "$task_id"
git -C "$AUTOMATION_ROOT" cat-file -e "$base^{commit}" 2>/dev/null || automation_die "invalid integration base: $base"
git -C "$AUTOMATION_ROOT" cat-file -e "$head^{commit}" 2>/dev/null || automation_die "invalid integration head: $head"
"$SCRIPT_DIR/validate-contract.sh" "$task_id" >/dev/null

contract="$(automation_contract_path "$task_id")"
changed_file_list="$(automation_changed_paths_between "$base" "$head")"
[[ -n "$changed_file_list" ]] || { automation_die "integration candidate has no product changes"; exit 40; }

max_changed="$(jq -r '.maxChangedFiles' "$contract")"
changed_count="$(printf '%s\n' "$changed_file_list" | awk 'NF { count++ } END { print count + 0 }')"
[[ "$changed_count" -le "$max_changed" ]] || { automation_die "integration changed file count $changed_count exceeds limit $max_changed"; exit 40; }

production_changed=0
test_changed=0
while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    if automation_array_matches_path "$AUTOMATION_CONFIG" '.protectedPaths' "$path"; then
        automation_die "protected path changed in integration candidate: $path"
        exit 40
    fi
    if automation_array_matches_path "$contract" '.forbiddenPaths' "$path"; then
        automation_die "forbidden path changed in integration candidate: $path"
        exit 40
    fi
    if ! automation_array_matches_path "$contract" '.allowedPaths' "$path"; then
        automation_die "integration path is outside allowedPaths: $path"
        exit 40
    fi
    case "$path" in
        app/src/main/*) production_changed=1 ;;
        app/src/test/*|app/src/androidTest/*) test_changed=1 ;;
    esac
done <<< "$changed_file_list"

deleted_tests="$(git -C "$AUTOMATION_ROOT" diff --name-only --diff-filter=D "$base" "$head" -- app/src/test app/src/androidTest)"
[[ -z "$deleted_tests" ]] || { automation_die "test deletion is forbidden: $deleted_tests"; exit 40; }
if [[ "$production_changed" == "1" && "$(jq -r '.testPolicy' "$contract")" == "required" && "$test_changed" != "1" ]]; then
    automation_die "production code changed without a test change"
    exit 40
fi

added_test_lines="$(git -C "$AUTOMATION_ROOT" diff --unified=0 "$base" "$head" -- app/src/test app/src/androidTest \
    | awk '/^\+\+\+/ { next } /^\+/ { sub(/^\+/, ""); print }')"
weakening_pattern='@Ignore|@Disabled|assumeTrue\(false\)|assertTrue\(true\)|assertFalse\(false\)'
if printf '%s\n' "$added_test_lines" | rg -n "$weakening_pattern" >/dev/null; then
    automation_die "potential test weakening detected in integration candidate"
    exit 40
fi

automation_info "integration scope gate passed ($changed_count changed files)"
printf '%s\n' "$changed_file_list"
