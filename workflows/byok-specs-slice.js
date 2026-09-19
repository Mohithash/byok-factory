export const meta = {
  name: 'byok-specs-slice',
  description: 'Author a slice of BYOK app specs (themes chosen by args) with adversarial review',
  phases: [
    { title: 'Author', detail: '50 agents × 10 apps each, one batch file per theme' },
    { title: 'Review', detail: 'adversarial quality reviewer fixes each batch in place' },
  ],
}

const THEMES = [
  ['w01_baking', 'Food & Drink — baking, desserts, pastry, chocolate, cake decorating'],
  ['w02_cuisines', 'Food & Drink — specific world cuisines (Indian, Mexican, Japanese, Italian, Korean, Middle Eastern, West African…) each as its own app'],
  ['w03_drinks', 'Food & Drink — home brewing, fermentation, smoothies, juicing, tea/coffee niches, non-alcoholic'],
  ['w04_budget_cook', 'Food & Drink — student cooking, family meal planning, air fryer, slow cooker, one-pan, camping food'],
  ['w05_wellbeing', 'Health & Fitness — stress, burnout, mindfulness, gratitude, therapy homework, journaling niches'],
  ['w06_chronic', 'Medical — self-management companions for diabetes, hypertension, asthma, migraine, thyroid, IBS, arthritis (general info, strong disclaimers)'],
  ['w07_womens', 'Health & Fitness / Medical — pregnancy, postpartum, menopause, fertility awareness, pelvic health (general info, disclaimers)'],
  ['w08_mens_ageing', 'Health & Fitness — men\'s health, healthy ageing 50+, mobility, balance, longevity habits (general info, disclaimers)'],
  ['w09_strength', 'Health & Fitness — home workouts, calisthenics, kettlebells, powerlifting, mobility, physio-style rehab (disclaimers)'],
  ['w10_endurance', 'Sports — cycling, triathlon, hiking, trail running, rowing, open-water, ultra events'],
  ['w11_movement', 'Sports / Health — martial arts, pilates, dance, climbing, skiing, surfing, golf, tennis technique'],
  ['w12_budget_niche', 'Finance — budgeting for students, couples, new parents, freelancers, gig workers, immigrants (disclaimers)'],
  ['w13_invest_edu', 'Finance — investing education, retirement planning basics, index funds, FIRE, debt strategies (general info only, disclaimers)'],
  ['w14_smallbiz_fin', 'Business — bookkeeping, cash flow, pricing, e-commerce ops, restaurant/shop owners, invoicing niches'],
  ['w15_writing', 'Productivity — note-taking, journaling systems, memoir, blogging, newsletters, technical writing'],
  ['w16_students', 'Education — study systems for university students: thesis, citations, lab reports, group projects, exam anxiety'],
  ['w17_managers', 'Business — managers: 1:1s, performance reviews, OKRs, hiring, team rituals, difficult feedback'],
  ['w18_remote', 'Productivity — remote work, freelancing, digital nomads, time zones, async communication, contracts'],
  ['w19_languages', 'Education — one app per language learning niche (Spanish for travel, business English, Japanese kana, Arabic script, German grammar, French pronunciation, Korean K-drama, sign language basics…)'],
  ['w20_stem', 'Education — physics, chemistry, statistics, calculus, biology, computer science concepts, electronics'],
  ['w21_testprep', 'Education — SAT/ACT, IELTS/TOEFL, GRE, citizenship tests, driving theory, professional certifications (general prep)'],
  ['w22_kids_learn', 'Education / Parenting — early reading, phonics, early maths, science for kids, handwriting, kids coding'],
  ['w23_jobsearch', 'Business — job search by field: nurses, teachers, engineers, trades, retail, hospitality, government'],
  ['w24_leadership', 'Business — leadership, executive communication, presentations, coaching, conflict, delegation'],
  ['w25_marketing', 'Business — marketing: SEO briefs, ads copy, email campaigns, landing pages, brand voice, influencer outreach'],
  ['w26_sales', 'Business — sales: cold outreach, discovery calls, objection handling, proposals, account plans, customer success'],
  ['w27_startup_ops', 'Business — startup ops: pitch decks, investor updates, OKRs, hiring plans, policies, vendor evaluation'],
  ['w28_devtools', 'Tools — developer helpers per stack: React, Python data, Kotlin/Android, iOS Swift, Rust, DevOps/Kubernetes, shell scripting, testing'],
  ['w29_data', 'Tools — data & analytics: spreadsheet analysis, chart design, survey design, A/B tests, KPI dashboards, SQL for analysts'],
  ['w30_writing_tools', 'Tools — editing: grammar coach, plain-language rewriter, translation checker, screenplay format, poetry, speech-to-outline'],
  ['w31_home_diy', 'House & Home — interior design, small-space living, cleaning schedules, laundry, organising kitchens, renovation budgets'],
  ['w32_pets', 'Lifestyle — one app per pet type: rabbits, aquarium fish, reptiles, birds, hamsters, horses, backyard chickens, senior dogs'],
  ['w33_fashion', 'Beauty / Lifestyle — men\'s style, wardrobe by body type, makeup, nails, fragrance, sustainable fashion, thrifting'],
  ['w34_relationships', 'Lifestyle — couples communication, long-distance, friendship, in-laws, co-parenting, boundaries'],
  ['w35_hobbies', 'Art & Design / Music — drawing, watercolour, guitar, piano, singing, DJing, podcasting, film photography, calligraphy'],
  ['w36_outdoors', 'Lifestyle — camping, fishing, foraging, birdwatching, kayaking, backpacking, van life, mountaineering'],
  ['w37_travel_types', 'Travel & Local — backpacking, cruises, family trips, honeymoon, solo female travel, business travel, road trips in specific regions'],
  ['w38_expat', 'Travel & Local — relocation, expat life, visas & paperwork (general), culture shock, international students'],
  ['w39_babies', 'Parenting — newborn sleep, breastfeeding, weaning, toddler tantrums, potty training, twins'],
  ['w40_kids_teens', 'Parenting — school-age routines, screen time, homework, teen mental health, college applications, learning differences'],
  ['w41_social', 'Social / Events — community organising, club management, volunteering, meetups, dinner clubs, reunions'],
  ['w42_games', 'Games / Entertainment — D&D game master, puzzle setters, party games, trivia, board-game design, video-game guides'],
  ['w43_vehicles', 'Auto & Vehicles — motorcycles, EV ownership, classic cars, boats, RVs, driving lessons, car detailing'],
  ['w44_shopping', 'Shopping — deal hunting, product comparison, secondhand selling, sustainable shopping, gadget buying guides'],
  ['w45_faith_culture', 'Books & Reference / Lifestyle — scripture study helpers across faiths (respectful, non-proselytising), cultural etiquette, holidays & traditions'],
  ['w46_seniors', 'Lifestyle / Medical — seniors & accessibility: tech help for elders, memory games, retirement life, low-vision friendly planning'],
  ['w47_realestate', 'House & Home / Finance — home buying, mortgages (general), renovation planning, landlord/tenant niches, moving abroad'],
  ['w48_everyday_legal', 'Tools — everyday legal-ish (general info): consumer complaints, small claims prep, contracts explained, wills basics, workplace rights'],
  ['w49_celebrations', 'Events — birthdays, baby showers, holidays (Diwali, Eid, Christmas, Lunar New Year), proposals, retirement parties'],
  ['w50_sustainability', 'Lifestyle — zero waste, composting, urban gardening, cycling commute, repair culture, energy saving, ethical investing basics'],
]

const AUTHOR_SCHEMA = { type: 'object', properties: {
  file: { type: 'string' }, ids: { type: 'array', items: { type: 'string' } }, names: { type: 'array', items: { type: 'string' } }, notes: { type: 'string' } },
  required: ['file', 'ids', 'names'] }
const REVIEW_SCHEMA = { type: 'object', properties: {
  file: { type: 'string' }, ids: { type: 'array', items: { type: 'string' } }, fixed: { type: 'array', items: { type: 'string' } }, dropped: { type: 'array', items: { type: 'string' } }, ok: { type: 'boolean' } },
  required: ['file', 'ids', 'fixed', 'dropped', 'ok'] }

const SLICE = THEMES.filter((_, i) => args.indexes.includes(i))
log(`slice: ${SLICE.map(t => t[0]).join(', ')}`)
const results = await pipeline(SLICE,
  ([batch, theme], _item, i) => agent(`You are authoring a batch of 10 bring-your-own-key Android app specs for an app factory.

Read /root/claude/Factory/specsrc/AUTHORING.md (the DSL and quality bar) and skim /root/claude/Factory/specsrc/b1_food_health.py for two full examples. The existing catalog (ids — names) is in /tmp/claude-0/-root-claude/6364fb78-2035-42f3-a90d-a702d774c6de/scratchpad/existing.txt — do NOT duplicate any of those concepts or names.

Your theme: ${theme}
Write EXACTLY 10 distinct, specific, installable app concepts within this theme. Each app gets 4–5 tools with concrete prompts that reference every input as {key} and specify sections. Use varied palettes and icons. Add a disclaimer for anything medical/mental-health/legal/financial/safety.

Write the file /root/claude/Factory/specsrc/${batch}.py (start with \`from dsl import *\`), then run: cd /root/claude/Factory/specsrc && python3 ${batch}.py — fix until it runs clean. Then verify each specs/<id>.json parses. Every id must start with a letter, use only [a-z0-9_], be ≤24 chars, and be unique across the catalog.

Return JSON: file, ids (the 10 ids), names (the 10 app names), notes (anything the reviewer should know).`, { label: `author:${batch}`, phase: 'Author', schema: AUTHOR_SCHEMA }),
  (authored, [batch, theme]) => authored ? agent(`You are an adversarial quality reviewer for BYOK app specs. Assume the batch has problems and find them.

Read /root/claude/Factory/specsrc/AUTHORING.md, then open /root/claude/Factory/specsrc/${batch}.py (theme: ${theme}; ids: ${authored.ids.join(', ')}).

Check EVERY app and tool for: (1) every input key appears as {key} in its prompt; (2) 4–5 tools, each a genuinely different job; (3) ids match ^[a-z][a-z0-9_]{0,23}$ and are not in /tmp/claude-0/-root-claude/6364fb78-2035-42f3-a90d-a702d774c6de/scratchpad/existing.txt (ids or names); (4) prompts specify concrete sections from the allowed kinds and ask for specific, structured, useful output (named table columns, card meta labels, counts); (5) disclaimers present for medical/mental-health/legal/financial/safety apps; (6) icon is from the allowed list and colors are 3 valid hex; (7) taglines ≤60 chars, buttons ≤18 chars; (8) the app is a real niche, not a generic assistant; (9) no near-duplicate apps inside the batch.

FIX every problem directly in the file (rewrite prompts, rename ids, drop a hopeless app only if unfixable). Then run: cd /root/claude/Factory/specsrc && python3 ${batch}.py and confirm it runs clean and every specs/<id>.json parses. If you renamed an id, delete the stale specs/<old_id>.json.

Return JSON: file, ids (final list), fixed (short list of what you changed), dropped (ids removed), ok (true if the batch now passes all checks).`, { label: `review:${batch}`, phase: 'Review', schema: REVIEW_SCHEMA }) : null,
)

const done = results.filter(Boolean)
const allIds = done.flatMap(r => r.ids)
const dupes = allIds.filter((id, i) => allIds.indexOf(id) !== i)
log(`${done.length}/${SLICE.length} batches reviewed, ${allIds.length} ids, ${dupes.length} cross-batch duplicate ids`)
return { batches: done.length, ids: allIds.length, dupes, notOk: done.filter(r => !r.ok).map(r => r.file), dropped: done.flatMap(r => r.dropped) }