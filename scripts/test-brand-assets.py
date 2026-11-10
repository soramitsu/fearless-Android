#!/usr/bin/env python3
"""The Android brand artwork is the pinned Yatagarasu kit, converted without touching a contour."""
import hashlib
import importlib.util
from decimal import Decimal
import json
import math
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("brand_assets", ROOT / "scripts/generate-brand-assets.py")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)

ANDROID = "{http://schemas.android.com/apk/res/android}"
SVG = "{http://www.w3.org/2000/svg}"
KIT = ROOT / "brand/yatagarasu"
# The approved kit (design package r3, 2026-11-10, regenerated 12:16): its manifest.json, as the brand session records it.
APPROVED_KIT_MANIFEST = "70771ec29c8607b1964a966066affb15bb02a2032973227d7f7d2ae3c80c048b"
RETIRED = [
    "brand/sun-disc",
    "common/src/main/res/drawable/fw_brand_wings.xml",
    "common/src/main/res/drawable/fw_brand_hero.xml",
    "common/src/main/res/drawable/drawable_fearless_logo.xml",
    "common/src/main/res/drawable/fearless_bird_glossy.xml",
    "common/src/main/res/drawable/fw_splash_sunlit.xml",
    "runtime-utils/src/main/java/jp/co/soramitsu/fearless_utils/icon/WingedSunClip.kt",
]


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def drawable(relative):
    return ET.parse(ROOT / relative).getroot()


def path_data(root):
    return [p.get(ANDROID + "pathData") for p in root.iter("path")]


def svg_paths(name):
    return [p.get("d") for p in ET.parse(KIT / name).getroot().iter(SVG + "path")]


def cubic_points(data, samples=24):
    """Points along an absolute M/C/L/Z path (the kit's only commands), sampled on every curve."""
    tokens = re.findall(r"[MCLZmclz]|-?\d*\.?\d+(?:[eE][-+]?\d+)?", data)
    points, index, command, current, start = [], 0, None, (0.0, 0.0), (0.0, 0.0)
    while index < len(tokens):
        if tokens[index].isalpha():
            command = tokens[index]
            index += 1
            if command in "Zz":
                current = start
                continue
        if command == "M":
            current = start = (float(tokens[index]), float(tokens[index + 1]))
            index += 2
            command = "L"
            points.append(current)
        elif command == "L":
            current = (float(tokens[index]), float(tokens[index + 1]))
            index += 2
            points.append(current)
        elif command == "C":
            c1 = (float(tokens[index]), float(tokens[index + 1]))
            c2 = (float(tokens[index + 2]), float(tokens[index + 3]))
            end = (float(tokens[index + 4]), float(tokens[index + 5]))
            index += 6
            for step in range(1, samples + 1):
                t = step / samples
                u = 1 - t
                points.append(tuple(u ** 3 * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t ** 3 * p3
                                    for p0, p1, p2, p3 in zip(current, c1, c2, end)))
            current = end
        else:
            raise ValueError("Unexpected path command " + str(command))
    return points


class BrandAssetsTests(unittest.TestCase):
    def test_kit_copies_are_the_pinned_approved_files(self):
        self.assertEqual(APPROVED_KIT_MANIFEST, GENERATOR.KIT_MANIFEST_SHA256)
        self.assertGreaterEqual(len(GENERATOR.PINS), 24)
        for name, digest in GENERATOR.PINS.items():
            self.assertEqual(digest, sha256(KIT / name), name)
        manifest = json.loads((KIT / "android-assets.json").read_text())
        self.assertEqual(APPROVED_KIT_MANIFEST, manifest["kitManifestSha256"])
        self.assertEqual(GENERATOR.PINS, manifest["inputs"])

    def test_committed_outputs_are_what_the_generator_writes(self):
        outputs = json.loads((KIT / "android-assets.json").read_text())["outputs"]
        self.assertEqual(54, len(outputs))
        for relative, digest in outputs.items():
            self.assertEqual(digest, sha256(ROOT / relative), relative)
        for name, destination, (width, height), recolour, viewport_width in GENERATOR.VECTORS:
            text = (KIT / name).read_text()
            self.assertEqual(GENERATOR.vector(text, width, height, recolour, viewport_width), (ROOT / destination).read_text(), destination)
        for name, destination in GENERATOR.COPIES:
            self.assertEqual(GENERATOR.PINS[name], sha256(ROOT / destination), destination)

    def test_every_vector_keeps_the_master_path_data_verbatim(self):
        for name, destination, *_ in GENERATOR.VECTORS:
            generated = path_data(drawable(destination))
            for data in svg_paths(name):
                self.assertIn(data, generated, destination)
            self.assertTrue(all(len(d.encode("utf-8")) <= 32767 for d in generated), destination)

    def test_groups_carry_the_masters_own_transforms(self):
        root = drawable("common/src/main/res/drawable/fw_brand_lockup.xml")
        groups = [(g.get(ANDROID + "translateX"), g.get(ANDROID + "translateY"), g.get(ANDROID + "scaleX")) for g in root.iter("group")]
        self.assertEqual(("120", "0", "0.78"), groups[0], "crest placement of the stacked lockup")
        self.assertEqual(("19.33", "412", "0.93"), groups[1], "wordmark placement of the stacked lockup")
        self.assertEqual(["80.5", "168", "256.5", "343", "421.5", "502", "586.5"], [g[0] for g in groups[2:]], "glyph advances")
        badge = drawable("common/src/main/res/drawable/fw_brand_badge.xml")
        self.assertEqual("M0 256A256 256 0 1 0 512 256A256 256 0 1 0 0 256Z", path_data(badge)[0], "the badge circle")
        gradient = next(badge.iter("gradient"))
        self.assertEqual(("radial", "256", "215.04", "378.88"), tuple(gradient.get(ANDROID + k) for k in ("type", "centerX", "centerY", "gradientRadius")))
        self.assertEqual(["#FFFF7A2E", "#FFE0126E", "#FF4A1160", "#FF120A2E"], [i.get(ANDROID + "color") for i in gradient])

    def test_lockup_keeps_its_proportions(self):
        root = drawable("common/src/main/res/drawable/fw_brand_lockup.xml")
        view_box = ET.parse(KIT / "masters/fearless-lockup-stacked.svg").getroot().get("viewBox").split()
        self.assertEqual(view_box[2:], [root.get(ANDROID + "viewportWidth"), root.get(ANDROID + "viewportHeight")])
        aspect = Decimal(view_box[2]) / Decimal(view_box[3])
        size = Decimal(root.get(ANDROID + "width")[:-2]) / Decimal(root.get(ANDROID + "height")[:-2])
        self.assertLess(abs(aspect - size), Decimal("0.0005"))
        # FwLockup.ASPECT in FwSun.kt is the same ratio.
        source = (ROOT / "common/src/main/java/jp/co/soramitsu/common/compose/theme/fw/FwSun.kt").read_text()
        self.assertIn("const val ASPECT = 640f / 505.30324f", source)
        self.assertIn("const val MAX_WIDTH_DP = 280", source)
        self.assertIn("const val WIDTH_FRACTION = 0.56f", source)

    def test_system_splash_crest_stays_inside_the_centre_192_dp_circle(self):
        root = drawable("common/src/main/res/drawable/fw_splash_icon.xml")
        self.assertEqual(("288dp", "288dp", "288", "288"), tuple(root.get(ANDROID + k) for k in ("width", "height", "viewportWidth", "viewportHeight")))
        group = next(root.iter("group"))
        scale, dx, dy = (float(group.get(ANDROID + k)) for k in ("scaleX", "translateX", "translateY"))
        reach = 0.0
        for data in path_data(root):
            for x, y in cubic_points(data):
                reach = max(reach, math.hypot(dx + scale * x - 144, dy + scale * y - 144))
        # Android 12+ keeps only the centre 192 dp of the 288 dp icon: the whole crest must sit inside it.
        self.assertLess(reach, 96.0)
        self.assertGreater(reach, 80.0, "the crest fills the circle rather than shrinking")

    def test_launch_art_puts_the_lockup_crest_where_the_system_splash_drew_it(self):
        dimens = {d.get("name"): d.text for d in ET.parse(ROOT / "common/src/main/res/values/fw_splash_dimens.xml").getroot()}
        width = float(dimens["fw_launch_lockup_width"][:-2])
        height = float(dimens["fw_launch_lockup_height"][:-2])
        drop = float(dimens["fw_launch_lockup_drop"][:-2])
        self.assertLessEqual(width, 280)
        self.assertAlmostEqual(width * 505.3032355154251 / 640, height, places=2)
        unit = width / 640
        crest_centre = 0.78 * 256 * unit
        self.assertAlmostEqual(height / 2 - crest_centre, drop / 2, places=2)
        launch = (ROOT / "common/src/main/res/drawable/fw_splash_launch.xml").read_text()
        self.assertIn('android:drawable="@color/fw_brand_ink"', launch)
        self.assertIn('android:top="@dimen/fw_launch_lockup_drop"', launch)
        starting = (ROOT / "app/src/main/res/values-v31/brand_starting_theme.xml").read_text()
        self.assertIn("@color/fw_brand_ink", starting)
        self.assertIn("@drawable/fw_splash_icon", starting)

    def test_launcher_icons_development_gold_store_white_and_themed(self):
        for variant in ("main", "debug", "release"):
            fills = {p.get(ANDROID + "fillColor") for p in drawable(f"app/src/{variant}/res/drawable/ic_launcher_foreground.xml").iter("path")}
            self.assertEqual({"#FFFFC24A"} if variant == "debug" else {"#FFFFFFFF"}, fills, variant)
            mono = {p.get(ANDROID + "fillColor") for p in drawable(f"app/src/{variant}/res/drawable/ic_launcher_monochrome.xml").iter("path")}
            self.assertEqual({"#FFFFFFFF"}, mono, variant)
            for icon in ("ic_launcher", "ic_launcher_round"):
                themed = (ROOT / f"app/src/{variant}/res/mipmap-anydpi-v33/{icon}.xml").read_text()
                self.assertIn('<monochrome android:drawable="@drawable/ic_launcher_monochrome" />', themed)
                self.assertTrue((ROOT / f"app/src/{variant}/res/mipmap-anydpi/{icon}.xml").is_file())
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('android:icon="@mipmap/ic_launcher"', manifest)
        self.assertIn('android:roundIcon="@mipmap/ic_launcher_round"', manifest)
        # The adaptive foreground keeps the crest inside the 66 dp safe zone of the 108 dp canvas.
        group = next(drawable("app/src/main/res/drawable/ic_launcher_foreground.xml").iter("group"))
        self.assertEqual(("0.5", "88"), (group.get(ANDROID + "scaleX"), group.get(ANDROID + "translateX")))

    def test_brand_tokens_follow_the_kit_colours(self):
        kit = json.loads((KIT / "tokens/colors.json").read_text())["colors"]
        semantic = json.loads((ROOT / "design/fw.tokens.json").read_text())["color"]["semantic"]
        hexes = {name: token["$value"]["hex"].upper() for name, token in semantic.items() if not name.startswith("$")}
        for token, key in [("brand", "pink"), ("fillTop", "pinkActive"), ("brandInk", "ink"),
                           ("bgBase", "ink"), ("brandNight", "night"), ("surfaceRaised2", "night"), ("brandPlum", "plum"),
                           ("brandGold", "gold"), ("sunCore", "gold"),
                           ("textPrimary", "text"), ("textSecondary", "textMuted")]:
            self.assertEqual(kit[key].upper(), hexes[token], token)
        # Spec item 9 (the kit's tools/apply-tokens.mjs remap): the primary fill runs #D6006A to #A8005A, which is also
        # the pressed primary; brand text, selection and accent edges are #FF4FA3; #E6006F stays off buttons with text.
        for token, value in [("fillBottom", "#A8005A"), ("brandPressed", "#A8005A"), ("textBrand", "#FF4FA3"),
                             ("textLink", "#FF4FA3"), ("accentEdge", "#FF4FA3"), ("selectEdge", "#FF4FA3")]:
            self.assertEqual(value, hexes[token], token)
        stops = [s["color"]["hex"].upper() for s in json.loads((ROOT / "design/fw.tokens.json").read_text())["gradient"]["primaryFill"]["$value"]]
        self.assertEqual(["#D6006A", "#A8005A"], stops)
        # The retired magentas are gone from every colour in the token file.
        text = (ROOT / "design/fw.tokens.json").read_text().upper()
        for retired in ("#FF4DB8", "#FF009A", "#D6007F", "#A3006A", "#EE0077"):
            self.assertNotIn(retired, text, retired)
        # Prices and status keep the shared semantic tokens (spec item 9): a gain is #38E8E0 in every app. Kit mint
        # #4FF0B8 is for brand art only; it would also fail the protan gate against priceDown (FwContrastContractTest).
        self.assertEqual("#4FF0B8", kit["mint"].upper())
        for token, value in [("priceUp", "#38E8E0"), ("priceDown", "#FF9F57")]:
            self.assertEqual(value, hexes[token], token)
        # Semantic colours stay.
        for token, value in [("success", "#3DF2B0"), ("warning", "#FFC452"), ("danger", "#FF6B5E"), ("info", "#2EE6FF")]:
            self.assertEqual(value, hexes[token], token)

    def test_wallet_avatar_mask_is_the_crest_badge(self):
        clip = (ROOT / "runtime-utils/src/main/java/jp/co/soramitsu/fearless_utils/icon/CrestClip.kt").read_text()
        badge = ET.parse(KIT / "svg/crest-badge-small.svg").getroot()
        group = next(g for g in badge.iter(SVG + "g") if g.get("transform"))
        self.assertIn(f'internal const val CREST_TRANSFORM = "{group.get("transform")}"', clip)
        self.assertIn(f'internal const val CREST_PATH = "{svg_paths("svg/crest-badge-small.svg")[0]}"', clip)

    def test_winged_sun_artwork_and_its_code_are_retired(self):
        for retired in RETIRED:
            self.assertFalse((ROOT / retired).exists(), retired)
        pattern = re.compile(r"fw_brand_wings|fw_brand_hero|FwWings|FwSunGlowDrawable|WINGED_SUN|winged-sun|fearless_bird_glossy|drawable_fearless_logo|fw_splash_sunlit")
        for module in ROOT.iterdir():
            source = module / "src"
            if not source.is_dir():
                continue
            for path in source.rglob("*"):
                if path.suffix in (".kt", ".java", ".xml") and path.is_file():
                    self.assertIsNone(pattern.search(path.read_text(errors="ignore")), str(path.relative_to(ROOT)))

    def test_welcome_media_is_the_kit_hero(self):
        raw = ROOT / "feature-onboarding-impl/src/main/res/raw"
        self.assertEqual(GENERATOR.PINS["hero/welcome-idle.mp4"], sha256(raw / "fw_welcome_idle.mp4"))
        self.assertEqual(GENERATOR.PINS["hero/welcome-caw.mp4"], sha256(raw / "fw_welcome_caw.mp4"))
        self.assertEqual(GENERATOR.PINS["audio/caw.m4a"], sha256(raw / "fw_welcome_caw_sound.m4a"))
        poster = ROOT / "feature-onboarding-impl/src/main/res/drawable-nodpi/fw_welcome_poster.webp"
        self.assertEqual(GENERATOR.PINS["hero/welcome-poster.webp"], sha256(poster))

    def test_transform_parser_refuses_what_it_cannot_express(self):
        self.assertEqual({"scaleX": "0.5", "scaleY": "0.5", "translateX": "88", "translateY": "88"},
                         GENERATOR.parse_transform("translate(88 88) scale(0.5)"))
        self.assertEqual({}, GENERATOR.parse_transform("translate(0 0)"))
        for unsupported in ("rotate(45)", "scale(2) translate(1 1)", "matrix(1 0 0 1 0 0)"):
            with self.assertRaises(ValueError):
                GENERATOR.parse_transform(unsupported)


if __name__ == "__main__":
    unittest.main()
