# Privacy Policy — BYOK Factory apps

_Last updated: 2026-10-05 (app version 1.1)_

This policy covers **every app in this catalog** (see [CATALOG.md](CATALOG.md)). Each one is a bring‑your‑own‑key (BYOK) app: it has no accounts, no analytics, no ads, and no servers of its own. **The developer collects no data** — nothing you do in the app is ever sent to us.

## What the app stores
Everything is stored **only on your device**, in the app's private storage:
- your results, follow-up threads, ticked checklist items, favourites, notes and renamed titles;
- the text you typed into a tool for each result, so you can regenerate it or edit & rerun it (photos are not stored);
- your profile answers and preferences (theme, colours, answer language and length, standing instructions);
- a usage counter (number of requests and tokens) so you can see what you spend on your own key — it stays on the device and is never sent anywhere;
- your AI provider settings and API key. The key is kept in a separate file that is **excluded from Android cloud backup and device-to-device transfer**.

Uninstalling the app deletes all of it.

## What leaves your device
When you use an AI feature (run a tool, ask a follow-up, regenerate), the request is sent **directly from your phone to the AI provider you configured** (Anthropic, OpenAI, or any OpenAI‑compatible endpoint you entered), authenticated with **your** API key. A request contains what you typed (and the photo, if you attached one), your profile answers, your answer preferences and standing instructions, and — for a follow-up — the earlier turns of that thread (up to the last 4). When you open the model list, the app asks the same provider which models your key can use. Nothing is routed through us. Requests use HTTPS, except to a plain `http://` address you enter yourself (for example a model server such as Ollama or LM Studio on your own device or network) — those are not encrypted, and the app warns you when you enter one. Those providers process requests under their own privacy policies:
- Anthropic: https://www.anthropic.com/privacy
- OpenAI: https://openai.com/policies/privacy-policy

The app never sends anything to an AI provider unless you trigger an AI action.

## Features that use Android system services
These features hand data to services that are part of your device, not to us:
- **Read aloud** uses the text-to-speech engine installed on your device. The answer's text is passed to that engine to be spoken; the app sends it nowhere else.
- **Voice typing** uses your device's system speech recogniser. The app does not record audio itself and has no microphone permission; the recogniser listens and returns the text into the field you are typing in. Depending on the speech provider configured on your device (for example Google), your audio may be sent to that provider to be transcribed, under its own privacy policy. Nothing is sent to your AI provider until you run the tool.
- **Sharing into the app** only processes what you choose to share (text, or one photo). It fills in a tool's form; nothing is sent until you run the tool. Shared photos are handled like attached photos (below).
- **Save as PDF, export, copy and share** use Android's print service, file picker, clipboard and share sheet. Files go wherever you choose to save or send them.
- **Launcher shortcuts** contain only the names of the app's tools.

## Backups
- **Backup & restore** writes a JSON file with your results, notes, profile and preferences to a location **you** choose with the Android file picker, and restores from a file you pick. The file **never includes your API key**. We never receive it; keep it private, since it contains what you wrote.
- **Android backup:** if you have turned on Android's backup or device-to-device transfer, Android may include the app's data (results, profile, preferences) in that backup, under your Google account's settings. The API key is always excluded.

## Permissions
- **Internet** — required only to reach the AI provider you chose.
- **Camera / Photos** — optional; used only when you choose to attach or share a photo. Photos are downscaled on‑device and sent only as part of that AI request. They are not stored by the app.
- The app declares that it uses the device's text-to-speech and speech-recognition services so it can offer read aloud and voice typing; it requests no microphone permission.

## Play data safety
The Google Play data-safety answers are **unchanged from version 1.0**: _no data collected by the developer; user-entered content (and optional photos) sent to the user's chosen AI provider only on user action._ The only data that leaves the app is the request you send, on your action, to the AI provider you configured with your own key — the same destination and trigger as in 1.0 (a request may now also carry your standing instructions and the earlier turns of a thread, and opening the model list asks that provider for its models). Everything new in 1.1 either stays on the device (usage counter, preferences, notes, backup files you save yourself) or is handled by Android system services you set up and control (text-to-speech, speech recognition, printing, sharing, Android backup), from which the developer receives nothing.

## Children
The app is not directed at children under 13.

## Changes
Changes to this policy are listed in [CHANGELOG.md](CHANGELOG.md) and dated above.

## Contact
Open an issue at https://github.com/Mohithash/byok-factory/issues.
