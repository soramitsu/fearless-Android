#!/usr/bin/env python3
"""Regress the AAPT path overflow without changing any approved contour or hole."""
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


class BrandAssetsTests(unittest.TestCase):
    def test_oversized_master_keeps_every_exact_vertex_and_closed_contour(self):
        paths = [p.get("d") for p in ET.parse(ROOT / "brand/sun-disc/winged-sun-badge-lines.svg").iter() if p.get("d")]
        original = max(paths, key=len)
        self.assertGreater(len(original), 32767)  # Actual previously crashing master.
        compact = GENERATOR.compact_polygon_path(original)
        self.assertLessEqual(len(compact.encode("utf-8")), 32767)
        self.assertEqual(vertices(original), vertices(compact))
        self.assertEqual(136, sum(kind == "close" for kind, _ in vertices(compact)))

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
        self.assertEqual(14, count)


if __name__ == "__main__":
    unittest.main()
