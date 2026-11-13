#!/usr/bin/env python3
"""Copy the approved badge's exact contours into the wallet avatar's luminance mask."""
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "brand/sun-disc/winged-sun-badge.svg"
# Approved SORA-disc badge (radius 64, no cobras, original wings): fearless-wallet-web 1275e1a6.
SHA = "ff65329596da5e829c1b704fb8bb89d59ae8065fe6f72d68ba30e368f3b00e89"
source = SOURCE.read_bytes()
if hashlib.sha256(source).hexdigest() != SHA:
    raise ValueError("Approved badge master changed")
paths = [(p.attrib.get("id", "circle"), p.attrib["d"]) for p in ET.fromstring(source).iter() if p.tag.endswith("path")]
if [name for name, _ in paths] != ["circle", "badge-wings", "badge-disc-keyline", "badge-disc"]:
    raise ValueError("Expected the approved circle, wings, disc keyline and disc")
names = {"circle": "WINGED_SUN_CIRCLE_PATH", "badge-wings": "WINGED_SUN_WINGS_PATH",
         "badge-disc-keyline": "WINGED_SUN_KEYLINE_PATH", "badge-disc": "WINGED_SUN_DISC_PATH"}
output = ROOT / "runtime-utils/src/main/java/jp/co/soramitsu/fearless_utils/icon/WingedSunClip.kt"
output.write_text('package jp.co.soramitsu.fearless_utils.icon\n\n'
    '// Generated from the pinned approved badge; run scripts/generate-avatar-brand.py.\n'
    '// Painted as a luminance mask in badge order (circle white, wings black, keyline white, disc black),\n'
    '// so the mark stays negative space exactly as the badge draws it; no contour is altered.\n'
    + "".join('internal const val ' + names[name] + ' = "' + data + '"\n' for name, data in paths))
print("Generated approved avatar mask:", sum(len(data) for _, data in paths), "characters")
