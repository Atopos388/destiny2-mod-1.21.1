"""Build the safe Source Han Sans subset used by the Director UI.

Run after adding visible UI copy. The scanner collects characters from runtime
source/resource text, so newly introduced glyphs become part of the next JAR.
Install fontTools into build/fonttools first; that directory is disposable.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCAL_FONTTOOLS = ROOT / "build" / "fonttools"
if LOCAL_FONTTOOLS.exists():
    sys.path.insert(0, str(LOCAL_FONTTOOLS))

from fontTools import subset  # type: ignore  # installed only for this asset task

SOURCE = ROOT / "src/main/resources/assets/destiny2-mod/font/source_han_sans_cn_semibold.ttf"
OUTPUT = ROOT / "src/main/resources/assets/destiny2-mod/font/source_han_sans_cn_semibold_subset.ttf"
TEXT_FILE = ROOT / "build/director_font_chars.txt"
SCAN_ROOTS = (ROOT / "src",)
TEXT_SUFFIXES = {".kt", ".java", ".json", ".mcmeta", ".properties", ".xml"}


def wanted(character: str) -> bool:
    code = ord(character)
    return (
        0x20 <= code <= 0x7E
        or 0x3000 <= code <= 0x303F
        or 0x3400 <= code <= 0x4DBF
        or 0x4E00 <= code <= 0x9FFF
        or 0xFF00 <= code <= 0xFFEF
    )


def collect_characters() -> str:
    characters = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 +-*/%:;,.!?()[]{}<>_#@&'\"|\\~`◆◇×//")
    # Frequent Director copy remains available even while a new source file is
    # temporarily incomplete during development.
    characters.update("装备武器护甲物品栏收藏品旅程火力战队光等能量伤害射程稳定性操控性装填速度弹匣精准特性模组催化剂返回替换详情职业生命手雷超能近战空槽位")
    for scan_root in SCAN_ROOTS:
        for path in scan_root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            characters.update(character for character in text if wanted(character))
    return "".join(sorted(characters, key=ord))


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Missing source font: {SOURCE}")
    TEXT_FILE.parent.mkdir(parents=True, exist_ok=True)
    text = collect_characters()
    TEXT_FILE.write_text(text, encoding="utf-8")
    subset.main(
        [
            str(SOURCE),
            f"--output-file={OUTPUT}",
            f"--text-file={TEXT_FILE}",
            "--layout-features=*",
            "--name-IDs=*",
            "--name-languages=*",
            "--name-legacy",
            "--notdef-glyph",
            "--notdef-outline",
            "--recommended-glyphs",
        ]
    )
    print(f"Director font subset: {len(text)} characters, {OUTPUT.stat().st_size} bytes")


if __name__ == "__main__":
    main()
