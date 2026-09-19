#!/bin/bash
# build_flavors.sh <flavor...>  — signed release AAB + APK per flavor, copied to dist/
cd /root/claude/Factory
tasks=""
for f in "$@"; do F="$(tr '[:lower:]' '[:upper:]' <<< ${f:0:1})${f:1}"; tasks="$tasks :app:bundle${F}Release :app:assemble${F}Release"; done
./gradlew $tasks -q 2>&1 | grep -E "^e:|FAILED|error:|What went wrong" | head
mkdir -p dist
for f in "$@"; do
  id=$(python3 -c "import json;print(json.load(open('app/src/$f/assets/app.json'))['id'])")
  cp app/build/outputs/bundle/${f}Release/app-${f}-release.aab dist/${id}-v1.0.aab 2>/dev/null && cp app/build/outputs/apk/${f}/release/app-${f}-release.apk dist/${id}-v1.0.apk 2>/dev/null && echo "BUILT $id" || echo "MISSING $id"
done
