#!/usr/bin/env python3
"""Generate the frozen Android legacy palette used during the approved token migration."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/fw.legacy.android.json'
DEST = ROOT / 'common/src/main/java/jp/co/soramitsu/common/compose/theme/fw/FwLegacyPalette.kt'
XML = ROOT / 'common/src/main/res/values/fw_legacy_colors.xml'


def generate(raw):
    values = json.loads(raw)
    if values['schemaVersion'] != 1:
        raise ValueError('Unknown legacy palette version')
    digest = hashlib.sha256(raw).hexdigest()
    lines = [
        '// GENERATED from design/fw.legacy.android.json; do not edit.',
        '// sha256 ' + digest,
        '// Regenerate: python3 scripts/generate-legacy-palette.py',
        'package jp.co.soramitsu.common.compose.theme.fw',
        '',
        'import androidx.compose.ui.graphics.Color',
        'import jp.co.soramitsu.common.compose.theme.tokens.Colors',
        '',
        '/** Frozen original palette; presentation can migrate without changing ambiguous legacy colors. */',
        'object FwLegacyPalette {'
    ]
    seen = set()
    for name, value in values['compose'].items():
        if not re.fullmatch(r'[a-z][A-Za-z0-9]*', name):
            raise ValueError('Invalid color name')
        if set(value) == {'argb'} and re.fullmatch(r'[0-9A-F]{8}', value['argb']):
            expression = 'Color(0x' + value['argb'] + ')'
        elif set(value) == {'alias'} and value['alias'] in seen:
            expression = value['alias']
        else:
            raise ValueError('Unknown color or forward alias: ' + name)
        lines.append('    val ' + name + ' = ' + expression)
        seen.add(name)
    for name, colors in values['gradients'].items():
        if not re.fullmatch(r'[a-z][A-Za-z0-9]*', name) or not colors or any(not re.fullmatch(r'[0-9A-F]{8}', c) for c in colors):
            raise ValueError('Invalid gradient')
        lines += ['', '    val ' + name + ' = listOf(']
        lines += ['        Color(0x' + color + ')' + (',' if i < len(colors) - 1 else '') for i, color in enumerate(colors)]
        lines.append('    )')
    for name, source in values['night'].items():
        if not all(re.fullmatch(r'[A-Z][A-Za-z0-9]*', s) for s in [name, source]):
            raise ValueError('Invalid night palette alias')
        lines.append('    val night' + name + ' = Colors.' + source)
    lines += ['}', '']
    xml = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- GENERATED from design/fw.legacy.android.json; do not edit. -->',
           '<!-- sha256 ' + digest + ' -->', '<resources>']
    for name, value in values['xml'].items():
        if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*', name):
            raise ValueError('Invalid XML color name')
        if value.startswith('@color/'):
            if value[7:] not in values['xml']:
                raise ValueError('Unknown XML alias')
            value = '@color/fw_legacy_' + value[7:]
        elif not re.fullmatch(r'#[A-Fa-f0-9]{3,8}', value) or len(value) not in [4, 5, 7, 9]:
            raise ValueError('Invalid XML color')
        xml.append('    <color name="fw_legacy_' + name + '">' + value + '</color>')
    xml += ['</resources>', '']
    # Parse before writing to keep malformed resources out of the source tree.
    ET.fromstring('\n'.join(xml))
    return '\n'.join(lines), '\n'.join(xml)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for path, content in zip([DEST, XML], generate(SOURCE.read_bytes())):
        if args.check:
            if not path.is_file() or path.read_text() != content:
                raise SystemExit('Legacy palette drift: ' + str(path))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
    print('Legacy Android palette ' + ('checked' if args.check else 'generated'))


if __name__ == '__main__':
    main()
