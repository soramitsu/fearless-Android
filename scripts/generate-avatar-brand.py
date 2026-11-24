#!/usr/bin/env python3
"""Copy the Yatagarasu crest badge's exact crest contour into the wallet avatar's luminance mask."""
import hashlib
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "brand/yatagarasu/svg/crest-badge-small.svg"
# The kit's small crest badge (avatars render below 64 dp); pinned in scripts/generate-brand-assets.py too.
SHA = "cc2bce14632d0d24eedcb578e47fadc4852478a01b67fa0d81c2d99b37bad004"
source = SOURCE.read_bytes()
if hashlib.sha256(source).hexdigest() != SHA:
    raise ValueError("Pinned crest badge changed")
svg = ET.fromstring(source)
namespace = "{http://www.w3.org/2000/svg}"
circle = svg.find(namespace + "circle")
if circle is None or (circle.get("cx"), circle.get("cy"), circle.get("r")) != ("256", "256", "256"):
    raise ValueError("Expected the badge's 512-unit circle")
groups = [g for g in svg.iter(namespace + "g") if g.get("transform")]
paths = list(svg.iter(namespace + "path"))
if len(groups) != 1 or len(paths) != 1 or paths[0].get("fill-rule") != "evenodd":
    raise ValueError("Expected one placed crest contour")
transform = groups[0].get("transform")
if not re.fullmatch(r"translate\([\d. ]+\) scale\([\d.]+\)", transform):
    raise ValueError("Unexpected crest placement " + transform)
output = ROOT / "runtime-utils/src/main/java/jp/co/soramitsu/fearless_utils/icon/CrestClip.kt"
output.write_text('package jp.co.soramitsu.fearless_utils.icon\n\n'
    '// Generated from the pinned Yatagarasu crest badge; run scripts/generate-avatar-brand.py.\n'
    '// Painted as a luminance mask (the badge circle white, the crest black at its badge placement), so the\n'
    '// crest stays negative space exactly as the badge draws it; no contour is altered.\n'
    'internal const val CREST_TRANSFORM = "' + transform + '"\n'
    'internal const val CREST_PATH = "' + paths[0].get("d") + '"\n')
print("Generated the crest avatar mask:", len(paths[0].get("d")), "characters")
