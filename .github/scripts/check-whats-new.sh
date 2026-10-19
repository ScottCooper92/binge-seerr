#!/usr/bin/env bash
# Checks Play's "what's new" notes and stages them for the upload.
#
#   check-whats-new.sh <notes-dir> <out-dir>
#
# <notes-dir> holds one `whatsnew-<locale>` file per language, as the Play upload action reads them.
# Play refuses a note over 500 characters, so a longer one fails here, before the build, rather than
# at the end of a release. Every note is copied to <out-dir>, which the upload reads from.
set -euo pipefail

# Play counts characters, not bytes; `wc -m` counts characters only under a UTF-8 locale.
export LC_ALL=C.UTF-8

readonly LIMIT=500

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <notes-dir> <out-dir>" >&2
  exit 2
fi
notes_dir=$1
out_dir=$2

if [ ! -d "$notes_dir" ]; then
  echo "::error::$notes_dir is not a directory."
  exit 1
fi

shopt -s nullglob
notes=("$notes_dir"/whatsnew-*)
if [ "${#notes[@]}" -eq 0 ]; then
  echo "::error::$notes_dir has no whatsnew-<locale> file."
  exit 1
fi

failed=0
for note in "${notes[@]}"; do
  chars=$(wc -m < "$note" | tr -d ' ')
  if [ "$chars" -gt "$LIMIT" ]; then
    echo "::error file=$note::$note is $chars characters; Play allows $LIMIT."
    failed=1
  fi
done
[ "$failed" -eq 0 ] || exit 1

mkdir -p "$out_dir"
cp -- "${notes[@]}" "$out_dir"/
echo "Staged ${#notes[@]} note(s) in $out_dir."
