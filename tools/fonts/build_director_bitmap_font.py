"""Build a Minecraft bitmap font from Source Han Sans CN Semibold.

Minecraft's TrueType provider is unstable with the current client FreeType
runtime. This generator keeps the intended Source Han appearance while making
the game load PNG glyph atlases instead of the TTF at runtime.
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCAL_TOOLS = ROOT / "build" / "fonttools"
if LOCAL_TOOLS.exists():
    sys.path.insert(0, str(LOCAL_TOOLS))

from fontTools.ttLib import TTFont  # type: ignore
from PIL import Image, ImageDraw, ImageFont  # type: ignore

FONT_ROOT = ROOT / "src/main/resources/assets/destiny2-mod/font"
TEXTURE_ROOT = ROOT / "src/main/resources/assets/destiny2-mod/textures/font"
SOURCE = FONT_ROOT / "source_han_sans_cn_semibold.ttf"
OUTPUT_DEFINITION = FONT_ROOT / "source_han_sans_cn_semibold_bitmap.json"
TEXT_FILE = ROOT / "build/director_font_chars.txt"
SCAN_ROOTS = (ROOT / "src",)
TEXT_SUFFIXES = {".kt", ".java", ".json", ".mcmeta", ".properties", ".xml"}

FONT_SIZE = 18
CELL_SIZE = 20
ATLAS_COLUMNS = 32
ATLAS_GLYPHS = 1024
BASELINE = 16
RUNTIME_HEIGHT = 10
RUNTIME_ASCENT = 9


def collect_characters(cmap: set[int]) -> list[str]:
    characters = set(
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 "
        "+-*/%:;,.!?()[]{}<>_#@&'\"|\\~`"
        "，。！？：；（）【】《》“”‘’·—…"
        "◆◇◈×←→↑↓△▽○●□■"
    )
    for scan_root in SCAN_ROOTS:
        for path in scan_root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            characters.update(
                character
                for character in text
                if character.isprintable() and not character.isspace()
            )
    return sorted(
        (character for character in characters if ord(character) in cmap),
        key=ord,
    )


def render_atlas(characters: list[str], atlas_index: int) -> dict[str, object]:
    rows = math.ceil(len(characters) / ATLAS_COLUMNS)
    padded = characters + ["\0"] * (rows * ATLAS_COLUMNS - len(characters))
    image = Image.new(
        "RGBA",
        (ATLAS_COLUMNS * CELL_SIZE, rows * CELL_SIZE),
        (0, 0, 0, 0),
    )
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(str(SOURCE), FONT_SIZE)

    for index, character in enumerate(padded):
        if character == "\0":
            continue
        column = index % ATLAS_COLUMNS
        row = index // ATLAS_COLUMNS
        # Keep glyphs left-aligned inside each cell. Minecraft derives the
        # runtime advance from the right-most visible pixel, which preserves
        # Source Han's proportional Latin and punctuation widths.
        x = column * CELL_SIZE + 1
        y = row * CELL_SIZE + BASELINE
        draw.text((x, y), character, font=font, fill=(255, 255, 255, 255), anchor="ls")

    filename = f"source_han_sans_cn_semibold_bitmap_{atlas_index}.png"
    TEXTURE_ROOT.mkdir(parents=True, exist_ok=True)
    image.save(TEXTURE_ROOT / filename, optimize=True)
    return {
        "type": "bitmap",
        "file": f"destiny2-mod:font/{filename}",
        "ascent": RUNTIME_ASCENT,
        "height": RUNTIME_HEIGHT,
        "chars": [
            "".join(padded[row * ATLAS_COLUMNS : (row + 1) * ATLAS_COLUMNS])
            for row in range(rows)
        ],
    }


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Missing source font: {SOURCE}")

    source_font = TTFont(SOURCE, lazy=True)
    try:
        cmap = set((source_font.getBestCmap() or {}).keys())
    finally:
        source_font.close()

    characters = collect_characters(cmap)
    if not characters:
        raise SystemExit("No supported Director glyphs were discovered")

    for stale in TEXTURE_ROOT.glob("source_han_sans_cn_semibold_bitmap_*.png"):
        stale.unlink()

    providers = []
    for atlas_index, start in enumerate(range(0, len(characters), ATLAS_GLYPHS)):
        providers.append(render_atlas(characters[start : start + ATLAS_GLYPHS], atlas_index))

    OUTPUT_DEFINITION.write_text(
        json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    TEXT_FILE.parent.mkdir(parents=True, exist_ok=True)
    TEXT_FILE.write_text("".join(characters), encoding="utf-8")
    print(
        f"Director bitmap font: {len(characters)} glyphs, "
        f"{len(providers)} atlas file(s)"
    )


if __name__ == "__main__":
    main()
