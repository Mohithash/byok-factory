import json, os
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "specs")

def F(key, label, type="text", placeholder="", options=None, default="", required=False, multi=False):
    d = {"key": key, "label": label, "type": type}
    if placeholder: d["placeholder"] = placeholder
    if options: d["options"] = options
    if default: d["default"] = default
    if required: d["required"] = True
    if multi: d["multi"] = True
    return d

def T(id, title, sub, emoji, inputs, prompt, shape="", button="Generate", loading="Working…"):
    return {"id": id, "title": title, "subtitle": sub, "emoji": emoji, "inputs": inputs, "prompt": prompt, "shape": shape, "button": button, "loading": loading}

def APP(id, name, tagline, category, colors, icon, about, persona, ob_title, ob_sub, bullets, profile, tools, disclaimer=""):
    spec = {"id": id, "name": name, "tagline": tagline, "category": category, "colors": colors, "icon": icon, "about": about, "persona": persona,
            "onboarding": {"title": ob_title, "subtitle": ob_sub, "bullets": bullets}, "profile": profile, "tools": tools}
    if disclaimer: spec["disclaimer"] = disclaimer
    os.makedirs(OUT, exist_ok=True)
    json.dump(spec, open(os.path.join(OUT, id + ".json"), "w"), ensure_ascii=False, indent=1)
    return spec

NAME = F("name", "Name (optional)")
PHOTO = lambda label, ph="", req=False: F("photo", label, "photo", ph, required=req)
LONG = lambda key, label, ph="", req=True: F(key, label, "longtext", ph, required=req)
TXT = lambda key, label, ph="", req=False: F(key, label, "text", ph, required=req)
CH = lambda key, label, opts, default="", multi=False: F(key, label, "chips", options=opts, default=default, multi=multi)
NUM = lambda key, label, ph="": F(key, label, "number", ph)
