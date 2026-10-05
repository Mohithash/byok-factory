#!/bin/bash
# refresh_store.sh — while a stream build runs, republish the BYOK Store every 20 min; once the stream log
# contains STREAM-DONE, publish one final "all apps built" refresh.
#   STORE       byok-store checkout (default ../byok-store next to this repo; /root/claude/Store if only that exists)
#   STREAM_LOG  log the stream build writes (default dist/stream.log, git-ignored), e.g.
#                 mkdir -p dist; ./stream_build3.sh > dist/stream.log 2>&1 &   ./refresh_store.sh &
#   FACTORY     passed to the store's publish.sh (default: this directory)
cd "$(dirname "$0")" || exit 1
if [ -z "$STORE" ]; then
  STORE=../byok-store
  [ ! -d "$STORE" ] && [ -d /root/claude/Store ] && STORE=/root/claude/Store
fi
[ -f "$STORE/publish.sh" ] || { echo "$0: no publish.sh in $STORE (set STORE=/path/to/byok-store)" >&2; exit 1; }
LOG="${STREAM_LOG:-dist/stream.log}"
export FACTORY="${FACTORY:-$PWD}"
while ! grep -q STREAM-DONE "$LOG" 2>/dev/null; do sleep 1200; bash "$STORE/publish.sh" "Catalog refresh" >/dev/null 2>&1; done
bash "$STORE/publish.sh" "Catalog: all apps built"
