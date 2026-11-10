#!/usr/bin/env python3
"""Derive the Android artwork from the checksum-pinned Yatagarasu brand kit.

The kit (``brand/yatagarasu`` in the Fearless workspace, generated from the design package
``design/yatagarasu-20261110``, package r3) is not committed anywhere. The files this app
consumes are copied byte for byte into ``brand/yatagarasu/`` and pinned below, together with the
SHA-256 of the kit's ``manifest.json``.

    python3 scripts/generate-brand-assets.py                 regenerate from the in-repo copies
    python3 scripts/generate-brand-assets.py --kit <kit>     refresh the copies from a kit first

SVG path data is copied verbatim into the VectorDrawables. Only the frames around it change:
groups carry the masters' own translate/scale transforms, and the badges' gradient circle becomes
the equivalent arc path. Rasters, video and sound are copied as they are.
"""
import argparse
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
SVG = "http://www.w3.org/2000/svg"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)

# SHA-256 of the kit's manifest.json (fearless-yatagarasu-brand-kit, design package r3, regenerated 2026-11-10 12:16;
# against the first r3 kit 4575466317…5028 only audio/caw.m4a changed: afconvert timestamps, identical decoded audio).
KIT_MANIFEST_SHA256 = "70771ec29c8607b1964a966066affb15bb02a2032973227d7f7d2ae3c80c048b"
# Every kit file the app consumes, at its kit path; the in-repo copy keeps the same path under COPY.
PINS = {
    "masters/fearless-crest.svg": "0dddb03fc97971d0c9226e6ed052565dfe52e75681b1df99f5b3c356f75bede5",
    "masters/fearless-crest-small.svg": "e5fdf61cbe6300ff4242c603d273b316b9866d770897cbf31cdc8aeec32a5910",
    "masters/fearless-lockup-stacked.svg": "459285f612e3283fa4c8aefcd46fab37ff3a67c447b1ef76d96a5cca9aa2f119",
    "svg/android-adaptive-foreground.svg": "6a66d02bc75b81b49a81783036b44b9daafb2b7229732eab8eb842f6dcaf201f",
    "svg/android-adaptive-background.svg": "fffe81d2bcd8ff65c1c23a9a73790d67ea84ae1fa04e719068f2d0df41a9fbd4",
    "svg/android-adaptive-monochrome.svg": "6a66d02bc75b81b49a81783036b44b9daafb2b7229732eab8eb842f6dcaf201f",
    "svg/android12-splash-icon.svg": "ead6fc2aaba4a68ac44fd79558e3326ff8b8453bae3059ffee35e3e8b6a982f6",
    "svg/crest-badge.svg": "7bb8f75f8df17f72b36fe783bf5f35f31f7d9e53b1025b7d8fe2ae42824e240c",
    "svg/crest-badge-small.svg": "cc2bce14632d0d24eedcb578e47fadc4852478a01b67fa0d81c2d99b37bad004",
    "png/android/ic_launcher-mdpi.png": "48df1d14284d30361dc095becad4c39579f62ee5ffa4f9ff148c3196a46b1aae",
    "png/android/ic_launcher-hdpi.png": "de2e241b08406af3ea3406315583787b7078cde22bb4a257e8f2d0d543767178",
    "png/android/ic_launcher-xhdpi.png": "531c8c87d20989f775548a9b83d0d5b42e635ccd7b85d92596d9b64dc4762f22",
    "png/android/ic_launcher-xxhdpi.png": "927375abe0efa66682e2ac759bd13822dd54929f66cadebff1a80a1995763c4b",
    "png/android/ic_launcher-xxxhdpi.png": "4f8b4373461fec1fc16e6a074262e1ebf19507cf337ccc7aab461e8dd007a7da",
    "png/android/ic_launcher_round-mdpi.png": "31babfc818d10c1c5f0af665711285e2d09f0a8db781acd1050ea14544cfd6a2",
    "png/android/ic_launcher_round-hdpi.png": "e92d405b878792be2e59fa838e7b5435cb4f6bc82ea99dcd9940fd6d252e05a1",
    "png/android/ic_launcher_round-xhdpi.png": "a79ddf92807f13485294a3df84d3e111d88ec802ed4a0497a09819aeec9ba768",
    "png/android/ic_launcher_round-xxhdpi.png": "8ad53c918a57d01dad949ad272bdccb1455b82f9c676be938097ac917789cc61",
    "png/android/ic_launcher_round-xxxhdpi.png": "f81533630be097d6e5e80fdd27b0b4557f7e429e50701aea8f7a49d67e791481",
    "hero/welcome-idle.mp4": "5a58764e1f06f5825bb5acba26832286ba8c3718b307cb37260b3c4e392cf114",
    "hero/welcome-caw.mp4": "48ae947b681e9e801f09211779bbb9973234d3a79101b53a61b4939d77dbca1f",
    "hero/welcome-poster.webp": "d15dc23aabf7972e807816d80fe9419eb3e3a8e46db4541656d3bc201f7a3eed",
    "audio/caw.m4a": "5094385060c15d02a391fc082b255d78a966434b7a22e1aa6151e21d79371fc6",
    "tokens/colors.json": "defdad74f63dec6421a9a5d8d3ef4042d6292eb51ebb044bf725cb18528cf4e9",
}
COPY = "brand/yatagarasu"
# Brand colours this app takes from the kit's tokens/colors.json (design/fw.tokens.json carries them).
GOLD = "#FFC24A"
GENERATED = "Generated from the pinned Yatagarasu brand kit; run scripts/generate-brand-assets.py."

DENSITIES = ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")


def attr(name):
    return "{" + ANDROID + "}" + name


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def number(value):
    """Shortest exact text of a Decimal: 640 stays 640, 215.04 keeps its digits."""
    text = format(value, "f")
    return text.rstrip("0").rstrip(".") if "." in text else text


def color(value):
    """SVG colour (#rgb or #rrggbb) to an opaque Android #AARRGGBB."""
    hex_digits = value.strip().lstrip("#")
    if re.fullmatch(r"[0-9a-fA-F]{3}", hex_digits):
        hex_digits = "".join(c * 2 for c in hex_digits)
    if not re.fullmatch(r"[0-9a-fA-F]{6}", hex_digits):
        raise ValueError("Unsupported colour " + value)
    return "#FF" + hex_digits.upper()


def parse_transform(text):
    """An SVG ``translate(tx ty) scale(sx sy)`` transform as the attributes of one Android group.

    Android groups scale first and translate after (p' = S p + T), which is exactly the SVG matrix
    of translate followed by scale, so the master's own number text is kept. The kit uses no other
    form; anything else is refused rather than approximated.
    """
    match = re.fullmatch(r"\s*(?:translate\(([^)]*)\))?\s*(?:scale\(([^)]*)\))?\s*", text)
    if not match or not (match[1] or match[2]):
        raise ValueError("Unsupported transform " + text)
    group = {}
    if match[2]:
        values = re.split(r"[\s,]+", match[2].strip())
        group["scaleX"], group["scaleY"] = values[0], values[-1]
    if match[1]:
        values = re.split(r"[\s,]+", match[1].strip())
        x, y = values[0], values[1] if len(values) > 1 else "0"
        if (Decimal(x), Decimal(y)) != (0, 0):
            group["translateX"], group["translateY"] = x, y
    return group


def view_box(svg):
    x, y, w, h = svg.attrib["viewBox"].split()
    return x, y, w, h


def vector(source, width, height, recolour=None, viewport_width=None):
    """Android vector of a kit SVG. Path data is copied verbatim; groups keep the master transforms.

    recolour       {svg fill: new fill} applied to plain fills (the development icon's gold crest)
    viewport_width a wider viewport with the artwork centred in it (the QR centre mark's 2:1 slot)
    """
    svg = ET.fromstring(source)
    x, y, vw, vh = view_box(svg)
    root = ET.Element("vector", {attr("width"): f"{width}dp", attr("height"): f"{height}dp",
                                 attr("viewportWidth"): viewport_width or vw, attr("viewportHeight"): vh})
    parent = root
    if viewport_width:
        offset = (Decimal(viewport_width) - Decimal(vw)) / 2
        parent = ET.SubElement(root, "group", {attr("translateX"): number(offset)})
    if (Decimal(x), Decimal(y)) != (0, 0):
        parent = ET.SubElement(parent, "group", {attr("translateX"): number(-Decimal(x)), attr("translateY"): number(-Decimal(y))})
    gradients = {g.attrib["id"]: g for g in svg.iter("{%s}radialGradient" % SVG)}

    def fill_of(value):
        if recolour and value.lower() in recolour:
            value = recolour[value.lower()]
        return color(value)

    def gradient_fill(path, definition, box):
        """objectBoundingBox radial gradient over a square box (x, y, side): the SVG default units."""
        if definition.attrib.get("gradientUnits", "objectBoundingBox") != "objectBoundingBox":
            raise ValueError("Only bounding-box gradients are used by the kit")
        bx, by, side = box

        def fraction(name):
            text = definition.attrib[name]
            if not text.endswith("%"):
                raise ValueError("Expected a percentage for " + name)
            return Decimal(text[:-1]) / 100

        item = ET.SubElement(path, "{" + AAPT + "}attr", {"name": "android:fillColor"})
        grad = ET.SubElement(item, "gradient", {
            attr("type"): "radial",
            attr("centerX"): number(bx + fraction("cx") * side),
            attr("centerY"): number(by + fraction("cy") * side),
            # For a square box the percentage of r is of the side (sqrt((w^2 + h^2) / 2)).
            attr("gradientRadius"): number(fraction("r") * side),
        })
        for stop in definition:
            ET.SubElement(grad, "item", {attr("offset"): stop.attrib["offset"], attr("color"): color(stop.attrib["stop-color"])})

    def emit(target, data, fill, even_odd, box=None):
        values = {attr("pathData"): data, attr("fillType"): "evenOdd" if even_odd else "nonZero"}
        gradient = fill.startswith("url(")
        if not gradient:
            values[attr("fillColor")] = fill_of(fill)
        if len(data.encode("utf-8")) > 32767:
            raise ValueError("Path exceeds AAPT's UTF-8 limit")
        path = ET.SubElement(target, "path", values)
        if gradient:
            gradient_fill(path, gradients[fill[5:-1]], box)

    def visit(element, target, inherited_fill):
        tag = element.tag.split("}")[-1]
        fill = element.attrib.get("fill", inherited_fill)
        if "transform" in element.attrib:
            group = parse_transform(element.attrib["transform"])
            if group:
                target = ET.SubElement(target, "group", {attr(k): v for k, v in group.items()})
        if tag in ("svg", "g"):
            for child in element:
                visit(child, target, fill)
        elif tag == "path":
            emit(target, element.attrib["d"], fill, element.attrib.get("fill-rule") == "evenodd")
        elif tag == "circle":
            cx, cy, r = (Decimal(element.attrib[k]) for k in ("cx", "cy", "r"))
            data = f"M{number(cx - r)} {number(cy)}A{number(r)} {number(r)} 0 1 0 {number(cx + r)} {number(cy)}" \
                   f"A{number(r)} {number(r)} 0 1 0 {number(cx - r)} {number(cy)}Z"
            emit(target, data, fill, False, (cx - r, cy - r, 2 * r))
        elif tag == "rect":
            w, h = Decimal(element.attrib["width"]), Decimal(element.attrib["height"])
            rx, ry = Decimal(element.attrib.get("x", "0")), Decimal(element.attrib.get("y", "0"))
            if w != h:
                raise ValueError("Only square rects are used by the kit")
            data = f"M{number(rx)} {number(ry)}H{number(rx + w)}V{number(ry + h)}H{number(rx)}Z"
            emit(target, data, fill, False, (rx, ry, w))
        elif tag != "defs":
            raise ValueError("Unsupported SVG element: " + tag)

    for child in svg:
        visit(child, parent, svg.attrib.get("fill", "#000"))
    ET.indent(root)
    text = ET.tostring(root, encoding="unicode")
    if 'xmlns:aapt=' not in text and "aapt:" in text:
        raise ValueError("aapt namespace missing")
    return '<?xml version="1.0" encoding="utf-8"?>\n<!-- ' + GENERATED + ' -->\n' + text + "\n"


def check_single_fill(source, old):
    """The kit's development-icon rule recolours the crest's one plain fill; refuse anything else."""
    if source.count(f'fill="{old}"') != 1:
        raise ValueError("Expected exactly one crest fill " + old)


ADAPTIVE = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '{monochrome}</adaptive-icon>\n')
MONOCHROME = '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'

# (kit file, destination, size in dp, recolour, wide viewport)
COMMON = "common/src/main/res/drawable/"
VECTORS = [
    # Android 12+ system splash: 288 dp box, the whole crest inside the centre 192 dp circle.
    ("svg/android12-splash-icon.svg", COMMON + "fw_splash_icon.xml", (288, 288), None, None),
    # Full-page brand screens (PIN, welcome, onboarding start, About, launch art): crest over wordmark.
    ("masters/fearless-lockup-stacked.svg", COMMON + "fw_brand_lockup.xml", (280, "221.07"), None, None),
    # Round slots: the crest in the app-icon gradient circle; the small crest below 64 dp.
    ("svg/crest-badge.svg", COMMON + "fw_brand_badge.xml", (64, 64), None, None),
    ("svg/crest-badge-small.svg", COMMON + "fw_brand_badge_small.xml", (24, 24), None, None),
    ("svg/crest-badge-small.svg", COMMON + "ic_wallet.xml", (40, 40), None, None),
    ("svg/crest-badge-small.svg", COMMON + "ic_qr_code_logo.xml", (96, 48), None, "1024"),
    # Tintable marks: the white crest alone, small below 64 dp.
    ("masters/fearless-crest.svg", COMMON + "ic_fearless_logo.xml", (64, 64), None, None),
    ("masters/fearless-crest-small.svg", COMMON + "ic_fearless_bird.xml", (24, 24), None, None),
    ("masters/fearless-crest-small.svg", COMMON + "ic_fearless.xml", (24, 24), None, None),
]
for variant in ("main", "debug", "release"):
    drawable = f"app/src/{variant}/res/drawable/"
    VECTORS += [
        # Development builds wear the gold crest so a debug install is never mistaken for the store app.
        ("svg/android-adaptive-foreground.svg", drawable + "ic_launcher_foreground.xml", (108, 108),
         {"#fff": GOLD} if variant == "debug" else None, None),
        ("svg/android-adaptive-background.svg", drawable + "ic_launcher_background.xml", (108, 108), None, None),
        ("svg/android-adaptive-monochrome.svg", drawable + "ic_launcher_monochrome.xml", (108, 108), None, None),
    ]

ONBOARDING = "feature-onboarding-impl/src/main/res/"
# (kit file, destination): copied byte for byte.
COPIES = [(f"png/android/ic_launcher-{d}.png", f"app/src/{v}/res/mipmap-{d}/ic_launcher.png") for v in ("main", "release") for d in DENSITIES]
COPIES += [(f"png/android/ic_launcher_round-{d}.png", f"app/src/{v}/res/mipmap-{d}/ic_launcher_round.png") for v in ("main", "release") for d in DENSITIES]
COPIES += [
    ("hero/welcome-idle.mp4", ONBOARDING + "raw/fw_welcome_idle.mp4"),
    ("hero/welcome-caw.mp4", ONBOARDING + "raw/fw_welcome_caw.mp4"),
    ("audio/caw.m4a", ONBOARDING + "raw/fw_welcome_caw_sound.m4a"),
    ("hero/welcome-poster.webp", ONBOARDING + "drawable-nodpi/fw_welcome_poster.webp"),
]


def refresh_from_kit(root, kit):
    manifest_bytes = (kit / "manifest.json").read_bytes()
    if sha256(manifest_bytes) != KIT_MANIFEST_SHA256:
        raise ValueError("The kit's manifest.json is not the pinned r3 kit")
    listed = {entry["path"]: entry["sha256"] for entry in json.loads(manifest_bytes)["files"]}
    for name, digest in PINS.items():
        if listed.get(name) != digest:
            raise ValueError("Kit manifest disagrees with the pin for " + name)
        data = (kit / name).read_bytes()
        if sha256(data) != digest:
            raise ValueError("Kit file differs from its manifest: " + name)
        target = root / COPY / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)


def load_sources(root):
    sources = {}
    for name, digest in PINS.items():
        data = (root / COPY / name).read_bytes()
        if sha256(data) != digest:
            raise ValueError("Pinned brand file changed: " + name)
        sources[name] = data
    return sources


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--kit", type=Path, help="Yatagarasu brand kit folder to refresh the pinned copies from")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.kit:
        refresh_from_kit(root, args.kit.resolve())
    sources = load_sources(root)
    outputs = {}

    def write(relative, data):
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        outputs[relative] = sha256(data)

    for name, destination, (width, height), recolour, viewport_width in VECTORS:
        text = sources[name].decode("utf-8")
        for old in recolour or {}:
            check_single_fill(text, old)
        write(destination, vector(text, width, height, recolour, viewport_width).encode("utf-8"))
    for variant in ("main", "debug", "release"):
        for qualifier, mono in (("", ""), ("-v33", MONOCHROME)):
            for icon in ("ic_launcher", "ic_launcher_round"):
                write(f"app/src/{variant}/res/mipmap-anydpi{qualifier}/{icon}.xml", ADAPTIVE.format(monochrome=mono).encode("utf-8"))
    for name, destination in COPIES:
        write(destination, sources[name])
    manifest = {
        "format": "fearless.android.brand-assets",
        "version": 2,
        "kit": "fearless-yatagarasu-brand-kit",
        "kitManifestSha256": KIT_MANIFEST_SHA256,
        "inputs": PINS,
        "outputs": dict(sorted(outputs.items())),
    }
    (root / COPY / "android-assets.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Generated", len(outputs), "Android brand assets from", len(PINS), "pinned kit files")


if __name__ == "__main__":
    main()
