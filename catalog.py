#!/usr/bin/env python3
"""Generate store listings (listings/<id>.md) and CATALOG.md from specs/*.json.

- Release links use RELEASED_VERSION: the newest version that actually has GitHub releases (tag <id>-v<ver>).
  ship_factory.sh bumps it to the engine's versionName once every app has been released at that version.
- Listings whose id is no longer in specs/ are deleted, so listings/ always mirrors the catalog.
- Fails (exit 1) if any listing breaks a Play Console limit: title <= 30, short <= 80, full description <= 4000.
"""
import glob, json, os, re, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
TITLE_MAX, SHORT_MAX, FULL_MAX = 30, 80, 4000


def released_version():
    """Contents of RELEASED_VERSION (e.g. "1.0"); "1.0" if the file is missing or empty."""
    try:
        with open(os.path.join(ROOT, "RELEASED_VERSION"), encoding="utf-8") as f:
            v = f.read().strip().lstrip("vV")
    except OSError:
        v = ""
    if v and not re.fullmatch(r"\d+(\.\d+)*", v):
        sys.exit(f"catalog.py: bad RELEASED_VERSION {v!r}")
    return v or "1.0"


def engine_version():
    """versionName in app/build.gradle.kts (the version the next build will carry)."""
    try:
        with open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8") as f:
            m = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', f.read(), re.M)
    except OSError:
        m = None
    return m.group(1) if m else None


# What every app gets from the shared engine, per engine version — one short line each (Play renders plain text).
# Listings describe the version people can download (RELEASED_VERSION), so they never promise unreleased features.
# "{share}" is filled per app: only apps with a photo tool accept shared photos.
FEATURES_BY_VERSION = {
    "1.0": [],  # v1.0 listings had no feature block; the tools list and BYOK paragraph describe the app.
    "1.1": [
        "Clear answers: steps, tickable checklists, tables, cards and key facts",
        "Follow-up questions in a thread that remembers the conversation",
        "Regenerate any answer, or edit your input and run it again",
        "Answers in 40+ languages, brief, standard or detailed",
        "Standing instructions applied to every answer (e.g. \"metric units\")",
        "Read answers aloud, or type with your voice",
        "Save as PDF, export Markdown, copy or share",
        "{share}",
        "Home-screen shortcuts straight to each tool",
        "Favourites, notes, search and filters for everything you save",
        "Backup & restore to a file you choose (never includes your key)",
        "Light, dark or system theme with Material You colours",
        "Usage counter for your requests and tokens",
        "Cancel any request; automatic retry when the provider is busy",
        "Pick any model from your provider's list",
    ],
}

BYOK_BY_VERSION = {
    "1.0": ("Works with Claude (Anthropic) or any OpenAI-compatible https endpoint (OpenAI, Groq, OpenRouter…). "
            "Your key and everything you write stay on your phone; requests go straight from your device to the provider "
            "you chose. No accounts, no ads, no analytics."),
    "1.1": ("Works with Claude (Anthropic) or any OpenAI-compatible endpoint (OpenAI, Groq, OpenRouter, Ollama…). "
            "Your key and everything you write stay on your phone; requests go straight from your device to the provider "
            "you chose. No accounts, no ads, no analytics."),
}


def _vkey(v): return tuple(int(x) for x in v.split("."))


def for_version(table, ver):
    """The entry for the newest version <= ver (the oldest entry if none is)."""
    known = sorted(table, key=_vkey)
    usable = [k for k in known if _vkey(k) <= _vkey(ver)]
    return table[usable[-1] if usable else known[0]]


def has_photo_tool(s):
    return any(f.get("type") == "photo" for t in s["tools"] for f in t.get("inputs", []))


def full_description(s, ver):
    inside = "\n".join(f"• {t['title']} — {t['subtitle']}" for t in s["tools"])
    share = "Share text or photos into the app from any other app" if has_photo_tool(s) else "Share text into the app from any other app"
    feats = [x.replace("{share}", share) for x in for_version(FEATURES_BY_VERSION, ver)]
    block = ("\n\nFEATURES\n" + "\n".join(f"• {x}" for x in feats)) if feats else ""
    return f"""{s['about']}

WHAT'S INSIDE
{inside}{block}

BRING YOUR OWN KEY
{for_version(BYOK_BY_VERSION, ver)}

Every answer is a structured, shareable document with follow-up questions, saved on-device so you can come back to it."""


def main():
    specs = []
    for p in sorted(glob.glob(f"{ROOT}/specs/*.json")):
        with open(p, encoding="utf-8") as f:
            specs.append(json.load(f))
    rel = released_version()
    eng = engine_version()
    os.makedirs(f"{ROOT}/listings", exist_ok=True)

    problems, rows = [], []
    for s in specs:
        full, short = full_description(s, rel), s["tagline"][:SHORT_MAX]
        if len(s["tagline"]) > SHORT_MAX: problems.append(f"{s['id']}: tagline {len(s['tagline'])} > {SHORT_MAX}")
        if len(s["name"]) > TITLE_MAX: problems.append(f"{s['id']}: title {len(s['name'])} > {TITLE_MAX}")
        if len(full) > FULL_MAX: problems.append(f"{s['id']}: full description {len(full)} > {FULL_MAX}")
        tools = "\n".join(f"- {t['emoji']} **{t['title']}** — {t['subtitle']}" for t in s["tools"])
        with open(f"{ROOT}/listings/{s['id']}.md", "w", encoding="utf-8") as f:
            f.write(f"""# {s['name']}

**Category:** {s['category']}  
**Package:** `com.mohithash.byok.{s['id'].replace('_', '')}`  
**Short description (≤80):** {short}

## Full description
{full}

## Tools
{tools}

## Privacy policy
See [PRIVACY.md](../PRIVACY.md). Data safety: no data collected by the developer; user-entered content{' (and optional photos)' if has_photo_tool(s) else ''} sent to the user's chosen AI provider only on user action.
""")
        rows.append(f"| {s['name']} | {s['category']} | {s['tagline']} | [listing](listings/{s['id']}.md) · "
                    f"[release](https://github.com/Mohithash/byok-factory/releases/tag/{s['id']}-v{rel}) |")

    ids = {s["id"] for s in specs}
    stale = [p for p in glob.glob(f"{ROOT}/listings/*.md") if os.path.basename(p)[:-3] not in ids]
    for p in stale:
        os.remove(p)

    if eng and eng != rel:
        links = (f"Release links point to **v{rel}**, the newest version published on GitHub (`RELEASED_VERSION`). "
                 f"The engine is at v{eng}; `ship_factory.sh` switches the links once every app has a v{eng} release. "
                 "[CHANGELOG.md](CHANGELOG.md) lists what each version includes.")
    else:
        links = (f"Release links point to **v{rel}** (`RELEASED_VERSION`), the current engine version. "
                 "[CHANGELOG.md](CHANGELOG.md) lists what each version includes.")
    with open(f"{ROOT}/CATALOG.md", "w", encoding="utf-8") as f:
        f.write(f"""# BYOK app catalog — {len(specs)} apps

All apps share one engine (`app/src/main`) and differ by `specs/<id>.json` (brand, palette, icon, profile fields and 4–5 purpose-built AI tools with tailored prompts). Every app gets every engine feature{f' (v{eng})' if eng else ''}: Material 3 Expressive UI; structured answers (steps, tickable checklists with progress, tables, cards, key facts, callouts, quotes); follow-up questions in a thread; regenerate and edit & rerun; answers in 40+ languages at brief, standard or detailed length, with standing instructions; read aloud and voice typing; save as PDF, export Markdown, copy and share; share text in from other apps (and photos, in apps with a photo tool); a launcher shortcut per tool; on-device history with favourites, notes, search and filters; backup & restore (never the API key); light, dark and Material You themes; a usage counter; BYOK settings (Claude or any OpenAI-compatible endpoint, with the model list fetched from the provider).

{links}

| App | Category | Tagline | Links |
|---|---|---|---|
{chr(10).join(rows)}

## Build
```bash
python3 gen.py                      # specs → flavors + resources
./build_flavors.sh fpantrypal …     # signed AAB + APK per flavor into dist/<id>-v<version>.*
python3 catalog.py                  # listings + this file
./ship_factory.sh "message"         # commit, push, one GitHub release per built app
```
""")

    print(len(specs), "listings", f"(release links v{rel})" + (f", removed {len(stale)} stale" if stale else ""))
    if problems:
        sys.exit("catalog.py: Play limits exceeded:\n  " + "\n  ".join(problems))


if __name__ == "__main__":
    main()
