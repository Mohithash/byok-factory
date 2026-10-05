# BYOK Factory

One engine, 603 apps. Each app is a JSON spec (brand, palette, icon, profile fields, tools with tailored prompts) rendered by a shared Jetpack Compose / Material 3 Expressive engine, built as a Gradle product flavor, and shipped as a signed AAB (Play Console) + APK (sideload). Every app gets every engine feature; only the spec differs.

- **Catalog:** [CATALOG.md](CATALOG.md) — every app with category, tagline, listing and release links.
- **Store listings:** `listings/<id>.md` — short/full descriptions, package name, category, tools.
- **Privacy policy:** [PRIVACY.md](PRIVACY.md) — one policy covers all apps (BYOK, on-device only).
- **Changelog:** [CHANGELOG.md](CHANGELOG.md) — what each engine version adds.

## Features
Engine v1.1 (`versionName` in `app/build.gradle.kts`). Everything below is in every app.

### Answers
- Structured answers: text, bullets, steps, tickable checklists with a progress bar, cards, tables, key facts, callouts and quotes, with inline **bold** and _italic_.
- Follow-up questions keep the whole thread as context (the last 4 turns), with a thread view of the conversation.
- Regenerate an answer, or edit & rerun with the original inputs pre-filled.
- Answers in 40+ languages, at brief, standard or detailed length.
- Standing custom instructions ("I'm in the UK", "metric units") added to every request.

### Working with results
- Save as PDF (Android print), export Markdown, copy, share.
- Read aloud with the device's text-to-speech engine.
- Pinned favourites, notes, rename, search inside results, filters by tool, date groups.
- Swipe to delete, with undo.

### Input
- Tool forms: text, long text, number, chips, toggle and photo fields; profile fields from onboarding go into every prompt.
- Voice typing through the system speech recogniser.
- Share text (or a text file) INTO the app from any other app — and photos, in apps that have a photo tool; pick a tool and the content is filled in.
- A launcher shortcut for each tool (long-press the app icon).

### Data & privacy
- Everything stays on the device: no accounts, analytics, ads or servers of our own.
- Backup & restore to a JSON file the user saves wherever they choose; it never includes the API key.
- The API key lives in its own preferences file, excluded from Android cloud backup and device-to-device transfer.
- Per-device usage counter (requests, input and output tokens), so BYOK users can see what they spend.

### Look & feel
- Material 3 Expressive UI in the brand palette from the spec.
- Light, dark or system theme, and Material You (wallpaper) colours on Android 12+.

### AI providers
- Claude via the Anthropic Messages API (JSON-schema structured output, image blocks). Default model `claude-opus-5-5`, with server-side refusal fallback on supported models (switched off automatically if the server rejects it).
- Any OpenAI-compatible endpoint (OpenAI, Groq, OpenRouter, Ollama…).
- The model list is fetched from the provider.
- Cancel a running request. Rate limits and overloads (408/409/429/5xx/529, network errors) are retried automatically with backoff, honouring `retry-after`.

## How an app works
Onboarding (brand hero + "about you" profile fields) → **Home** (tool grid, then saved results with search, tool filters, date groups and pinned favourites) → **Tool** (form: text / long text / number / chips / toggle / photo, with voice typing; also reachable from a launcher shortcut or by sharing text — or, in apps with a photo tool, a photo — into the app) → **Result**: a structured document (`title`, `summary`, sections of kind text · bullets · steps · checklist · cards · table · kv · callout · quote, tags, suggested follow-ups) saved on-device.

From a result: ask a follow-up (the thread view shows the whole conversation), regenerate, edit & rerun, tick checklist items, read aloud, save as PDF, export Markdown, copy, share, favourite, add a note, rename, delete (with a confirmation; swipe-to-delete in History has undo).

Every request is built from the spec persona, the user's profile, the preferences (answer language, length, standing instructions) and the tool's prompt with its inputs; follow-ups add the last 4 turns of the thread. It goes straight from the phone to the user's provider with the user's key. **Settings** holds provider, key, base URL and model (list fetched from the provider), language, length, instructions, theme, Material You, backup & restore, and the usage counter.

## Pipeline
```bash
python3 specsrc/b1_food_health.py    # (each batch writes specs/*.json — see specsrc/AUTHORING.md)
python3 gen.py                       # specs → product flavors + per-flavor res/assets
./build_flavors.sh fpantrypal fbabydays   # signed AAB + APK into dist/<id>-v<VERSION>.{aab,apk}
python3 catalog.py                   # listings/*.md + CATALOG.md (release links use RELEASED_VERSION)
./ship_factory.sh "message"          # commit, push, one GitHub release <id>-v<VERSION> per built app
```
For the whole catalog, `mkdir -p dist && ./stream_build3.sh > dist/stream.log 2>&1 &` builds every spec without a `dist/<id>-v<VERSION>.aab` in chunks of 20, releasing after each chunk (`stream_build2.sh`: chunks of 10; `stream_build.sh`: all at once). They stop when nothing is left and `STOP_STREAM2` (or `STOP_STREAM`) exists — `touch STOP_STREAM2` once you want the stream to exit after the last chunk (the stop files are git-ignored). `./refresh_store.sh &` republishes the BYOK Store every 20 minutes until the log says `STREAM-DONE` (`STORE=/path/to/byok-store`, default `../byok-store`; `STREAM_LOG`, default `dist/stream.log`). `./build_all.sh list.txt` only builds the flavors listed in a file. All scripts work from any directory.

Release builds are intentionally **unminified** (R8 cost ~3.5 min per flavor on the build box; unminified ~45 s). Turn `isMinifyEnabled` back on for a smaller download if desired.

### Versions
- **VERSION** is `versionName` in `app/build.gradle.kts`. The build and release scripts read it for `dist/` file names and release tags (`<id>-v<VERSION>`), so nothing else needs editing.
- **Shipping a new engine version:** bump `versionCode` and `versionName`, add a section to [CHANGELOG.md](CHANGELOG.md) and its headline features to `WHATS_NEW` in `ship_factory.sh` (release notes) and to `FEATURES_BY_VERSION` in both the store's `store_site.py` and this repo's `catalog.py` (plus `BYOK_BY_VERSION` there if provider support changed), then build and ship every app (e.g. `stream_build3.sh`).
- **RELEASED_VERSION** holds the newest version that has GitHub releases (now `1.0`). `catalog.py` and the BYOK Store link to `<id>-v<RELEASED_VERSION>`, so links keep working while a new version is being built. Once **every** app in `specs/` has a `<id>-v<VERSION>` release, `ship_factory.sh` writes VERSION into `RELEASED_VERSION`, re-runs `catalog.py` and commits + pushes the result. To switch by hand: `echo 1.1 > RELEASED_VERSION && python3 catalog.py`.

## Developing the engine
The engine is `app/src/main` (`ai/` provider client, `data/` Room database + backups, `engine/` prompts, preferences and document formats, `ui/` Compose screens); JVM unit tests are in `app/src/test`.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties   # git-ignored; JDK 17+
python3 gen.py fpantrypal                                  # resources for every spec, but only this flavor in the build file
./gradlew :app:testFpantrypalDebugUnitTest :app:assembleFpantrypalDebug
git checkout app/build.gradle.kts                          # restore the committed flavor block
```
`gen.py` with flavor arguments rewrites the `FLAVORS-BEGIN … FLAVORS-END` block of `app/build.gradle.kts` to just those flavors (a fast Gradle sync instead of 603); with no arguments it writes all of them. `build_flavors.sh` does the same for the flavors it builds. `git checkout app/build.gradle.kts` restores the committed file, and `ship_factory.sh` commits with `git add -A`, so restore it first if you don't want the flavor list committed. Per-flavor sources (`app/src/f*/`) are generated and git-ignored.

## Play Console checklist (per app)
1. Create the app with the package name from its listing; upload `dist/<id>-v<VERSION>.aab` (or the one attached to its GitHub release).
2. Paste short/full description from `listings/<id>.md`; category as listed.
3. Privacy policy URL → this repo's `PRIVACY.md` (make the repo public or host the file).
4. Data safety: no data collected; user content sent to the user's own AI provider on user action. Unchanged for v1.1 (see [PRIVACY.md](PRIVACY.md#play-data-safety)).
5. All factory apps are signed with `release.jks` (git-ignored) — back it up, or enrol in Play App Signing.
