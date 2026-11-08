#!/usr/bin/env python3
"""Regress the AAPT path overflow without changing any approved contour or hole."""
import hashlib
import importlib.util
from decimal import Decimal
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("brand_assets", ROOT / "scripts/generate-brand-assets.py")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


def vertices(path):
    # Independent SVG token stream reader, including implicit repeated commands.
    tokens = re.findall(r"[A-Za-z]|[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?", path)
    result, position, origin = [], (Decimal(0), Decimal(0)), None
    index, command = 0, None
    while index < len(tokens):
        if tokens[index].isalpha():
            command = tokens[index]
            index += 1
        if command in ("Z", "z"):
            result.append(("close", origin))
            position = origin
            command = None
            continue
        count = {"M": 2, "m": 2, "L": 2, "l": 2, "H": 1, "h": 1, "V": 1, "v": 1}[command]
        numbers = [Decimal(n) for n in tokens[index:index + count]]
        index += count
        x, y = position
        if command in ("M", "L"):
            position = tuple(numbers)
        elif command in ("m", "l"):
            position = (x + numbers[0], y + numbers[1])
        elif command == "H":
            position = (numbers[0], y)
        elif command == "h":
            position = (x + numbers[0], y)
        elif command == "V":
            position = (x, numbers[0])
        else:
            position = (x, y + numbers[0])
        move = command in ("M", "m")
        result.append(("move" if move else "line", position))
        if move:
            origin = position
            command = "l" if command == "m" else "L"
    return result


ANDROID = "{http://schemas.android.com/apk/res/android}"
APPROVED = {
    # fearless-wallet-web 1275e1a6 src/assets/brand: SORA disc radius 64, no cobras, original wings.
    "winged-sun-badge-lines.svg": "9756447ca43921b8098a52cd860e08dc78910bf23fcfe0e04ec1be036853d4d8",
    "winged-sun-badge.svg": "ff65329596da5e829c1b704fb8bb89d59ae8065fe6f72d68ba30e368f3b00e89",
}


def master_path(name, path_id):
    return next(p.get("d") for p in ET.parse(ROOT / "brand/sun-disc" / name).iter() if p.get("id") == path_id)


class BrandAssetsTests(unittest.TestCase):
    def test_badge_masters_are_the_approved_ones(self):
        for name, digest in APPROVED.items():
            self.assertEqual(digest, GENERATOR.PINS[name])
            self.assertEqual(digest, hashlib.sha256((ROOT / "brand/sun-disc" / name).read_bytes()).hexdigest())

    def test_oversized_master_keeps_every_exact_vertex_and_closed_contour(self):
        paths = [p.get("d") for p in ET.parse(ROOT / "brand/sun-disc/winged-sun-badge-lines.svg").iter() if p.get("d")]
        original = max(paths, key=len)
        self.assertGreater(len(original), 32767)  # Actual previously crashing master.
        compact = GENERATOR.compact_polygon_path(original)
        self.assertLessEqual(len(compact.encode("utf-8")), 32767)
        self.assertEqual(vertices(original), vertices(compact))
        self.assertEqual(110, sum(kind == "close" for kind, _ in vertices(compact)))

    def test_generated_wings_keep_the_master_contours_exactly(self):
        for drawable, master in [("common/src/main/res/drawable/ic_fearless_logo.xml", "winged-sun-badge-lines.svg"),
                                 ("common/src/main/res/drawable/fw_brand_badge.xml", "winged-sun-badge-lines.svg"),
                                 ("common/src/main/res/drawable/fw_brand_wings.xml", "winged-sun-badge-lines.svg"),
                                 ("app/src/main/res/drawable/ic_launcher_foreground.xml", "winged-sun-badge-lines.svg"),
                                 ("app/src/main/res/drawable/ic_launcher_monochrome.xml", "winged-sun-badge.svg"),
                                 ("common/src/main/res/drawable/ic_fearless.xml", "winged-sun-badge.svg")]:
            wings = vertices(master_path(master, "badge-wings"))
            generated = [vertices(p.get(ANDROID + "pathData")) for p in ET.parse(ROOT / drawable).iter()
                         if p.tag == "path" and not re.search(r"[^MLHVZmlhvz0-9. ,\-]", p.get(ANDROID + "pathData"))]
            self.assertIn(wings, generated, drawable)

    def test_tinted_marks_cut_the_keyline_gap_with_a_clip_not_by_editing_the_wings(self):
        keyline = master_path("winged-sun-badge.svg", "badge-disc-keyline")
        for drawable in ("common/src/main/res/drawable/ic_fearless_logo.xml", "common/src/main/res/drawable/ic_fearless.xml",
                         "app/src/main/res/drawable/ic_launcher_monochrome.xml"):
            clips = [c.get(ANDROID + "pathData") for c in ET.parse(ROOT / drawable).iter() if c.tag == "clip-path"]
            self.assertEqual(["M0 0H512V512H0Z" + keyline], clips, drawable)
            fills = {p.get(ANDROID + "fillColor") for p in ET.parse(ROOT / drawable).iter() if p.tag == "path"}
            self.assertEqual({"#FFFFFFFF"}, fills, drawable)

    def test_launcher_keyline_meets_the_background_gradient_without_a_seam(self):
        def gradient(path):
            return next(g for g in ET.parse(ROOT / path).iter() if g.tag == "gradient")
        background = gradient("app/src/main/res/drawable/ic_launcher_background.xml")
        foreground = gradient("app/src/main/res/drawable/ic_launcher_foreground.xml")
        for key in ("startX", "startY", "endX", "endY"):
            mapped = Decimal(foreground.get(ANDROID + key)) * Decimal(".60") + Decimal("102.4")
            self.assertLess(abs(mapped - Decimal(background.get(ANDROID + key))), Decimal("0.0001"), key)
        self.assertEqual([i.get(ANDROID + "color") for i in background], [i.get(ANDROID + "color") for i in foreground])
        for variant in ("main", "debug", "release"):
            icon = (ROOT / f"app/src/{variant}/res/mipmap-anydpi-v33/ic_launcher.xml").read_text()
            self.assertIn('<monochrome android:drawable="@drawable/ic_launcher_monochrome" />', icon)

    def test_wide_mark_is_the_badge_without_its_circle(self):
        root = ET.parse(ROOT / "common/src/main/res/drawable/fw_brand_wings.xml").getroot()
        paths = [p for p in root.iter() if p.tag == "path"]
        self.assertEqual(3, len(paths), "wings, keyline and disc; never the circle")
        circle = next(p.get("d") for p in ET.parse(ROOT / "brand/sun-disc/winged-sun-badge-lines.svg").iter() if p.get("d", "").startswith("M256 512C"))
        for path in paths:
            self.assertNotEqual(circle, path.get(ANDROID + "pathData"))
        for path_id, path in zip(("badge-wings", "badge-disc-keyline", "badge-disc"), paths):
            master = master_path("winged-sun-badge-lines.svg", path_id)
            if path_id == "badge-disc-keyline":
                self.assertEqual(master, path.get(ANDROID + "pathData"))
            else:
                self.assertEqual(vertices(master), vertices(path.get(ANDROID + "pathData")), path_id)
        wings = [position for kind, position in vertices(master_path("winged-sun-badge-lines.svg", "badge-wings")) if position]
        group = next(g for g in root.iter() if g.tag == "group")
        left, top = -Decimal(group.get(ANDROID + "translateX")), -Decimal(group.get(ANDROID + "translateY"))
        width, height = Decimal(root.get(ANDROID + "viewportWidth")), Decimal(root.get(ANDROID + "viewportHeight"))
        self.assertLessEqual(left, min(x for x, _ in wings), "no wing tip is cut off")
        self.assertGreaterEqual(left + width, max(x for x, _ in wings), "no wing tip is cut off")
        self.assertEqual(Decimal("257.7"), top + height / 2, "the disc's centre is the frame's centre")
        self.assertEqual(Decimal("66.2"), height / 2, "the frame holds the whole keyline disc")
        # The aspect the views size the mark with (FwWings.ASPECT).
        self.assertLess(abs(width / height - Decimal(root.get(ANDROID + "width")[:-2]) / Decimal(root.get(ANDROID + "height")[:-2])), Decimal("0.001"))

    def test_retired_bird_artwork_is_gone(self):
        for retired in ("common/src/main/res/drawable-xxxhdpi/ic_wallet.png", "common/src/main/res/drawable/animated_bird.gif",
                        "common/src/main/res/drawable/background_story_wallet.png"):
            self.assertFalse((ROOT / retired).exists(), retired)
        for generated in ("ic_wallet", "ic_qr_code_logo"):
            self.assertIn("Generated from approved Figma masters", (ROOT / f"common/src/main/res/drawable/{generated}.xml").read_text())

    def test_all_generated_vector_attributes_fit_aapt_string_pool(self):
        count = 0
        for folder in (ROOT / "common/src/main/res/drawable", *(ROOT / f"app/src/{v}/res/drawable" for v in ("main", "debug", "release"))):
            for path in folder.glob("*.xml"):
                if "Generated from approved Figma masters" not in path.read_text():
                    continue
                for element in ET.parse(path).iter():
                    for value in element.attrib.values():
                        self.assertLessEqual(len(value.encode("utf-8")), 32767, str(path))
                count += 1
        self.assertEqual(20, count)


if __name__ == "__main__":
    unittest.main()
