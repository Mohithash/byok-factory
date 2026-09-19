#!/bin/bash
# stream_build.sh — every loop: build any spec (older than 15 min, so reviewers are done with it) that has no dist AAB yet, then release.
cd /root/claude/Factory
while true; do
  todo=$(for p in $(find specs -name '*.json' -mmin +15); do id=$(basename $p .json); [ -f dist/$id-v1.0.aab ] || echo "f$(echo $id | tr -d '_')"; done)
  n=$(echo "$todo" | grep -c .)
  if [ "$n" -eq 0 ]; then [ -f STOP_STREAM ] && break; sleep 120; continue; fi
  echo "[$(date +%H:%M)] building $n"
  echo "$todo" | xargs -n 8 ./build_flavors.sh 2>&1 | grep -E "BUILT|MISSING|wrong"
  python3 catalog.py >/dev/null
  ./ship_factory.sh "More apps" 2>&1 | grep -cE "RELEASED" | sed 's/^/released: /'
done
./gradlew --stop >/dev/null 2>&1; echo STREAM-DONE
