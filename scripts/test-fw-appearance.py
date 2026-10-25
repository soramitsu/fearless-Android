#!/usr/bin/env python3
"""Exercise real Gradle appearance inputs and inspect only non-secret generated fields."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SURFACES = (
    'RECOVERY', 'PORTFOLIO', 'MANAGE_ASSETS', 'SEND', 'RECEIVE', 'PICKERS',
    'UNAVAILABLE', 'SIGNING', 'WELCOME', 'APPEARANCE', 'NAVIGATION', 'MNEMONIC', 'STAKING', 'LOADING'
)
FLAGS = tuple('FW_RESTYLE_' + surface for surface in SURFACES)
GENERATED = ROOT / 'common/build/generated/source/buildConfig/debug/jp/co/soramitsu/common/BuildConfig.java'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    args = parser.parse_args()
    args.evidence_dir.mkdir(parents=True, exist_ok=False)
    results = []

    def run(name, properties, expected=None, diagnostic=None, unit=False, full=False):
        destination = args.evidence_dir / name
        destination.mkdir()
        tasks = [':common:testDebugUnitTest'] if unit else [':common:generateDebugBuildConfig']
        if full:
            tasks += ['detektAll']
        command = ['./gradlew', *tasks, '--console=plain', '--max-workers=2']
        if unit and not full:
            command += ['--tests', 'jp.co.soramitsu.common.compose.theme.fw.FwRestyleTest']
        command += ['-P' + key + '=' + value for key, value in properties.items()]
        with (destination / 'gradle.log').open('w') as log:
            completed = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        result = {'name': name, 'command': command, 'exit': completed.returncode}
        results.append(result)
        (args.evidence_dir / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
        if diagnostic:
            assert completed.returncode != 0, name + ': invalid input accepted'
            assert diagnostic in (destination / 'gradle.log').read_text(), name + ': wrong failure'
            result['expectedDiagnostic'] = diagnostic
        else:
            assert completed.returncode == 0, name + ': Gradle failed'
            # Never copy or print the complete BuildConfig: it may contain provider credentials.
            source = GENERATED.read_text()
            fields = dict(re.findall(r'public static final (?:String|boolean) (FW_[A-Z_]+) = (.+);', source))
            assert fields == expected, name + ': generated appearance fields differ'
            result['generatedAppearanceFields'] = fields
            (destination / 'appearance.json').write_text(json.dumps(fields, indent=2) + '\n')
            if unit:
                xml_dir = destination / 'unit-results'
                xml_dir.mkdir()
                counts = dict(tests=0, failures=0, errors=0, skipped=0)
                files = sorted((ROOT / 'common/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
                assert files, name + ': no test results'
                for path in files:
                    shutil.copy2(path, xml_dir / path.name)
                    tree = ET.parse(path).getroot()
                    for key in counts:
                        counts[key] += int(tree.attrib.get(key, 0))
                assert counts['tests'] >= 4 and counts['failures'] == counts['errors'] == counts['skipped'] == 0, counts
                result['unit'] = counts
        (args.evidence_dir / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
        print(name + ': PASS', flush=True)

    def expected(palette, flags):
        return {'FW_PALETTE': '"' + palette + '"', **flags}

    off = dict.fromkeys(FLAGS, 'false')
    on = dict.fromkeys(FLAGS, 'true')
    run('default', {}, expected('legacy', off), unit=True)
    run('all-v1', {'FW_PALETTE': 'v1', **on}, expected('v1', on), unit=True)
    run('global-rollback', {'FW_PALETTE': 'legacy', **on}, expected('legacy', on), unit=True)
    for surface in SURFACES:
        individual = {**on, 'FW_RESTYLE_' + surface: 'false'}
        run('rollback-' + surface.lower(), {'FW_PALETTE': 'v1', **individual}, expected('v1', individual), unit=surface == 'RECOVERY')
    for index, value in enumerate(['V1', 'v1 ', '', 'future']):
        run('invalid-palette-' + str(index), {'FW_PALETTE': value}, diagnostic='FW_PALETTE must be exactly legacy or v1')
    for index, value in enumerate(['TRUE', '1', '', 'false ']):
        run('invalid-boolean-' + str(index), {'FW_RESTYLE_RECOVERY': value}, diagnostic='FW_RESTYLE_RECOVERY must be exactly true or false')
    run('unknown-surface', {'FW_RESTYLE_RECOVREY': 'true'}, diagnostic='Unknown appearance flags: FW_RESTYLE_RECOVREY')
    # Restore the default compiled configuration and run the whole common suite.
    run('final-default', {}, expected('legacy', off), unit=True, full=True)


if __name__ == '__main__':
    main()
