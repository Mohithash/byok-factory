#!/bin/bash
# build_all.sh <list-file> — builds the flavors listed in the file (whitespace-separated) in chunks of 6, logs BUILT/MISSING
list=$1
case $list in ""|/*) ;; *) list="$PWD/$list" ;; esac   # resolve before cd
cd "$(dirname "$0")" || exit 1
[ -f "$list" ] || { echo "usage: $0 <list-file>   (flavor names, e.g. fpantrypal fbabydays)" >&2; exit 2; }
xargs -n 6 ./build_flavors.sh < "$list"
./gradlew --stop >/dev/null 2>&1
echo ALL-DONE
