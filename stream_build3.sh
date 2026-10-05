#!/bin/bash
# chunks of 20: build specs (older than 15 min) with no dist/<id>-v<VERSION>.aab; after each chunk stop the Gradle
# daemon (memory), delete per-flavor intermediates (disk), release, and drop released APKs (disk).
# VERSION = versionName in app/build.gradle.kts (read once at start; restart after a version bump).
# Stops when nothing is left to build and STOP_STREAM2 exists.
cd "$(dirname "$0")" || exit 1
VERSION=$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)
[ -n "$VERSION" ] || { echo "$0: no versionName in app/build.gradle.kts" >&2; exit 1; }
while true; do
  todo=$(for p in $(find specs -name '*.json' -mmin +15); do id=$(basename "$p" .json); [ -f "dist/$id-v$VERSION.aab" ] || echo "f$(echo "$id" | tr -d '_')"; done | head -20)
  n=$(echo "$todo" | grep -c .)
  if [ "$n" -eq 0 ]; then [ -f STOP_STREAM2 ] && break; sleep 120; continue; fi
  echo "[$(date +%H:%M)] chunk of $n (v$VERSION)"
  ./build_flavors.sh $todo 2>&1 | grep -E "BUILT|MISSING|wrong"
  ./gradlew --stop >/dev/null 2>&1
  find app/build/intermediates -mindepth 2 -maxdepth 2 -name "f*" -exec rm -rf {} + 2>/dev/null
  rm -rf app/build/outputs/bundle/f* app/build/outputs/apk/f* app/build/intermediates/incremental/f* 2>/dev/null
  python3 catalog.py >/dev/null
  ./ship_factory.sh "More apps" 2>&1 | grep -c '^RELEASED ' | sed 's/^/released: /'
  # APKs are on GitHub now; keep only AABs locally as the built marker (tag <id>-v<ver> = dist/<id>-v<ver>.apk)
  for t in $(gh release list --repo Mohithash/byok-factory --limit 5000 --json tagName -q '.[].tagName'); do rm -f "dist/$t.apk"; done
  df -h / | tail -1 | awk '{print "disk free: "$4}'
done
echo STREAM-DONE
