#!/usr/bin/env python3
"""Generate store listings (listings/<id>.md) and the catalog README from specs."""
import json, glob, os
ROOT = os.path.dirname(os.path.abspath(__file__))
specs = [json.load(open(p)) for p in sorted(glob.glob(f"{ROOT}/specs/*.json"))]
os.makedirs(f"{ROOT}/listings", exist_ok=True)
rows = []
for s in specs:
    tools = "\n".join(f"- {t['emoji']} **{t['title']}** — {t['subtitle']}" for t in s["tools"])
    full = f"""{s['about']}

WHAT'S INSIDE
{chr(10).join(f"• {t['title']} — {t['subtitle']}" for t in s['tools'])}

BRING YOUR OWN KEY
Works with Claude (Anthropic) or any OpenAI-compatible endpoint (OpenAI, Groq, OpenRouter, Ollama…). Your key and everything you write stay on your phone; requests go straight from your device to the provider you chose. No accounts, no ads, no analytics.

Every answer is a structured, shareable document with follow-up questions, saved on-device so you can come back to it."""
    open(f"{ROOT}/listings/{s['id']}.md", "w").write(f"""# {s['name']}

**Category:** {s['category']}  
**Package:** `com.mohithash.byok.{s['id'].replace('_','')}`  
**Short description (≤80):** {s['tagline'][:80]}

## Full description
{full}

## Tools
{tools}

## Privacy policy
See [PRIVACY.md](../PRIVACY.md). Data safety: no data collected by the developer; user-entered content (and optional photos) sent to the user's chosen AI provider only on user action.
""")
    rows.append(f"| {s['name']} | {s['category']} | {s['tagline']} | [listing](listings/{s['id']}.md) · [release](https://github.com/Mohithash/byok-factory/releases/tag/{s['id']}-v1.0) |")
open(f"{ROOT}/CATALOG.md", "w").write(f"""# BYOK app catalog — {len(specs)} apps

All apps share one engine (`app/src/main`) and differ by `specs/<id>.json` (brand, palette, icon, profile fields and 4–5 purpose-built AI tools with tailored prompts). Every app: Material 3 Expressive UI, on-device history with favourites and checklists, follow-up questions, share/copy, BYOK settings (Claude or OpenAI-compatible).

| App | Category | Tagline | Links |
|---|---|---|---|
{chr(10).join(rows)}

## Build
```bash
python3 gen.py                      # specs → flavors + resources
./build_flavors.sh fpantrypal …     # signed AAB + APK per flavor into dist/
```
""")
print(len(specs), "listings")
