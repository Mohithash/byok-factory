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
./gradlew $tasks -q 2>&1 | grep -E "^e:|FAILED|error:|What went wrong" | head
mkdir -p dist
for f in "$@"; do
  id=$(python3 -c "import json;print(json.load(open('app/src/$f/assets/app.json'))['id'])")
  cp "app/build/outputs/bundle/${f}Release/app-${f}-release.aab" "dist/${id}-v${VERSION}.aab" 2>/dev/null && cp "app/build/outputs/apk/${f}/release/app-${f}-release.apk" "dist/${id}-v${VERSION}.apk" 2>/dev/null && echo "BUILT $id" || echo "MISSING $id"
done
