#!/usr/bin/env python3
"""Generate product flavors + per-app resources from specs/*.json."""
import json, glob, os, re, colorsys, sys
ONLY = set(a for a in sys.argv[1:] if a.startswith("f"))  # flavor names to emit into build.gradle.kts (default: all)

ROOT = os.path.dirname(os.path.abspath(__file__))
GLYPHS = {
 "spark": "M12,2 l2.6,6.4 l6.4,2.6 l-6.4,2.6 l-2.6,6.4 l-2.6,-6.4 l-6.4,-2.6 l6.4,-2.6 z",
 "leaf": "M12,22 c0,0 -1,-5 -1,-9 c0,-2 0.5,-3 1,-4 c0.5,1 1,2 1,4 c0,4 -1,9 -1,9 z M11,13 C7,13 4,10 4,6 c4,0 7,3 7,7 z M13,11 c0,-4 3,-7 7,-7 c0,4 -3,7 -7,7 z",
 "chat": "M4,4 h16 a2,2 0 0 1 2,2 v9 a2,2 0 0 1 -2,2 h-9 l-4,3.5 V17 H4 a2,2 0 0 1 -2,-2 V6 a2,2 0 0 1 2,-2 z",
 "book": "M4,5 a2,2 0 0 1 2,-2 h5 v16 H6 a2,2 0 0 0 -2,2 z M13,3 h5 a2,2 0 0 1 2,2 v16 a2,2 0 0 0 -2,-2 h-5 z",
 "heart": "M12,21 l-1.5,-1.4 C5.4,15 2,11.9 2,8.1 C2,5 4.4,2.6 7.5,2.6 c1.7,0 3.4,0.8 4.5,2.1 c1.1,-1.3 2.8,-2.1 4.5,-2.1 C19.6,2.6 22,5 22,8.1 c0,3.8 -3.4,6.9 -8.5,11.5 z",
 "bolt": "M13,2 L4,14 h6 l-1,8 l9,-12 h-6 z",
 "drop": "M12,2 C8,8 5,11 5,15 a7,7 0 0 0 14,0 c0,-4 -3,-7 -7,-13 z",
 "flame": "M12,2 c1,4 4,5 4,9 a4,4 0 0 1 -8,0 c0,-1.5 0.5,-2.5 1,-3.5 c0.3,1 1,1.8 2,2 c-0.3,-2 -0.5,-5 1,-7.5 z M6,13 a6,6 0 0 0 12,0 c0,4 -2,9 -6,9 s-6,-5 -6,-9 z",
 "gear": "M12,8 a4,4 0 1 0 0.01,0 z M10.5,2 h3 l0.5,2.5 l2,1 l2.3,-1.2 l2.1,2.1 l-1.2,2.3 l1,2 L22,10.5 v3 l-2.5,0.5 l-1,2 l1.2,2.3 l-2.1,2.1 l-2.3,-1.2 l-2,1 L13.5,22 h-3 l-0.5,-2.5 l-2,-1 l-2.3,1.2 l-2.1,-2.1 l1.2,-2.3 l-1,-2 L2,13.5 v-3 l2.5,-0.5 l1,-2 L4.3,5.7 l2.1,-2.1 l2.3,1.2 l2,-1 z",
 "pin": "M12,2 a7,7 0 0 0 -7,7 c0,5 7,13 7,13 s7,-8 7,-13 a7,7 0 0 0 -7,-7 z M12,6.5 a2.5,2.5 0 1 1 0,5 a2.5,2.5 0 0 1 0,-5 z",
 "camera": "M9,3 l-1.8,2 H4 a2,2 0 0 0 -2,2 v11 a2,2 0 0 0 2,2 h16 a2,2 0 0 0 2,-2 V7 a2,2 0 0 0 -2,-2 h-3.2 L15,3 z M12,8 a4.5,4.5 0 1 1 0,9 a4.5,4.5 0 0 1 0,-9 z",
 "cart": "M2,3 h3 l3.2,10 h9.6 l2.5,-7 H7.5 M9,17 a1.5,1.5 0 1 0 0.01,0 z M17,17 a1.5,1.5 0 1 0 0.01,0 z M5,3 h3 l3.2,10 h9.6 l2.5,-7 H7.5 z",
 "bag": "M6,7 h12 l1,14 H5 z M9,7 a3,3 0 0 1 6,0 v2 h-1.5 V7 a1.5,1.5 0 0 0 -3,0 v2 H9 z",
 "pill": "M4.9,13.4 l8.5,-8.5 a4.2,4.2 0 0 1 6,6 l-8.5,8.5 a4.2,4.2 0 0 1 -6,-6 z M10.5,7.5 l6,6",
 "dumbbell": "M2,10 h2 V8 h2 v8 H4 v-2 H2 z M22,10 h-2 V8 h-2 v8 h2 v-2 h2 z M7,7 h2 v10 H7 z M15,7 h2 v10 h-2 z M9,11 h6 v2 H9 z",
 "plane": "M2.5,19 h19 v2 h-19 z M22,15.5 c-0.3,1 -1.3,1.6 -2.3,1.3 L3.5,12.2 l0.6,-2.3 l2.3,0.6 l1.4,-1.2 l-4.2,-4.3 l1.8,-0.5 l6.2,3.2 l5.2,-1.6 c1.1,-0.3 2.2,0.3 2.5,1.4 c0.3,1.1 -0.3,2.2 -1.4,2.5 L14,12 l1.6,3.4 z",
 "home": "M12,3 l9,8 h-2.5 v9 h-5 v-6 h-3 v6 h-5 v-9 H3 z",
 "note": "M5,3 h10 l4,4 v14 H5 z M8,10 h8 v1.6 H8 z M8,13.5 h8 v1.6 H8 z M8,17 h5 v1.6 H8 z",
 "bulb": "M12,2 C8,2 5,5 5,8.5 c0,2 1,3.5 2.5,4.5 L7,16 c0,1 0.5,1.5 1.5,1.5 h7 c1,0 1.5,-0.5 1.5,-1.5 l-0.5,-3 C18,12 19,10.5 19,8.5 C19,5 16,2 12,2 z M9,19 h6 v1.5 c0,0.8 -0.7,1.5 -1.5,1.5 h-3 C9.7,22 9,21.3 9,20.5 z",
 "coin": "M12,2 a10,10 0 1 0 0.01,0 z M12,5.5 a6.5,6.5 0 1 1 0,13 a6.5,6.5 0 0 1 0,-13 z M11,8 h2 v1 c1.2,0.2 2,1 2,2.2 h-2 c0,-0.4 -0.4,-0.7 -1,-0.7 s-1,0.3 -1,0.7 c0,0.5 0.5,0.7 1.3,0.9 c1.5,0.4 2.7,1 2.7,2.5 c0,1.3 -0.9,2.1 -2,2.3 V18 h-2 v-1.1 c-1.3,-0.2 -2,-1.1 -2,-2.3 h2 c0,0.5 0.5,0.8 1,0.8 s1,-0.3 1,-0.8 c0,-0.5 -0.5,-0.7 -1.3,-0.9 C10.2,13.3 9,12.7 9,11.2 c0,-1.2 0.8,-2 2,-2.2 z",
 "calendar": "M5,4 h14 a2,2 0 0 1 2,2 v14 a2,2 0 0 1 -2,2 H5 a2,2 0 0 1 -2,-2 V6 a2,2 0 0 1 2,-2 z M3,9 h18 v1.8 H3 z M7,2 h2 v4 H7 z M15,2 h2 v4 h-2 z M7,12 h3 v3 H7 z M11,12 h3 v3 h-3 z M15,12 h3 v3 h-3 z",
 "music": "M9,3 l11,-1.5 v13 a3,3 0 1 1 -2,-2.8 V5.2 L11,6.3 v10.2 a3,3 0 1 1 -2,-2.8 z",
 "paw": "M12,12 c3,0 6,2.5 6,5.5 c0,1.5 -1,2.5 -2.5,2.5 c-1.2,0 -2.2,-0.8 -3.5,-0.8 s-2.3,0.8 -3.5,0.8 C7,20 6,19 6,17.5 C6,14.5 9,12 12,12 z M6.5,7 a2,2.5 0 1 0 0.01,0 z M17.5,7 a2,2.5 0 1 0 0.01,0 z M3.5,11.5 a1.8,2.2 0 1 0 0.01,0 z M20.5,11.5 a1.8,2.2 0 1 0 0.01,0 z M10,4 a2,2.5 0 1 0 0.01,0 z M14,4 a2,2.5 0 1 0 0.01,0 z",
 "star": "M12,2 l2.4,4.9 l5.4,0.8 l-3.9,3.8 l0.9,5.4 L12,15.3 l-4.8,2.6 l0.9,-5.4 L4.2,8.7 l5.4,-0.8 z",
 "brain": "M9,3 a3.5,3.5 0 0 0 -3.5,3.5 A3,3 0 0 0 4,9.5 a3,3 0 0 0 1,5.5 a3.5,3.5 0 0 0 5,3.2 V3.4 A3.5,3.5 0 0 0 9,3 z M15,3 a3.5,3.5 0 0 1 3.5,3.5 A3,3 0 0 1 20,9.5 a3,3 0 0 1 -1,5.5 a3.5,3.5 0 0 1 -5,3.2 V3.4 A3.5,3.5 0 0 1 15,3 z",
 "shield": "M12,2 l8,3 v6 c0,5 -3.5,9 -8,11 c-4.5,-2 -8,-6 -8,-11 V5 z M10.5,15 l-3,-3 l1.4,-1.4 l1.6,1.6 l4.6,-4.6 L16.5,9 z",
 "sun": "M12,6 a6,6 0 1 0 0.01,0 z M11,1 h2 v3 h-2 z M11,20 h2 v3 h-2 z M1,11 h3 v2 H1 z M20,11 h3 v2 h-3 z M4,4 l1.4,-1.4 l2.1,2.1 L6.1,6.1 z M16.5,16.5 l1.4,-1.4 l2.1,2.1 l-1.4,1.4 z M4,20 l2.1,-2.1 l1.4,1.4 L5.4,21.4 z M16.5,7.5 l2.1,-2.1 L20,6.8 l-2.1,2.1 z",
 "moon": "M14,2 a10,10 0 1 0 8,15.5 A8,8 0 0 1 14,2 z",
 "car": "M5,11 l1.5,-4.5 A2,2 0 0 1 8.4,5 h7.2 a2,2 0 0 1 1.9,1.5 L19,11 h1 a1,1 0 0 1 1,1 v5 h-2 v2 h-3 v-2 H8 v2 H5 v-2 H3 v-5 a1,1 0 0 1 1,-1 z M7.5,7 l-1,3.5 h11 l-1,-3.5 z M6.5,13 a1.5,1.5 0 1 0 0.01,0 z M17.5,13 a1.5,1.5 0 1 0 0.01,0 z",
 "cup": "M4,4 h13 v9 a5,5 0 0 1 -5,5 H9 a5,5 0 0 1 -5,-5 z M17,6 h2 a3,3 0 0 1 0,6 h-2 v-2 h2 a1,1 0 0 0 0,-2 h-2 z M5,20 h11 v2 H5 z",
 "wrench": "M21,6.5 a5,5 0 0 1 -6.6,4.7 L6,19.6 a2,2 0 0 1 -2.8,-2.8 l8.4,-8.4 A5,5 0 0 1 17.5,2 l-3,3 l0.5,2 l2,0.5 l3,-3 c0.6,0.6 1,1.2 1,2 z",
 "baby": "M12,2 a5,5 0 1 0 0.01,0 z M12,4.5 a2.5,2.5 0 1 1 0,5 a2.5,2.5 0 0 1 0,-5 z M12,12 c4,0 7,2.5 7,5.5 V22 H5 v-4.5 C5,14.5 8,12 12,12 z",
 "gift": "M3,9 h18 v3 H3 z M4,12 h16 v10 H4 z M11,9 h2 v13 h-2 z M12,9 c-2,-3 -6,-4 -6,-1.5 C6,9 8,9 12,9 z M12,9 c2,-3 6,-4 6,-1.5 C18,9 16,9 12,9 z",
 "mic": "M12,2 a3,3 0 0 1 3,3 v7 a3,3 0 0 1 -6,0 V5 a3,3 0 0 1 3,-3 z M6,10 h2 a4,4 0 0 0 8,0 h2 a6,6 0 0 1 -5,5.9 V19 h3 v2 H8 v-2 h3 v-3.1 A6,6 0 0 1 6,10 z",
 "graph": "M3,20 h18 v1.5 H3 z M4,17 l5,-5 l4,3 l7,-8 l1.3,1.3 L13,17.5 l-4,-3 l-3.7,3.7 z",
 "globe": "M12,2 a10,10 0 1 0 0.01,0 z M12,4 a8,8 0 0 1 0,16 a8,8 0 0 1 0,-16 z M4,11 h16 v2 H4 z M11,4 h2 v16 h-2 z M12,4 c3,3 3,13 0,16 c-3,-3 -3,-13 0,-16 z",
 "scale": "M12,2 l1,3 h6 v2 h-1.5 l3,7 a3.5,3.5 0 0 1 -7,0 l3,-7 H13 v11 h4 v2 H7 v-2 h4 V7 H9.5 l3,7 a3.5,3.5 0 0 1 -7,0 l3,-7 H5 V5 h6 z",
 "hat": "M12,3 l10,5 l-10,5 L2,8 z M6,10.5 v4 c0,2 3,3.5 6,3.5 s6,-1.5 6,-3.5 v-4 l-6,3 z M20,9 h1.5 v6 H20 z",
 "tooth": "M8,2 c2,0 3,1 4,1 s2,-1 4,-1 c3,0 4,2.5 4,5 c0,3 -2,4 -2,7 c0,3 -1,8 -2.5,8 c-1.5,0 -1.5,-6 -3.5,-6 s-2,6 -3.5,6 C7,22 6,17 6,14 C6,11 4,10 4,7 C4,4.5 5,2 8,2 z",
 "ring": "M12,7 a7,7 0 1 0 0.01,0 z M12,10 a4,4 0 1 1 0,8 a4,4 0 0 1 0,-8 z M9,2 h6 l2,4 H7 z",
 "clock": "M12,2 a10,10 0 1 0 0.01,0 z M12,4 a8,8 0 0 1 0,16 a8,8 0 0 1 0,-16 z M11,7 h2 v5.5 l3.5,2 l-1,1.7 L11,13.5 z",
}

def hx(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) / 255 for i in (0, 2, 4))
def tone(h, l, s=None):
    hh, ll, ss = colorsys.rgb_to_hls(*hx(h)); r, g, b = colorsys.hls_to_rgb(hh, l, ss if s is None else s)
    return "#%02X%02X%02X" % tuple(max(0, min(255, round(c * 255))) for c in (r, g, b))

def flavor_name(sid): return "f" + re.sub(r"[^a-z0-9]", "", sid.lower())

specs = []
for p in sorted(glob.glob(os.path.join(ROOT, "specs", "*.json"))):
    s = json.load(open(p)); s["_file"] = p; specs.append(s)
    assert re.fullmatch(r"[a-z][a-z0-9_]*", s["id"]), s["id"]
    assert len(s["tools"]) >= 3, s["id"]

lines = []
for s in specs:
    fn = flavor_name(s["id"])
    d = os.path.join(ROOT, "app", "src", fn)
    os.makedirs(f"{d}/assets", exist_ok=True); os.makedirs(f"{d}/res/values", exist_ok=True); os.makedirs(f"{d}/res/drawable", exist_ok=True)
    json.dump({k: v for k, v in s.items() if not k.startswith("_")}, open(f"{d}/assets/app.json", "w"), ensure_ascii=False, indent=1)
    name = s["name"].replace("&", "&amp;").replace("'", "\\'")
    open(f"{d}/res/values/strings.xml", "w").write(f'<resources><string name="app_name">{name}</string></resources>\n')
    open(f"{d}/res/values/colors.xml", "w").write(f'<resources><color name="ic_launcher_background">{tone(s["colors"][0], .30)}</color></resources>\n')
    glyph = GLYPHS.get(s.get("icon", "spark"), GLYPHS["spark"])
    open(f"{d}/res/drawable/ic_launcher_foreground.xml", "w").write(f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="2.1" android:scaleY="2.1" android:translateX="28.8" android:translateY="28.8">
        <path android:fillColor="{tone(s["colors"][1] if len(s["colors"]) > 1 else s["colors"][0], .85)}" android:pathData="{glyph}" />
    </group>
</vector>
''')
    if not ONLY or fn in ONLY:
        lines.append(f'        create("{fn}") {{ dimension = "app"; applicationId = "com.mohithash.byok.{s["id"].replace("_", "")}" }}')

bg = open(os.path.join(ROOT, "app", "build.gradle.kts")).read()
bg = re.sub(r"(// FLAVORS-BEGIN.*?\n).*?(\s*// FLAVORS-END)", lambda m: m.group(1) + "\n".join(lines) + "\n" + m.group(2).lstrip("\n"), bg, flags=re.S)
open(os.path.join(ROOT, "app", "build.gradle.kts"), "w").write(bg)
print(f"{len(specs)} specs, {len(lines)} flavors in build file")
