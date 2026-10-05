# Changelog

All apps share one engine, so each version applies to **every app in the catalog**. GitHub release tags are `<id>-v<version>`; `RELEASED_VERSION` names the newest version released for every app (see [README.md](README.md#versions)).

## 1.1 — engine update (versionCode 2) — not yet released
Upgrading keeps all existing history (Room database migration v1 → v2).

### Answers
- Structured answers: text, bullets, steps, tickable checklists with progress, cards, tables, key facts, callouts and quotes, with inline **bold** and _italic_.
- Follow-up questions keep the whole thread as context (last 4 turns), with a thread view.
- Regenerate, and edit & rerun with the original inputs pre-filled.
- Answers in 40+ languages.
- Answer length: brief, standard or detailed.
- Standing custom instructions added to every request.

### Working with results
- Read aloud (on-device text-to-speech).
- Save as PDF (Android print), export Markdown, copy, share.
- Pinned favourites, notes, rename, search inside results, filters by tool, date groups.
- Swipe to delete with undo.

### Input
- Voice typing (system speech recogniser).
- Share text or photos into the app from any other app.
- Launcher shortcuts for each tool.

### Data & privacy
- Backup & restore to a JSON file you save where you choose; it never includes the API key. Importing keeps follow-ups attached to their threads and skips results you already have.
- The API key moved to its own preferences file, excluded from Android cloud backup and device-to-device transfer.
- Per-device usage counter (requests and tokens).
- [PRIVACY.md](PRIVACY.md) updated for the new features; Play data-safety answers unchanged.

### Look & feel
- Light, dark or system theme.
- Material You (wallpaper) colours on Android 12+.

### AI providers
- Claude: default model `claude-opus-5-5`, with server-side refusal fallback on supported models (switched off automatically if the server rejects it).
- Any OpenAI-compatible endpoint, as before.
- Model list fetched from the provider.
- Cancel a running request.
- Automatic retry with backoff on rate limits, overload and network errors (408/409/429/5xx/529), honouring `retry-after`.
- Clearer error messages; answers cut off by the token limit are detected.

### Fixes
- Photo inputs read as "the attached photo" in prompts, never "(not given)".
- Cancelling a request no longer overwrites the request that replaced it.
- Malformed JSON from a model shows a readable error.
- `effort` is not sent to models that don't accept it (Haiku 4.5, Sonnet 4.5, Claude 3.x) and is dropped automatically if a model rejects it.
- `max_tokens` is capped at 8000 for OpenAI-compatible endpoints.

### Factory
- Build and release scripts read the version from `app/build.gradle.kts` (`dist/<id>-v<version>.*`, tags `<id>-v<version>`), run from any directory, and release notes list what's new.
- `RELEASED_VERSION` keeps catalog and store links on the last fully released version until every app has the new one.
- Store listings gain a FEATURES block; listings for removed specs are deleted.

## 1.0 — 2026-09-19 (versionCode 1)
Initial release: **603 apps**, each with 4–5 purpose-built AI tools, as signed AABs (Play Console) and APKs (sideload) on GitHub Releases.
- Onboarding with brand hero and "about you" profile fields used in every prompt.
- Tool forms: text, long text, number, chips, toggle and photo inputs.
- Structured results (steps, checklists, tables, cards, key facts, callouts, quotes) with tags and suggested follow-ups.
- Follow-up questions that keep the result as context.
- On-device history with favourites, search and tickable checklists; copy and share.
- Material 3 Expressive UI in each app's own palette and icon.
- Claude (Anthropic Messages API, structured output, image input) or any OpenAI-compatible endpoint (OpenAI, Groq, OpenRouter, Ollama…); keys stay on the device.
