"""Build the Hearby app screen from the design preview.

The Android app shows docs/hearby/preview/index.html inside a WebView, in "real
phone mode" (the page checks for window.HearbyNative). This script:
  * wraps the preview in a full page sized for a phone,
  * swaps the Google Fonts link for the bundled fonts in assets/fonts,
  * turns the phone's fixed pink colours into theme variables, so the app can
    be light blue or light pink, each in a light and a dark version,
  * adds the full-screen layout used on a real phone,
and writes app/src/main/assets/hearby.html.

Run it after changing the preview, before building the APK:
    python3 android/build_web_asset.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs" / "hearby" / "preview" / "index.html"
OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "hearby.html"

# Fixed colours inside the phone design -> theme variables.
COLORS = {
    "--k-ink": ["#33263f", "#4a3858", "#4b3a59", "#23182c", "#3a2c47"],
    "--k-muted": ["#6e5d7e", "#6b5a7b", "#4e3f5c", "#7b6b89", "#8a7b98", "#8a7898", "#7a6a88", "#6f5f7e", "#3e3049", "#6a4e7c", "#b0a0bc"],
    "--k-accent": ["#db4f84", "#e2588a"],
    "--k-accent2": ["#c43f76"],
    "--k-deep": ["#b03a6a", "#9a2f5e", "#9a3e68", "#8a2f58"],
    "--k-bg1": ["#ffd9e8"], "--k-bg2": ["#d9d2ff"], "--k-bg3": ["#ffe6cf"],
    "--k-bg4": ["#f9e3f0", "#fbe7f1"], "--k-bg5": ["#e5defb", "#e8e1fb"], "--k-bg6": ["#fbe9dc", "#fbeadf"],
}
RGBA = {
    "rgba(var(--k-glass),": [r"rgba\(255,\s*255,\s*255,"],
    "rgba(var(--k-tint),": [r"rgba\(255,\s*205,\s*225,", r"rgba\(255,\s*210,\s*228,", r"rgba\(255,\s*190,\s*215,", r"rgba\(219,\s*79,\s*132,"],
}

THEMES = {
    ("pink", "light"): dict(ink="#33263f", muted="#6e5d7e", accent="#db4f84", accent2="#c43f76", deep="#b03a6a", glass="255, 255, 255", tint="255, 205, 225",
                            bg=["#ffd9e8", "#d9d2ff", "#ffe6cf", "#f9e3f0", "#e5defb", "#fbe9dc"]),
    ("blue", "light"): dict(ink="#22324a", muted="#5b6d85", accent="#3f86d6", accent2="#2f74c4", deep="#2a64a8", glass="255, 255, 255", tint="200, 225, 255",
                            bg=["#d6ebff", "#d4dcff", "#e0f4ff", "#e4f0fc", "#dbe6fb", "#e9f3fb"]),
    ("pink", "dark"): dict(ink="#f6e9f1", muted="#c9b1c2", accent="#f07aa8", accent2="#ff8fbd", deep="#ffa3c8", glass="58, 34, 52", tint="120, 50, 85",
                           bg=["#3a1830", "#2a1f45", "#3a2420", "#21131d", "#1d1630", "#261a1c"]),
    ("blue", "dark"): dict(ink="#e6f0fb", muted="#a6b8cd", accent="#6aaaf0", accent2="#82bcff", deep="#9ccaff", glass="28, 44, 68", tint="40, 80, 140",
                           bg=["#13294a", "#1f2350", "#102a33", "#0e1726", "#141b33", "#0f1c26"]),
}


def theme_css() -> str:
    out = []
    for (hue, mode), t in THEMES.items():
        v = [f"--k-ink: {t['ink']}", f"--k-muted: {t['muted']}", f"--k-accent: {t['accent']}", f"--k-accent2: {t['accent2']}",
             f"--k-deep: {t['deep']}", f"--k-glass: {t['glass']}", f"--k-tint: {t['tint']}"]
        v += [f"--k-bg{i + 1}: {c}" for i, c in enumerate(t["bg"])]
        # The page-level tokens are used by the settings panel.
        v += [f"--bg: {t['bg'][3]}", f"--fg: {t['ink']}", f"--muted: {t['muted']}", f"--accent: {t['accent']}",
              f"--card: rgba({t['glass']}, .7)", f"--line: rgba({t['glass']}, .95)", f"color-scheme: {mode}"]
        out.append(f'html.native[data-hue="{hue}"][data-theme="{mode}"] {{ {"; ".join(v)}; }}')
    return "\n".join(out)


NATIVE_CSS = """
@font-face { font-family: "Fredoka"; src: url(fonts/fredoka.woff2) format("woff2"); font-weight: 300 700; font-display: swap; }
@font-face { font-family: "Nunito"; src: url(fonts/nunito.woff2) format("woff2"); font-weight: 200 1000; font-display: swap; }
html.native, html.native body { margin: 0; padding: 0; height: 100%; overflow: hidden; background: var(--k-bg4); -webkit-tap-highlight-color: transparent; }
html.native .top, html.native .tabs, html.native .notes, html.native .device-label { display: none !important; }
html.native .stage { display: block; }
html.native .device[data-id="leo"] { width: 100%; max-width: none; display: block; }
html.native .device[data-id="mia"] { display: none !important; }
html.native .phone { width: 100vw; max-width: none; height: 100vh; height: 100dvh; border-radius: 0; padding: 0; background: none; box-shadow: none; animation: none !important; }
html.native .phone .ring, html.native .power { display: none; }
html.native .screen { border-radius: 0; color: var(--k-ink); }
html.native .statusbar, html.native .home, html.native .apps, html.native .dock, html.native .navbar, html.native .off-layer { display: none !important; }
html.native .happ { border-radius: 0; }
html.native .app-top { padding-top: 12px; }
html.native .banner { top: 8px; }
html.native .toast { background: var(--k-accent2); }
html.native .widget.open { top: 12px; height: min(calc(100% - 96px), 132vw); }
html.native .scrim { background: rgba(var(--k-tint), .25); }
html.native .scrim.on::after { content: "Swipe the scene to travel · hold it to touch together\\A Tap here to go back to the app"; white-space: pre; position: absolute; left: 0; right: 0; bottom: 26px; text-align: center; font-size: 10.5px; line-height: 1.6; font-weight: 800; color: var(--k-muted); }
html.native .controls { display: none; }
html.native .controls.native-open { display: grid; position: fixed; inset: 0; z-index: 60; overflow-y: auto; width: auto; background: var(--k-bg4); color: var(--k-ink); padding: 14px 14px 40px; align-content: start; gap: 10px; font-size: 13px; }
html.native .controls label[for="screen-leo"], html.native .controls label[for="slides-leo"], html.native .controls .try-nudge, html.native .controls .buzz { display: none; }
html.native .controls .ctl-done { justify-self: end; }
html.native .controls input[type="text"], html.native .controls select { background: rgba(var(--k-glass), .8); color: var(--k-ink); border: 1px solid rgba(var(--k-glass), 1); border-radius: 8px; }
html.native .native-card .n-btns { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
html.native .native-card .n-btns button { padding: 7px 6px; }
html.native[data-theme="dark"] .card, html.native[data-theme="dark"] .sheet { border-color: rgba(255, 255, 255, .12); }
/* Live "frosted glass" blur is slow in phone WebViews; the smooth backgrounds look the same without it. */
html.native *, html.native *::before, html.native *::after { backdrop-filter: none !important; -webkit-backdrop-filter: none !important; }
html.native .card { background: rgba(var(--k-glass), .5); }
html.native .app-nav { background: rgba(var(--k-glass), .72); }
html.native .sheet { background: rgba(var(--k-glass), .92); }
html.native .banner { background: rgba(var(--k-glass), .88); }
html.native .who, html.native .chip, html.native .close-w { background: rgba(var(--k-glass), .62); }
html.native .bubble { background: rgba(var(--k-glass), .66); }
html.native .actions button { background: rgba(var(--k-glass), .5); }
html.native .actions .felt { background: rgba(var(--k-tint), .62); }
html.native .pill { background: rgba(var(--k-glass), .6); }
html.native .pop { background: rgba(46, 22, 64, .55); }
html.native .dua-row > span, html.native .bk-row > span { flex: 1 1 0; min-width: 0; }
/* The widget pop-up: only the expanded widget, over the blurred home screen. */
html.native.widget-mode, html.native.widget-mode body, html.native.widget-mode .screen, html.native.widget-mode .phone { background: transparent !important; }
html.native.widget-mode .scrim { background: transparent; }
html.native.widget-mode .scrim.on::after { content: "Tap outside to close · tap the name to open Hearby"; bottom: 18px; color: #fff; text-shadow: 0 1px 4px rgba(0, 0, 0, .5); }
html.native.widget-mode .widget.open { height: min(calc(100% - 64px), 112vw); transition: none; }
html.native.widget-mode .happ, html.native.widget-mode .sleep-layer { display: none !important; }
html.native[data-theme="dark"] .scene-label { background: rgba(var(--k-glass), .95); color: var(--k-deep); }
"""

HEAD = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=280, user-scalable=no">
"""


def main() -> None:
    src = SRC.read_text(encoding="utf-8")
    src = re.sub(r'<link rel="preconnect"[^>]*>\s*', "", src)
    src = re.sub(r'<link rel="stylesheet" href="https://fonts\.googleapis\.com[^>]*>\s*', "", src)
    start = src.index("<style>") + len("<style>")
    end = src.index("</style>")
    css = src[start:end]
    for var, hexes in COLORS.items():
        for h in hexes:
            css = re.sub(re.escape(h) + r"(?![0-9a-fA-F])", f"var({var})", css, flags=re.IGNORECASE)
    for rep, pats in RGBA.items():
        for pat in pats:
            css = re.sub(pat, rep, css)
    css += "\n/* ---------- Real phone mode ---------- */\n" + theme_css() + NATIVE_CSS
    head = src[:start - len("<style>")]
    body = src[end + len("</style>"):]
    page = HEAD + head.strip() + "\n<style>" + css + "</style>\n</head>\n<body>\n" + body.strip() + "\n</body>\n</html>\n"
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(page, encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)} ({len(page):,} bytes)")


if __name__ == "__main__":
    main()
