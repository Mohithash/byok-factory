#!/bin/bash
# ship_factory.sh — commit repo (once/again) and publish one GitHub release per built app in dist/
cd /root/claude/Factory
export GIT_COMMITTER_NAME=Mohithash GIT_COMMITTER_EMAIL=17986082+Mohithash@users.noreply.github.com
[ -d .git ] || git init -q -b main
git add -A >/dev/null 2>&1; git commit -q --author="Mohithash <17986082+Mohithash@users.noreply.github.com>" -m "${1:-Factory update}" 2>/dev/null
if ! git remote get-url origin >/dev/null 2>&1; then gh repo create Mohithash/byok-factory --private --source=. --remote=origin --push >/dev/null; else git push -q origin main; fi
existing=$(gh release list --repo Mohithash/byok-factory --limit 1000 --json tagName -q '.[].tagName')
for aab in dist/*.aab; do
  id=$(basename "$aab" -v1.0.aab); tag="$id-v1.0"; apk="dist/$id-v1.0.apk"
  grep -qx "$tag" <<< "$existing" && continue
  [ -f "$apk" ] || continue
  name=$(python3 -c "import json;print(json.load(open('specs/$id.json'))['name'])")
  notes=$(python3 - "$id" <<'PY'
import json,sys; s=json.load(open(f"specs/{sys.argv[1]}.json"))
print(f"**{s['name']}** — {s['tagline']}\n\n_{s['category']}_ · package `com.mohithash.byok.{s['id'].replace('_','')}`\n\n{s['about']}\n\n**Tools**\n" + "\n".join(f"- {t['emoji']} {t['title']} — {t['subtitle']}" for t in s['tools']) + "\n\nBring your own key (Claude or any OpenAI-compatible endpoint). Attached: signed AAB (Play Console) and APK (sideload). Store listing: `listings/" + s['id'] + ".md`.")
PY
)
  if gh release create "$tag" "$aab" "$apk" --repo Mohithash/byok-factory --title "$name v1.0" --notes "$notes" >/dev/null 2>&1; then echo "RELEASED $id"; else echo "FAILED $id"; fi
done
