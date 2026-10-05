#!/bin/bash
# build_flavors.sh <flavor...>  — signed release AAB + APK per flavor, copied to dist/<id>-v<VERSION>.{aab,apk}
# VERSION is versionName in app/build.gradle.kts (bump it, and versionCode, before building a new release).
cd "$(dirname "$0")" || exit 1
VERSION=$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)
[ -n "$VERSION" ] || { echo "$0: no versionName in app/build.gradle.kts" >&2; exit 1; }
[ $# -gt 0 ] || { echo "usage: $0 <flavor...>   e.g. $0 fpantrypal fbabydays" >&2; exit 2; }
python3 gen.py "$@" >/dev/null
tasks=""
for f in "$@"; do F="$(tr '[:lower:]' '[:upper:]' <<< "${f:0:1}")${f:1}"; tasks="$tasks :app:bundle${F}Release :app:assemble${F}Release"; done
# Clear earlier outputs first, so a failed build can't pass off an old AAB/APK as this version.
for f in "$@"; do rm -rf "app/build/outputs/bundle/${f}Release" "app/build/outputs/apk/${f}/release"; done
./gradlew $tasks -q 2>&1 | grep -E "^e:|FAILED|error:|What went wrong" | head
mkdir -p dist
for f in "$@"; do
  id=$(python3 -c "import json;print(json.load(open('app/src/$f/assets/app.json'))['id'])")
  aab="app/build/outputs/bundle/${f}Release/app-${f}-release.aab"; apk="app/build/outputs/apk/${f}/release/app-${f}-release.apk"
  # APK first: the AAB in dist/ is the "built" marker, so it only appears once both copies worked.
  if [ -f "$aab" ] && [ -f "$apk" ] && cp "$apk" "dist/${id}-v${VERSION}.apk" && cp "$aab" "dist/${id}-v${VERSION}.aab"; then echo "BUILT $id"; else echo "MISSING $id"; fi
done
