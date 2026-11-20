"""Build the standalone GitHub Pages version of the Hearby preview.

The preview in docs/hearby/preview/index.html is written for the Claude artifact
viewer, which adds the <!doctype>, <head> and base styles itself. GitHub Pages
serves files as they are, so this script wraps the preview in a complete page
and writes it to docs/index.html (the site's home page).

Run it from the repository root after changing the preview:
    python3 docs/hearby/build_github_page.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs" / "hearby" / "preview" / "index.html"
OUT = ROOT / "docs" / "index.html"

BASE = """<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="description" content="Hearby: a home-screen widget and app for long-distance couples. Miles apart, a heartbeat away.">
<style>
:root { padding-top: env(safe-area-inset-top, 0px); padding-bottom: env(safe-area-inset-bottom, 0px); }
body { margin: 0; font: 14px system-ui, -apple-system, "Segoe UI", sans-serif; }
img { max-width: 100%; }
[hidden] { display: none !important; }
</style>
"""


def main() -> None:
    src = SRC.read_text(encoding="utf-8")
    cut = src.index("</style>") + len("</style>")
    head, body = src[:cut], src[cut:]
    page = (
        "<!doctype html>\n<html lang=\"en\">\n<head>\n"
        + BASE
        + head.strip()
        + "\n</head>\n<body>\n"
        + body.strip()
        + "\n</body>\n</html>\n"
    )
    OUT.write_text(page, encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)} ({len(page):,} bytes)")


if __name__ == "__main__":
    main()
