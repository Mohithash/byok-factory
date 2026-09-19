#!/bin/bash
# build_all.sh <list-file> — builds flavors in chunks of 12, logs BUILT/MISSING
cd /root/claude/Factory
xargs -n 6 ./build_flavors.sh < "$1"
./gradlew --stop >/dev/null 2>&1
echo ALL-DONE
