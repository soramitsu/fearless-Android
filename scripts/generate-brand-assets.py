#!/usr/bin/env python3
"""Derive Android artwork from the checksum-pinned, approved Figma SVG masters."""
import argparse
from decimal import Decimal
import hashlib
import json
import re
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)
# Badge masters: the owner-approved SORA-disc badge (disc radius 64, no cobras, wings byte-identical
# to the 2026-10-10 Figma export), fearless-wallet-web commit 1275e1a6 src/assets/brand/.
PINS = {
    "winged-sun-badge-lines.svg": "9756447ca43921b8098a52cd860e08dc78910bf23fcfe0e04ec1be036853d4d8",
    "winged-sun-badge.svg": "ff65329596da5e829c1b704fb8bb89d59ae8065fe6f72d68ba30e368f3b00e89",
    "winged-sun-colour.svg": "93158e0284a01e6b682ce1c87a331d769722444cf6ade68e17188d08b50b47e8",
    "winged-sun-feather-lines.svg": "930d8fff6d2b19a4c277e98a98ef3e5d832cc0b9c1334613168564acc1042acc",
    "winged-sun-silhouette.svg": "835903453ea6bc29ec57ea84b5f45f2417752f6acd0b95e68c52c0586d34a3cd",
}


def attr(name):
    return "{" + ANDROID + "}" + name


def compact_polygon_path(data):
    """Keep every polygon coordinate exact while staying below AAPT's UTF-8 limit.

    Only the oversized, checksum-pinned line badge needs this conversion. Decimal
    arithmetic preserves its coordinates and compound-path fill rule; contours
    cannot be split into separate drawables because that could fill their holes.
    """
    if len(data.encode("utf-8")) <= 32767:
        return data
    if re.search(r"[^MLHVZ0-9. ,\-]", data):
        raise ValueError("Oversized path is not a supported absolute polygon")

    def number(value):
        text = format(value, "f")
        if "." in text:
            text = text.rstrip("0").rstrip(".")
        if value == 0:
            return "0"
        return text.replace("0.", ".", 1) if text.startswith(("0.", "-0.")) else text

    x = y = start_x = start_y = Decimal(0)
    output, previous = [], None
    for match in re.finditer(r"([MLHVZ])([^MLHVZ]*)", data):
        command = match[1]
        values = [Decimal(v) for v in re.findall(r"-?\d+(?:\.\d+)?", match[2])]
        if len(values) != {"M": 2, "L": 2, "H": 1, "V": 1, "Z": 0}[command]:
            raise ValueError("Unexpected polygon coordinate count")
        if command == "M":
            x, y = values
            start_x, start_y = x, y
            emitted, coordinates = "M", values
        elif command == "L":
            emitted, coordinates = "l", [values[0] - x, values[1] - y]
            x, y = values
        elif command == "H":
            emitted, coordinates = "h", [values[0] - x]
            x = values[0]
        elif command == "V":
            emitted, coordinates = "v", [values[0] - y]
            y = values[0]
        else:
            emitted, coordinates = "Z", []
            x, y = start_x, start_y
        prefix = " " if emitted == previous and emitted not in ("M", "Z") else emitted
        output.append(prefix + " ".join(number(value) for value in coordinates))
        previous = emitted
    result = re.sub(r" +(?=-)", "", "".join(output))
    # A second decimal point starts a new SVG number, including in Android PathParser.
    result = re.sub(r"(\.\d+) +(?=\.)", r"\1", result)
    if len(result.encode("utf-8")) > 32767:
        raise ValueError("Generated path still exceeds AAPT's UTF-8 limit")
    return result


# Adaptive icons keep the mark inside the 66/108 safe zone: scale 0.6 about the viewport centre.
ADAPTIVE_SCALE = Decimal(".60")
ADAPTIVE_OFFSET = Decimal("102.4")
KEYLINE = "badge-disc-keyline"


def decimal_text(value):
    """Shortest exact text of a Decimal: 640 stays 640, 554.6666... keeps its digits."""
    text = format(value, "f")
    return text.rstrip("0").rstrip(".") if "." in text else text


def adaptive_inverse(value):
    """Viewport coordinate of the background layer -> coordinate inside the scaled foreground group."""
    return decimal_text((Decimal(value) - ADAPTIVE_OFFSET) / ADAPTIVE_SCALE)


def vector(source, width, height, mode="full", adaptive=False, viewport_width=None):
    """Android vector from an approved master. Contours are copied verbatim (or exactly compacted).

    full        every path, as drawn in the master
    mark        the white mark only; a clip-path (the square minus the keyline disc, opposite windings)
                hides the wings under the keyline exactly as the keyline does in the badge, so the disc
                keeps its hairline joint when the drawable is tinted (monochrome, tinted icons)
    foreground  the white mark with the keyline painted in the background layer's gradient, mapped
                through the safe-zone transform so both layers meet without a seam
    background  the badge gradient as a full square
    """
    svg = ET.fromstring(source)
    _, _, vw, vh = svg.attrib["viewBox"].split()
    root = ET.Element("vector", {attr("width"): f"{width}dp", attr("height"): f"{height}dp",
                                attr("viewportWidth"): viewport_width or vw, attr("viewportHeight"): vh})
    parent = root
    if viewport_width:
        # Centre a square master in a wider viewport (the QR overlay keeps its 2:1 slot).
        offset = (Decimal(viewport_width) - Decimal(vw)) / 2
        parent = ET.SubElement(root, "group", {attr("translateX"): decimal_text(offset)})
    if adaptive:
        parent = ET.SubElement(parent, "group", {attr("scaleX"): ".60", attr("scaleY"): ".60",
                                                attr("translateX"): "102.4", attr("translateY"): "102.4"})
    gradients = {x.attrib["id"]: x for x in svg.iter() if x.tag.endswith("linearGradient")}
    keyline = next((x.attrib["d"] for x in svg.iter() if x.attrib.get("id") == KEYLINE), None)
    emitted_background = False

    def gradient_fill(path, definition, remap=False):
        assert definition.attrib["gradientUnits"] == "userSpaceOnUse"
        item = ET.SubElement(path, "{" + AAPT + "}attr", {"name": "android:fillColor"})
        coordinates = {new: definition.attrib[old] for old, new in [("x1", "startX"), ("y1", "startY"), ("x2", "endX"), ("y2", "endY")]}
        if remap:
            coordinates = {key: adaptive_inverse(value) for key, value in coordinates.items()}
        grad = ET.SubElement(item, "gradient", {attr("type"): "linear", **{attr(key): value for key, value in coordinates.items()}})
        for stop in definition:
            ET.SubElement(grad, "item", {attr("offset"): stop.attrib["offset"], attr("color"): stop.attrib["stop-color"]})

    def visit(element, opacity=1.0, inherited_fill="none"):
        nonlocal emitted_background
        tag = element.tag.split("}")[-1]
        opacity *= float(element.attrib.get("opacity", "1"))
        fill = element.attrib.get("fill", inherited_fill)
        if tag in ("svg", "g"):
            assert "transform" not in element.attrib
            for child in element:
                visit(child, opacity, fill)
        elif tag == "path":
            gradient = fill.startswith("url(")
            is_keyline = element.attrib.get("id") == KEYLINE
            if mode == "background":
                if not gradient or emitted_background:
                    return
                emitted_background = True
            elif mode in ("mark", "foreground") and gradient and not (mode == "foreground" and is_keyline):
                return
            data = "M0 0H512V512H0Z" if mode == "background" else compact_polygon_path(element.attrib["d"])
            even_odd = element.attrib.get("fill-rule") == "evenodd"
            target = parent
            if mode == "mark" and keyline and element.attrib.get("id") == "badge-wings":
                # The square is clockwise and the keyline counter-clockwise, so the non-zero clip keeps
                # everything but the keyline disc; the wing contours themselves are untouched.
                target = ET.SubElement(parent, "group")
                ET.SubElement(target, "clip-path", {attr("pathData"): "M0 0H512V512H0Z" + keyline})
            values = {attr("pathData"): data, attr("fillType"): "evenOdd" if even_odd else "nonZero"}
            if not gradient:
                values[attr("fillColor")] = {"none": "#00000000", "white": "#FFFFFFFF", "black": "#FF000000"}.get(fill, fill)
            values[attr("fillAlpha")] = str(opacity)
            if "stroke" in element.attrib:
                values[attr("strokeColor")] = element.attrib["stroke"]
                values[attr("strokeWidth")] = element.attrib.get("stroke-width", "1")
                values[attr("strokeAlpha")] = str(opacity)
            for old, new in [("stroke-linecap", "strokeLineCap"), ("stroke-linejoin", "strokeLineJoin")]:
                if old in element.attrib:
                    values[attr(new)] = element.attrib[old]
            if len(values[attr("pathData")].encode("utf-8")) > 32767:
                raise ValueError("Path exceeds AAPT's UTF-8 limit")
            path = ET.SubElement(target, "path", values)
            if gradient:
                gradient_fill(path, gradients[fill[5:-1]], remap=mode == "foreground" and adaptive)
        elif tag != "defs":
            raise ValueError("Unsupported SVG element: " + tag)

    visit(svg)
    ET.indent(root)
    return '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from approved Figma masters; run scripts/generate-brand-assets.py. -->\n' + ET.tostring(root, encoding="unicode") + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--node", default="node")
    parser.add_argument("--sharp-module", default="sharp", help="Installed Sharp module path or module name")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    masters = root / "brand/sun-disc"
    sources = {name: (masters / name).read_bytes() for name in PINS}
    for name, data in sources.items():
        assert hashlib.sha256(data).hexdigest() == PINS[name], name
    generated = []

    def write(relative, text):
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        generated.append(path)

    common = "common/src/main/res/drawable/"
    for name, source, width, height, mode, viewport_width in [
        ("fw_brand_hero", "colour", 246, 50.575, "full", None),
        ("fw_brand_badge", "badge-lines", 64, 64, "full", None),
        ("fw_brand_badge_small", "badge", 24, 24, "full", None),
        ("ic_fearless_logo", "badge-lines", 64, 64, "mark", None),
        ("ic_fearless_bird", "badge", 24, 24, "mark", None),
        ("ic_fearless", "badge", 24, 24, "mark", None),
        ("drawable_fearless_logo", "colour", 246, 50.575, "full", None),
        ("fearless_bird_glossy", "colour", 246, 50.575, "full", None),
        # The default wallet avatar and the QR centre mark (2:1 slot) were the retired bird.
        ("ic_wallet", "badge", 40, 40, "full", None),
        ("ic_qr_code_logo", "badge", 96, 48, "full", "1024"),
    ]:
        write(common + name + ".xml", vector(sources["winged-sun-" + source + ".svg"], width, height, mode, viewport_width=viewport_width))
    for variant in ["main", "debug", "release"]:
        prefix = "app/src/" + variant + "/res/"
        write(prefix + "drawable/ic_launcher_foreground.xml", vector(sources["winged-sun-badge-lines.svg"], 108, 108, "foreground", True))
        write(prefix + "drawable/ic_launcher_background.xml", vector(sources["winged-sun-badge-lines.svg"], 108, 108, "background"))
        # Themed icons tint one layer: the solid badge's mark reads at launcher sizes.
        write(prefix + "drawable/ic_launcher_monochrome.xml", vector(sources["winged-sun-badge.svg"], 108, 108, "mark", True))
        for qualifier, mono in [("", ""), ("-v33", '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n')]:
            write(prefix + f"mipmap-anydpi{qualifier}/ic_launcher.xml", '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n' + mono + '</adaptive-icon>\n')
    tasks = []
    for variant in ["main", "release"]:
        for density, pixels in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
            path = root / f"app/src/{variant}/res/mipmap-{density}/ic_launcher.png"
            tasks.append({"input": str(masters / "winged-sun-badge-lines.svg"), "output": str(path), "size": pixels})
            generated.append(path)
    code = "const fs=require('fs'),sharp=require(process.argv[1]); (async()=>{for(const t of JSON.parse(fs.readFileSync(0,'utf8')))await sharp(t.input,{density:384}).resize(t.size,t.size).png().toFile(t.output)})().catch(e=>{console.error(e);process.exit(1)});"
    subprocess.run([args.node, "-e", code, args.sharp_module], input=json.dumps(tasks), text=True, check=True)
    manifest = {"format": "fearless.android.brand-assets", "version": 1, "source": "Figma6nJRypk3njagBkp9B642tg/node60201-10535", "masters": PINS,
                "outputs": {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(generated)}}
    write("brand/sun-disc/android-assets.json", json.dumps(manifest, indent=2) + "\n")
    print("Generated", len(generated) - 1, "pinned Android assets")


if __name__ == "__main__":
    main()
