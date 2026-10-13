#!/usr/bin/env python3
"""Validate both immutable runtime sources without any network or artifact mutation."""
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class RuntimePinsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'scripts').mkdir()
        (self.root / 'config').mkdir()
        for name in ('ensure-fearless-utils.sh', 'verify-android-runtime-sources.sh'):
            shutil.copy2(ROOT / 'scripts' / name, self.root / 'scripts' / name)
        self.pins = {'schemaVersion': 1}
        for name, directory, repo in [('utils', 'fearless-utils-Android', 'soramitsu/fearless-utils-Android'),
                                     ('websocket', 'fearless-nv-websocket-client', 'soramitsu/fearless-nv-websocket-client')]:
            source = self.root / directory
            source.mkdir()
            self.git(source, 'init', '-q')
            self.git(source, 'config', 'user.name', 'Source Test')
            self.git(source, 'config', 'user.email', 'test@example.invalid')
            (source / 'source.txt').write_text('reviewed source\n')
            self.git(source, 'add', '.')
            self.git(source, 'commit', '-qm', 'fixture')
            self.git(source, 'remote', 'add', 'origin', 'https://github.com/' + repo + '.git')
            self.pins[name] = {'repository': repo, 'commit': self.git(source, 'rev-parse', 'HEAD'),
                               'tree': self.git(source, 'rev-parse', 'HEAD^{tree}')}
        self.save()

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, directory, *args):
        return subprocess.check_output(['git', '-C', str(directory), *args], text=True).strip()

    def save(self):
        (self.root / 'config/android-runtime-source-pins.json').write_text(json.dumps(self.pins))

    def verify(self, expected=True):
        result = subprocess.run(['bash', str(self.root / 'scripts/verify-android-runtime-sources.sh')],
                                capture_output=True, text=True, env={'PATH': '/usr/bin:/bin:/usr/local/bin:/opt/homebrew/bin'})
        self.assertEqual(result.returncode == 0, expected, result.stdout + result.stderr)

    def test_exact_both_sources_are_read_only(self):
        self.verify()
        for directory in ('fearless-utils-Android', 'fearless-nv-websocket-client'):
            self.assertEqual('', self.git(self.root / directory, 'status', '--porcelain'))

    def test_wrong_transport_commit_is_denied(self):
        self.pins['websocket']['commit'] = '0' * 40
        self.save()
        self.verify(False)

    def test_wrong_transport_tree_is_denied(self):
        self.pins['websocket']['tree'] = '0' * 40
        self.save()
        self.verify(False)

    def test_transport_source_mutation_is_denied(self):
        (self.root / 'fearless-nv-websocket-client/source.txt').write_text('changed')
        self.verify(False)

    def test_utils_source_mutation_is_denied(self):
        (self.root / 'fearless-utils-Android/source.txt').write_text('changed')
        self.verify(False)

    def test_wrong_transport_owner_is_denied(self):
        self.git(self.root / 'fearless-nv-websocket-client', 'remote', 'set-url', 'origin', 'https://github.com/other/repo.git')
        self.verify(False)

    def test_source_symlink_is_denied(self):
        p = self.root / 'fearless-nv-websocket-client/source.txt'
        p.unlink()
        p.symlink_to(self.root / 'fearless-utils-Android/source.txt')
        self.verify(False)

    def test_unknown_pin_fields_are_denied(self):
        self.pins['websocket']['fallback'] = True
        self.save()
        self.verify(False)

    def test_missing_transport_is_denied(self):
        shutil.rmtree(self.root / 'fearless-nv-websocket-client')
        self.verify(False)

if __name__ == '__main__':
    unittest.main()
