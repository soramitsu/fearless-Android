#!/usr/bin/env python3
"""Extract the exact approved badge contours for the existing negative-space avatar."""
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "brand/sun-disc/winged-sun-badge.svg"
SHA = "c46079a2baf438c13b6782c176f173aab70418a80bc2457c56ad8b797990bb8e"
source = SOURCE.read_bytes()
if hashlib.sha256(source).hexdigest() != SHA:
    raise ValueError("Approved badge master changed")
paths = [p.attrib["d"] for p in ET.fromstring(source).iter() if p.tag.endswith("path")]
if len(paths) != 2:
    raise ValueError("Expected the approved circle and compound foreground")
data = " ".join(paths)
output = ROOT / "runtime-utils/src/main/java/jp/co/soramitsu/fearless_utils/icon/WingedSunClip.kt"
output.write_text('package jp.co.soramitsu.fearless_utils.icon\n\n'
    '// Generated from the pinned approved badge; run scripts/generate-avatar-brand.py.\n'
    '// Both contours stay in one even-odd clip so the mark remains negative space.\n'
    + 'internal const val WINGED_SUN_CLIP_PATH = "' + data + '"\n')
print("Generated approved compound avatar clip:", len(data), "characters")
