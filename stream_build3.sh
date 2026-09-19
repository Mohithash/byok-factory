#!/bin/bash
# v2: chunks of 20; after each chunk: stop daemon (memory), delete per-flavor intermediates (disk), release, drop released APKs (disk).
cd /root/claude/Factory
while true; do
  todo=$(for p in $(find specs -name '*.json' -mmin +15); do id=$(basename $p .json); [ -f dist/$id-v1.0.aab ] || echo "f$(echo $id | tr -d '_')"; done | head -20)
  n=$(echo "$todo" | grep -c .)
  if [ "$n" -eq 0 ]; then [ -f STOP_STREAM2 ] && break; sleep 120; continue; fi
  echo "[$(date +%H:%M)] chunk of $n"
  ./build_flavors.sh $todo 2>&1 | grep -E "BUILT|MISSING|wrong"
  ./gradlew --stop >/dev/null 2>&1
  find app/build/intermediates -mindepth 2 -maxdepth 2 -name "f*" -exec rm -rf {} + 2>/dev/null
  rm -rf app/build/outputs/bundle/f* app/build/outputs/apk/f* app/build/intermediates/incremental/f* 2>/dev/null
  python3 catalog.py >/dev/null
  ./ship_factory.sh "More apps" 2>&1 | grep -cE "RELEASED" | sed 's/^/released: /'
  # APKs are on GitHub now; keep only AABs locally as the built marker
  for t in $(gh release list --repo Mohithash/byok-factory --limit 1000 --json tagName -q '.[].tagName'); do rm -f "dist/${t%-v1.0}-v1.0.apk"; done
  df -h / | tail -1 | awk '{print "disk free: "$4}'
done
echo STREAM-DONE
