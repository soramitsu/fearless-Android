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
PINS = {
    "winged-sun-badge-lines.svg": "61695664e36233812e24676fd5d6d533f009e237279a95f9ae6ce5f3c0889a07",
    "winged-sun-badge.svg": "c46079a2baf438c13b6782c176f173aab70418a80bc2457c56ad8b797990bb8e",
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


def vector(source, width, height, foreground=False, adaptive=False, background=False):
    svg = ET.fromstring(source)
    _, _, vw, vh = svg.attrib["viewBox"].split()
    root = ET.Element("vector", {attr("width"): f"{width}dp", attr("height"): f"{height}dp",
                                attr("viewportWidth"): vw, attr("viewportHeight"): vh})
    parent = root
    if adaptive:
        # The original white mark stays inside the 66/108 adaptive-icon safe zone.
        parent = ET.SubElement(root, "group", {attr("scaleX"): ".60", attr("scaleY"): ".60",
                                              attr("translateX"): "102.4", attr("translateY"): "102.4"})
    gradients = {x.attrib["id"]: x for x in svg.iter() if x.tag.endswith("linearGradient")}

    def visit(element, opacity=1.0, inherited_fill="none"):
        tag = element.tag.split("}")[-1]
        opacity *= float(element.attrib.get("opacity", "1"))
        fill = element.attrib.get("fill", inherited_fill)
        if tag in ("svg", "g"):
            assert "transform" not in element.attrib
            for child in element:
                visit(child, opacity, fill)
        elif tag == "path":
            gradient = fill.startswith("url(")
            if (foreground and gradient) or (background and not gradient):
                return
            data = "M0 0H512V512H0Z" if background else compact_polygon_path(element.attrib["d"])
            values = {attr("pathData"): data, attr("fillType"): "evenOdd" if element.attrib.get("fill-rule") == "evenodd" else "nonZero"}
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
            path = ET.SubElement(parent, "path", values)
            if gradient:
                definition = gradients[fill[5:-1]]
                assert definition.attrib["gradientUnits"] == "userSpaceOnUse"
                item = ET.SubElement(path, "{" + AAPT + "}attr", {"name": "android:fillColor"})
                grad = ET.SubElement(item, "gradient", {attr("type"): "linear", **{
                    attr(new): definition.attrib[old] for old, new in [("x1", "startX"), ("y1", "startY"), ("x2", "endX"), ("y2", "endY")]}})
                for stop in definition:
                    ET.SubElement(grad, "item", {attr("offset"): stop.attrib["offset"], attr("color"): stop.attrib["stop-color"]})
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
    for name, source, width, height, foreground in [
        ("fw_brand_hero", "colour", 246, 50.575, False),
        ("fw_brand_badge", "badge-lines", 64, 64, False),
        ("fw_brand_badge_small", "badge", 24, 24, False),
        ("ic_fearless_logo", "badge-lines", 64, 64, True),
        ("ic_fearless_bird", "badge", 24, 24, True),
        ("ic_fearless", "badge", 24, 24, True),
        ("drawable_fearless_logo", "colour", 246, 50.575, False),
        ("fearless_bird_glossy", "colour", 246, 50.575, False),
    ]:
        write(common + name + ".xml", vector(sources["winged-sun-" + source + ".svg"], width, height, foreground))
    for variant in ["main", "debug", "release"]:
        prefix = "app/src/" + variant + "/res/"
        write(prefix + "drawable/ic_launcher_foreground.xml", vector(sources["winged-sun-badge-lines.svg"], 108, 108, True, True))
        write(prefix + "drawable/ic_launcher_background.xml", vector(sources["winged-sun-badge-lines.svg"], 108, 108, background=True))
        for qualifier, mono in [("", ""), ("-v33", '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n')]:
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
