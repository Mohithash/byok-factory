# Authoring a batch of app specs

Write ONE Python file `specsrc/<batchname>.py` that starts with `from dsl import *` and calls `APP(...)` once per app. Run it with `cd /root/claude/Factory/specsrc && python3 <batchname>.py` — it writes `specs/<id>.json` and raises on invalid input. Then validate: `cd /root/claude/Factory && python3 -c "import json;json.load(open('specs/<id>.json'))"` for each id.

## DSL
- `APP(id, name, tagline, category, colors, icon, about, persona, ob_title, ob_sub, bullets, profile, tools, disclaimer="")`
  - `id`: `^[a-z][a-z0-9_]*$`, unique, ≤ 24 chars, becomes package `com.mohithash.byok.<id without _>`.
  - `name`: 1–3 words, distinctive (avoid generic "AI Helper"). `tagline` ≤ 60 chars, benefit-led.
  - `category`: a Play category, e.g. Food & Drink, Health & Fitness, Medical, Finance, Productivity, Business, Education, Tools, Lifestyle, House & Home, Parenting, Travel & Local, Social, Dating, Games, Sports, Beauty, Books & Reference, Music & Audio, Photography, Auto & Vehicles, Shopping, Communication, Art & Design, Events, Entertainment.
  - `colors`: three hex seeds `["#primary", "#secondary", "#tertiary"]` — pick a distinctive, category-appropriate palette (not all blue).
  - `icon`: one of spark, leaf, chat, book, heart, bolt, drop, flame, gear, pin, camera, cart, bag, pill, dumbbell, plane, home, note, bulb, coin, calendar, music, paw, star, brain, shield, sun, moon, car, cup, wrench, baby, gift, mic, graph, globe, scale, hat, tooth, ring, clock.
  - `about`: 1–2 sentences for the store listing. `persona`: the system persona (role, values, boundaries), 1–2 sentences.
  - `ob_title`/`ob_sub`: onboarding headline + one sentence. `bullets`: 3 short benefit bullets.
  - `profile`: 1–4 fields the app remembers (use `NAME`, `CH(...)`, `TXT(...)`, `NUM(...)`, `F(...)`). Referenced in prompts via `{profile}` automatically (the engine injects "User profile — …" into the system prompt; you may also write `{profile}` in a prompt).
  - `tools`: 4–5 `T(...)` entries. Each is a distinct job the app does, not a rephrasing.
  - `disclaimer`: REQUIRED for medical, mental-health, legal, financial, safety topics (the engine appends it as a callout).
- `T(id, title, sub, emoji, inputs, prompt, shape, button, loading)`
  - `inputs`: list of fields: `LONG(key, label, placeholder, required=True)`, `TXT(key, label, placeholder, required=False)`, `CH(key, label, [options], default, multi)`, `NUM(key, label, placeholder)`, `PHOTO(label, hint, required)` (key is always `photo`), `F(key, label, type, placeholder, options, default, required, multi)` with type text|longtext|number|chips|photo|toggle. 0–3 inputs per tool; at least one tool with zero inputs is fine ("Plan my week" from profile).
  - `prompt`: the instruction. MUST reference every input key as `{key}` (e.g. `{pantry}`). Say exactly which sections to produce and what goes in each, using the section kinds below. Be concrete about counts, units, ordering. 2–5 sentences.
  - `shape`: comma list of preferred section kinds in order, from: text, bullets, steps, checklist, cards, table, kv, callout, quote.
  - `button`: verb phrase ≤ 18 chars. `loading`: playful ≤ 20 chars.

## Quality bar
- Each app must be a specific niche a real person would install (not "general assistant"). Tools should feel like features of that niche product.
- Prompts must produce structured, useful output: tables with named columns, cards with meta labels (time/cost/level), checklists that can be ticked, callouts for safety/important notes, quote for scripts/messages/templates.
- Use profile context: prompts can say "for the profile" — the engine already injects it.
- No duplicates with the existing catalog (list provided). Distinct ids and names.
- Photo inputs are valuable for identify/scan/critique tools — use where natural.
- Tone: helpful, specific, never preachy. Keep everything in English.

## Example (abbreviated)
```python
from dsl import *
APP("pantry_pal", "Pantry Pal", "Dinner from what's already in your kitchen", "Food & Drink", ["#D9481F", "#2E7D32", "#F9A825"], "leaf",
 "Pantry Pal turns whatever is in your fridge into real dinner ideas, shopping lists and swaps.",
 "You are a resourceful, warm home cook who knows real technique and cheap staples.",
 "What's in your kitchen?", "Tell Pantry Pal how you eat. Then snap or type what you have.", ["Recipes from your actual pantry", "Swaps for anything missing", "Weekly plan + shopping list"],
 [NAME, CH("diet", "Diet", ["Anything", "Vegetarian", "Vegan", "Halal", "Keto", "Gluten-free"]), TXT("allergies", "Allergies / avoid", "peanuts, shellfish…")],
 [T("cook", "What can I cook?", "Recipes that use what you have", "🍳",
    [LONG("pantry", "What's in your kitchen", "eggs, rice, half an onion…"), CH("mood", "Mood", ["Quick", "Comfort", "Light"]), NUM("time", "Max minutes", "30")],
    "Pantry: {pantry}. Mood: {mood}. Max time: {time} minutes. Propose 3 recipes as cards (meta = minutes · missing count). Then the full method of the best one as steps, and a bullets section 'Missing' listing anything not in the pantry.",
    "cards, steps, bullets", "Suggest recipes", "Raiding the pantry…"),
  T("scan", "Scan my fridge", "Photo → inventory + ideas", "📷", [PHOTO("Photo of your fridge", "", True)],
    "Catalogue every food item visible as a table (Item | Qty | Category | Use by (est.)). Then 3 meals as cards, and a checklist of what to use first.", "table, cards, checklist", "Scan", "Looking inside…"),
  # …2–3 more tools
 ])
```
