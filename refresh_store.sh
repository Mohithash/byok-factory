#!/bin/bash
# periodically republish the store while the stream builds
while ! grep -q STREAM-DONE /tmp/claude-0/-root-claude/6364fb78-2035-42f3-a90d-a702d774c6de/scratchpad/stream.log 2>/dev/null; do sleep 1200; /root/claude/Store/publish.sh "Catalog refresh" >/dev/null 2>&1; done
/root/claude/Store/publish.sh "Catalog: all apps built"
