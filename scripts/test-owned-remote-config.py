#!/usr/bin/env python3
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('owned_config', ROOT / 'scripts/verify-owned-remote-config.py')
VERIFY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY)


class OwnedConfigTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'config').mkdir()
        (self.root / 'remote-config').mkdir()
        self.entries = []
        self.put('icon.svg', b'<svg/>')
        self.put('chains.json', json.dumps({'icon': VERIFY.PREFIX + 'icon.svg'}).encode())
        self.save()

    def tearDown(self):
        self.tmp.cleanup()

    def put(self, path, data):
        (self.root / 'remote-config' / path).write_bytes(data)
        self.entries = [entry for entry in self.entries if entry['path'] != path]
        digest = hashlib.sha256(data).hexdigest()
        self.entries.append({'path': path, 'repository': 'soramitsu/fixture', 'commit': 'a' * 40, 'sourcePath': path, 'upstreamSha256': digest, 'ownedSha256': digest})

    def save(self):
        (self.root / 'config/remote-config-imports.json').write_text(json.dumps({'schemaVersion': 1, 'ownership': 'Android application', 'sources': self.entries, 'changes': []}))

    def rejected(self):
        with self.assertRaises((ValueError, OSError)):
            VERIFY.verify(self.root)

    def test_closed_inventory(self):
        self.assertEqual(2, VERIFY.verify(self.root))

    def test_modified_bytes(self):
        (self.root / 'remote-config/icon.svg').write_text('changed')
        self.rejected()

    def test_unlisted_file(self):
        (self.root / 'remote-config/extra').write_text('extra')
        self.rejected()

    def test_missing_file(self):
        (self.root / 'remote-config/icon.svg').unlink()
        self.rejected()

    def test_duplicate_entry(self):
        self.entries.append(self.entries[0].copy())
        self.save()
        self.rejected()

    def test_broken_case_sensitive_url(self):
        self.put('chains.json', json.dumps({'icon': VERIFY.PREFIX + 'ICON.svg'}).encode())
        self.save()
        self.rejected()

    def test_url_traversal(self):
        self.put('chains.json', json.dumps({'icon': VERIFY.PREFIX + '%2e%2e/config/remote-config-imports.json'}).encode())
        self.save()
        self.rejected()

    def test_legacy_host(self):
        self.put('chains.json', b'{"icon":"https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/icon.svg"}')
        self.save()
        self.rejected()

    def test_symlink(self):
        (self.root / 'remote-config/icon.svg').unlink()
        (self.root / 'remote-config/icon.svg').symlink_to(self.root / 'remote-config/chains.json')
        self.rejected()

    def test_symlinked_manifest(self):
        path = self.root / 'config/remote-config-imports.json'
        data = path.read_bytes()
        path.unlink()
        (self.root / 'manifest.json').write_bytes(data)
        path.symlink_to(self.root / 'manifest.json')
        self.rejected()

    def test_invalid_json(self):
        self.put('chains.json', b'{')
        self.save()
        self.rejected()

    def test_missing_consumer_asset(self):
        (self.root / 'build.gradle').write_text('url = "' + VERIFY.PREFIX + 'missing.svg"')
        self.rejected()

    def test_valid_consumer_asset(self):
        (self.root / 'build.gradle').write_text('url = "' + VERIFY.PREFIX + 'icon.svg"')
        self.assertEqual(2, VERIFY.verify(self.root))

    def test_legacy_runtime_consumer_rejected(self):
        path = self.root / 'core-db/src/main/java/Seeds.kt'
        path.parent.mkdir(parents=True)
        path.write_text('val icon = "https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/icon.svg"')
        self.rejected()

    def test_historical_source_exception_does_not_allow_active_asset_url(self):
        path = self.root / 'runtime/src/main/assets/canonical-asset-groups.v1.json'
        path.parent.mkdir(parents=True)
        old = 'https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json'
        path.write_text(json.dumps({'registry': {'source': old}}))
        self.assertEqual(2, VERIFY.verify(self.root))
        path.write_text(json.dumps({'registry': {'source': old}, 'icon': old}))
        self.rejected()

    def test_imported_repository(self):
        self.assertGreater(VERIFY.verify(ROOT), 600)

    def test_lcdot_general_key_matches_five_byte_currency_id(self):
        for environment in ('production', 'development'):
            data = json.loads((ROOT / 'remote-config' / environment / 'xcm/v2/xcm_token_locations.json').read_text())
            matches = [asset for chain in data for asset in chain['assets'] if asset['symbol'] == 'lcDOT' and 'v3' in asset['versions']]
            self.assertEqual(1, len(matches))
            key = next(interior['generalKeyV3'] for interior in matches[0]['interiors'] if 'generalKeyV3' in interior)
            self.assertEqual(5, key['length'])
            self.assertEqual(bytes.fromhex('040d000000'), bytes.fromhex(key['data'][2:])[:key['length']])



if __name__ == '__main__':
    unittest.main()
