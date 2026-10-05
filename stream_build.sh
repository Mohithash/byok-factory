#!/bin/bash
# stream_build.sh — every loop: build any spec (older than 15 min, so reviewers are done with it) that has no
# dist/<id>-v<VERSION>.aab yet, then release. VERSION = versionName in app/build.gradle.kts (read once at start;
# restart after a version bump). Stops when nothing is left to build and STOP_STREAM exists.
cd "$(dirname "$0")" || exit 1
VERSION=$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)
[ -n "$VERSION" ] || { echo "$0: no versionName in app/build.gradle.kts" >&2; exit 1; }
while true; do
  todo=$(for p in $(find specs -name '*.json' -mmin +15); do id=$(basename "$p" .json); [ -f "dist/$id-v$VERSION.aab" ] || echo "f$(echo "$id" | tr -d '_')"; done)
  n=$(echo "$todo" | grep -c .)
  if [ "$n" -eq 0 ]; then [ -f STOP_STREAM ] && break; sleep 120; continue; fi
  echo "[$(date +%H:%M)] building $n (v$VERSION)"
  echo "$todo" | xargs -n 8 ./build_flavors.sh 2>&1 | grep -E "BUILT|MISSING|wrong"
  python3 catalog.py >/dev/null
  ./ship_factory.sh "More apps" 2>&1 | grep -c '^RELEASED ' | sed 's/^/released: /'
done
./gradlew --stop >/dev/null 2>&1; echo STREAM-DONE
