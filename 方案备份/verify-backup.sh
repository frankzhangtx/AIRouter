#!/usr/bin/env bash

set -euo pipefail

BACKUP_CHECK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

backup_require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        printf 'missing required command: %s\n' "$1" >&2
        exit 1
    }
}

backup_tree_digest() {
    local backup_tree_root="$1"

    (
        cd "$backup_tree_root"
        find . -type f -print \
            | LC_ALL=C sort \
            | while IFS= read -r backup_file; do
                shasum -a 256 "$backup_file"
            done \
            | shasum -a 256 \
            | awk '{print $1}'
    )
}

backup_tree_count() {
    find "$1" -type f -print | wc -l | tr -d ' '
}

backup_check_tree() {
    local backup_label="$1"
    local backup_relative_root="$2"
    local backup_expected_count="$3"
    local backup_expected_digest="$4"
    local backup_absolute_root="$BACKUP_CHECK_ROOT/$backup_relative_root"
    local backup_actual_count
    local backup_actual_digest

    [[ -d "$backup_absolute_root" ]] || {
        printf 'MISSING %s: %s\n' "$backup_label" "$backup_absolute_root" >&2
        exit 1
    }

    backup_actual_count="$(backup_tree_count "$backup_absolute_root")"
    backup_actual_digest="$(backup_tree_digest "$backup_absolute_root")"

    [[ "$backup_actual_count" == "$backup_expected_count" ]] || {
        printf 'COUNT MISMATCH %s: expected=%s actual=%s\n' \
            "$backup_label" "$backup_expected_count" "$backup_actual_count" >&2
        exit 1
    }

    [[ "$backup_actual_digest" == "$backup_expected_digest" ]] || {
        printf 'DIGEST MISMATCH %s\nexpected=%s\nactual=%s\n' \
            "$backup_label" "$backup_expected_digest" "$backup_actual_digest" >&2
        exit 1
    }

    printf 'OK %s: files=%s sha256=%s\n' \
        "$backup_label" "$backup_actual_count" "$backup_actual_digest"
}

backup_require_command find
backup_require_command sort
backup_require_command shasum
backup_require_command awk
backup_require_command wc
backup_require_command tr

backup_check_tree \
    repository-snapshot \
    snapshot/repository \
    70 \
    e08bb576db26b4b85db79d044c4146bd372266fd51da2e5aa039f52a82af23ca

backup_check_tree \
    external-dependencies \
    external-dependencies \
    183 \
    ecb94b3cd106fa906ddb1c87aac4db927cb26c7a642fef7a2817c7a88cb22017

backup_check_tree \
    runtime-audit \
    runtime-audit \
    79 \
    8284a8804177ad84f71616667d8f415ea97ef82314aad8fdad26b7b203fc22b7

printf 'backup verification passed\n'
