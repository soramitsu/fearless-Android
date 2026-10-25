import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('palette', Path(__file__).with_name('generate-legacy-palette.py'))
palette = importlib.util.module_from_spec(spec)
spec.loader.exec_module(palette)

class LegacyPaletteTests(unittest.TestCase):
    def test_deterministic_outputs_keep_original_values_and_aliases(self):
        raw = palette.SOURCE.read_bytes()
        kotlin, xml = palette.generate(raw)
        self.assertEqual((kotlin, xml), palette.generate(raw))
        self.assertIn('val white60 = Color(0xA6FFFFFF)', kotlin)
        self.assertIn('val white64 = Color(0xA3FFFFFF)', kotlin)
        self.assertIn('val transparent = Color(0x00FFFFFF)', kotlin)
        self.assertIn('<color name="fw_legacy_white_64">#A6FFFFFF</color>', xml)
        self.assertIn('<color name="fw_legacy_black1">@color/fw_legacy_gray1</color>', xml)

    def test_unknown_or_forward_alias_and_invalid_color_are_rejected(self):
        original = json.loads(palette.SOURCE.read_bytes())
        for entry in [{'alias': 'notAColor'}, {'alias': 'white'}, {'argb': '123'}, {'argb': 'FFFFFFFF', 'alias': 'white'}]:
            with self.subTest(entry=entry):
                candidate = json.loads(json.dumps(original))
                candidate['compose']['colorPrimary'] = entry
                with self.assertRaises(ValueError): palette.generate(json.dumps(candidate).encode())

    def test_xml_alias_must_resolve_within_original_map(self):
        candidate = json.loads(palette.SOURCE.read_bytes())
        candidate['xml']['colorPrimary'] = '@color/new_unreviewed_resource'
        with self.assertRaises(ValueError): palette.generate(json.dumps(candidate).encode())

if __name__ == '__main__': unittest.main()
