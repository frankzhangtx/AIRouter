#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <folder-name>" >&2
  exit 1
fi

name="$(printf '%s' "$1" | xargs)"

if [ -z "$name" ]; then
  echo "Folder name cannot be empty." >&2
  exit 1
fi

case "$name" in
  *"/"* | "." | "..")
    echo "Folder name must be a single directory name." >&2
    exit 1
    ;;
esac

target_dir="${PWD}/${name}"

if [ -e "$target_dir" ]; then
  echo "Folder already exists: $target_dir" >&2
  exit 1
fi

mkdir -p "$target_dir"
echo "$target_dir"
