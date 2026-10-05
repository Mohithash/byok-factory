#!/bin/bash
# ship_factory.sh ["commit message"] — commit + push the repo, then publish one GitHub release per app built for
# the current engine version: tag <id>-v<VERSION> with dist/<id>-v<VERSION>.aab + .apk attached.
# VERSION is versionName in app/build.gradle.kts.
#
# RELEASED_VERSION names the newest version that has GitHub releases; catalog.py (CATALOG.md links) and the
# BYOK Store link to it. Once EVERY app in specs/ has a <id>-v<VERSION> release, this script writes VERSION into
# RELEASED_VERSION, re-runs catalog.py and commits + pushes that, so links only switch when they all resolve.
cd "$(dirname "$0")" || exit 1
VERSION=$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)
[ -n "$VERSION" ] || { echo "$0: no versionName in app/build.gradle.kts" >&2; exit 1; }
REPO=Mohithash/byok-factory
AUTHOR="Mohithash <17986082+Mohithash@users.noreply.github.com>"
export GIT_COMMITTER_NAME=Mohithash GIT_COMMITTER_EMAIL=17986082+Mohithash@users.noreply.github.com
[ -e .git ] || git init -q -b main
git add -A >/dev/null 2>&1; git commit -q --author="$AUTHOR" -m "${1:-Factory update}" 2>/dev/null
if ! git remote get-url origin >/dev/null 2>&1; then gh repo create "$REPO" --private --source=. --remote=origin --push >/dev/null; else git push -q origin main; fi
existing=$(gh release list --repo "$REPO" --limit 5000 --json tagName -q '.[].tagName')
for aab in dist/*-v"$VERSION".aab; do
  [ -f "$aab" ] || continue
  id=$(basename "$aab" "-v$VERSION.aab"); tag="$id-v$VERSION"; apk="dist/$tag.apk"
  grep -qxF "$tag" <<< "$existing" && continue
  [ -f "$apk" ] || continue
  [ -f "specs/$id.json" ] || { echo "FAILED $id (no specs/$id.json)"; continue; }
  name=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1],encoding='utf-8'))['name'])" "specs/$id.json")
  notes=$(python3 - "$id" "$VERSION" <<'PY'
import json, sys
sid, ver = sys.argv[1], sys.argv[2]
s = json.load(open(f"specs/{sid}.json", encoding="utf-8"))
# Headline engine features per version (every app gets them). Full list: CHANGELOG.md.
WHATS_NEW = {
    "1.1": [
        "Richer structured answers (steps, tickable checklists with progress, tables, cards, key facts, callouts) with bold/italic",
        "Follow-up questions in a real thread (keeps the last 4 turns as context), regenerate, edit & rerun",
        "Answers in 40+ languages, brief/standard/detailed length, standing custom instructions",
        "Read aloud and voice typing",
        "Save as PDF, export Markdown; share text or photos into the app; launcher shortcuts for each tool",
        "Pinned favourites, notes, rename, search and filters; swipe to delete with undo",
        "Backup & restore to a file (never includes your API key)",
        "Light/dark/system theme with Material You colours; usage counter; cancel and automatic retry",
        "Claude (default claude-opus-5-5) or any OpenAI-compatible endpoint, with the model list fetched from your provider",
    ],
}
out = [f"**{s['name']}** v{ver} — {s['tagline']}", "",
       f"_{s['category']}_ · package `com.mohithash.byok.{s['id'].replace('_', '')}`", "",
       s["about"], "", "**Tools**"]
out += [f"- {t['emoji']} {t['title']} — {t['subtitle']}" for t in s["tools"]]
if ver in WHATS_NEW:
    out += ["", f"**New in v{ver}** (all apps in the catalog)"] + [f"- {x}" for x in WHATS_NEW[ver]]
out += ["", "Bring your own key (Claude or any OpenAI-compatible endpoint). Attached: signed AAB (Play Console) "
        f"and APK (sideload). Store listing: `listings/{s['id']}.md`. Changes: `CHANGELOG.md`."]
print("\n".join(out))
PY
)
  if gh release create "$tag" "$aab" "$apk" --repo "$REPO" --title "$name v$VERSION" --notes "$notes" >/dev/null 2>&1; then
    echo "RELEASED $id"; existing="$existing"$'\n'"$tag"
  else
    echo "FAILED $id"
  fi
done

# Switch RELEASED_VERSION (and the catalog links) once every app has a release for VERSION.
current=$(cat RELEASED_VERSION 2>/dev/null | tr -d '[:space:]')
if [ "$current" != "$VERSION" ]; then
  missing=$(for p in specs/*.json; do echo "$(basename "$p" .json)-v$VERSION"; done | grep -cvxF -f <(printf '%s\n' "$existing"))
  if [ "$missing" -eq 0 ]; then
    printf '%s\n' "$VERSION" > RELEASED_VERSION
    python3 catalog.py >/dev/null || { echo "$0: catalog.py failed" >&2; exit 1; }
    git add RELEASED_VERSION CATALOG.md listings >/dev/null 2>&1
    git commit -q --author="$AUTHOR" -m "v$VERSION released for every app: catalog links now point to v$VERSION" 2>/dev/null
    git push -q origin main
    echo "Catalog links switched to v$VERSION"
  else
    echo "v$VERSION: $missing app(s) still without a release; catalog links stay on v${current:-1.0}"
  fi
fi
