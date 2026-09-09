#!/usr/bin/env python3
"""Regenerate the Web Tools launcher mark: a page outline with three lines, a shelf of pages.

Emits the adaptive-icon vector foreground, the legacy PNG mipmaps, and docs/icon.png (192px,
opaque, the file BrightMarket reads). White on black, 108 canvas, 18..90 safe zone, same as the
rest of the Bright* set. Run from the repo root:

    python3 scripts/generate_icon.py
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
DOCS = ROOT / "docs"

# Everything is rectangles in the 108 viewport so the vector and the raster agree exactly.
# A page: outline (four bars), a title bar rule, and two short text lines.
STROKE = 5
PAGE = (26, 22, 82, 86)  # x0, y0, x1, y1
BARS = []
x0, y0, x1, y1 = PAGE
BARS += [(x0, y0, x1, y0 + STROKE), (x0, y1 - STROKE, x1, y1), (x0, y0, x0 + STROKE, y1), (x1 - STROKE, y0, x1, y1)]
BARS += [(x0, y0 + 15, x1, y0 + 15 + STROKE)]            # the address-bar rule
BARS += [(x0 + 12, 52, x0 + 44, 52 + STROKE), (x0 + 12, 64, x0 + 32, 64 + STROKE)]  # two lines of text

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def write_vector() -> None:
    paths = "\n".join(
        f'    <path android:fillColor="#FFFFFF" android:pathData="M{a},{b} H{c} V{d} H{a} Z" />'
        for (a, b, c, d) in BARS
    )
    (RES / "drawable/ic_launcher_foreground.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!--\n"
        "  Web Tools launcher mark: a page outline with a rule and two lines. One of the unified\n"
        "  Bright* set: 108 canvas, 18..90 safe zone, white on black, no colour.\n"
        "-->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        f"{paths}\n"
        "</vector>\n"
    )


def draw(size: int, rnd: bool, opaque: bool) -> Image.Image:
    k = size / 108.0
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255 if opaque else 0))
    d = ImageDraw.Draw(img)
    if not opaque:
        if rnd:
            d.ellipse([0, 0, size - 1, size - 1], fill=(0, 0, 0, 255))
        else:
            d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.14), fill=(0, 0, 0, 255))
    for (a, b, c, e) in BARS:
        d.rectangle([a * k, b * k, c * k - 1, e * k - 1], fill=(255, 255, 255, 255))
    return img


def write_pngs() -> None:
    for dens, size in DENSITIES.items():
        (RES / f"mipmap-{dens}").mkdir(parents=True, exist_ok=True)
        draw(size, False, False).save(RES / f"mipmap-{dens}/ic_launcher.png")
        draw(size, True, False).save(RES / f"mipmap-{dens}/ic_launcher_round.png")
    DOCS.mkdir(exist_ok=True)
    draw(192, False, True).convert("L").save(DOCS / "icon.png")


if __name__ == "__main__":
    write_vector()
    write_pngs()
    print("Web Tools icon regenerated")
