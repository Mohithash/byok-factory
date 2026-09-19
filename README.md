# BYOK Factory

One engine, one hundred-plus apps. Each app is a JSON spec (brand, palette, icon, profile fields, tools with tailored prompts) rendered by a shared Jetpack Compose / Material 3 Expressive engine, built as a Gradle product flavor, and shipped as a signed AAB (Play Console) + APK (sideload).

- **Catalog:** [CATALOG.md](CATALOG.md) — every app with category, tagline, listing and release links.
- **Store listings:** `listings/<id>.md` — short/full descriptions, package name, category, tools.
- **Privacy policy:** [PRIVACY.md](PRIVACY.md) — one policy covers all apps (BYOK, on-device only).

## How an app works
Onboarding (brand hero + "about you" fields) → Home (tool grid + recent results) → Tool (form: text / long text / number / chips / toggle / photo) → structured result (`title`, `summary`, sections of kind text · bullets · steps · checklist · cards · table · kv · callout · quote, tags, follow-ups) → saved on-device with favourites, tickable checklists, copy/share and follow-up questions that keep the result as context.

AI: Claude (Anthropic Messages API with JSON-schema structured output and image blocks) or any OpenAI-compatible endpoint. Keys stay on the device.

## Pipeline
```bash
python3 specsrc/b1_food_health.py    # (each batch writes specs/*.json)
python3 gen.py                       # specs → product flavors + per-flavor res/assets
./build_flavors.sh fpantrypal fbabydays   # signed AAB + APK into dist/
python3 catalog.py                   # listings + CATALOG.md
./ship_factory.sh "message"          # commit, push, one GitHub release per app
```
Release builds are intentionally **unminified** (R8 cost ~3.5 min per flavor on the build box; unminified ~45 s). Turn `isMinifyEnabled` back on for a smaller download if desired.

## Play Console checklist (per app)
1. Create the app with the package name from its listing; upload `dist/<id>-v1.0.aab` (or from the release).
2. Paste short/full description from `listings/<id>.md`; category as listed.
3. Privacy policy URL → this repo's `PRIVACY.md` (make the repo public or host the file).
4. Data safety: no data collected; user content sent to the user's own AI provider on user action.
5. All factory apps are signed with `release.jks` (git-ignored) — back it up, or enrol in Play App Signing.
