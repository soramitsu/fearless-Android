#!/usr/bin/env python3
"""Tests for scripts/sign-remote-config.py and its standard-library Ed25519."""
import datetime
import importlib.util
import json
import os
from pathlib import Path
import stat
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('sign_remote_config', ROOT / 'scripts/sign-remote-config.py')
SIGNING = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SIGNING)
ED25519 = SIGNING.ED25519

# RFC 8032, section 7.1, TEST 1 to 3.
RFC8032 = [
    ('9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60', 'd75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a', '',
     'e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b'),
    ('4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb', '3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c', '72',
     '92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00'),
    ('c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7', 'fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025', 'af82',
     '6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a'),
]


class Ed25519Test(unittest.TestCase):
    def test_rfc8032_vectors(self):
        for seed, public, message, signature in RFC8032:
            self.assertEqual(public, ED25519.public_key(bytes.fromhex(seed)).hex())
            self.assertEqual(signature, ED25519.sign(bytes.fromhex(seed), bytes.fromhex(message)).hex())
            self.assertTrue(ED25519.verify(bytes.fromhex(public), bytes.fromhex(message), bytes.fromhex(signature)))

    def test_rejects_changed_message_signature_or_key(self):
        seed, public, message, signature = (bytes.fromhex(v) for v in RFC8032[1])
        self.assertFalse(ED25519.verify(public, message + b'x', signature))
        self.assertFalse(ED25519.verify(public, message, bytes([signature[0] ^ 1]) + signature[1:]))
        self.assertFalse(ED25519.verify(bytes.fromhex(RFC8032[0][1]), message, signature))
        self.assertFalse(ED25519.verify(public, message, signature[:63]))
        # A non-canonical s (s + L) must not verify.
        s = int.from_bytes(signature[32:], 'little') + ED25519._L
        self.assertFalse(ED25519.verify(public, message, signature[:32] + s.to_bytes(32, 'little')))


class SigningToolTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'config').mkdir()
        (self.root / 'remote-config/production').mkdir(parents=True)
        (self.root / 'remote-config/production/chains.json').write_text('[]')
        (self.root / 'remote-config/production/feature_toggle').write_text('{}')
        (self.root / 'remote-config/icon.svg').write_text('<svg/>')
        self.key = self.root / 'keys' / 'remote-config.key'

    def tearDown(self):
        self.tmp.cleanup()

    def configure(self, public):
        (self.root / SIGNING.KEY_CONFIG).write_text(json.dumps({'algorithm': 'ed25519', 'publicKey': public.hex(), 'keyId': SIGNING.key_id(public)}))

    def test_keygen_creates_a_private_key_once(self):
        public = SIGNING.keygen(self.key)
        self.assertEqual(0o600, stat.S_IMODE(self.key.stat().st_mode))
        self.assertEqual(0o700, stat.S_IMODE(self.key.parent.stat().st_mode))
        self.assertEqual(public, ED25519.public_key(self.key.read_bytes()))
        with self.assertRaises(SIGNING.ManifestError):
            SIGNING.keygen(self.key)

    def test_sign_and_verify(self):
        self.configure(SIGNING.keygen(self.key))
        manifest = SIGNING.sign(self.root, self.key, now=datetime.datetime(2026, 11, 7, tzinfo=datetime.timezone.utc))
        self.assertEqual({'production/chains.json', 'production/feature_toggle'}, set(manifest['files']))
        self.assertEqual(1, manifest['sequence'])
        self.assertEqual('2026-11-07T00:00:00Z', manifest['signedAt'])
        self.assertEqual(manifest, SIGNING.verify(self.root))

    def test_sequence_only_grows(self):
        self.configure(SIGNING.keygen(self.key))
        SIGNING.sign(self.root, self.key)
        self.assertEqual(2, SIGNING.sign(self.root, self.key)['sequence'])
        with self.assertRaises(SIGNING.ManifestError):
            SIGNING.sign(self.root, self.key, sequence=2)
        self.assertEqual(9, SIGNING.sign(self.root, self.key, sequence=9)['sequence'])

    def test_stale_and_unlisted_files_fail_verification(self):
        self.configure(SIGNING.keygen(self.key))
        SIGNING.sign(self.root, self.key)
        (self.root / 'remote-config/production/chains.json').write_text('[1]')
        with self.assertRaises(SIGNING.ManifestError):
            SIGNING.verify(self.root)
        SIGNING.sign(self.root, self.key)
        (self.root / 'remote-config/production/new.json').write_text('{}')
        with self.assertRaises(SIGNING.ManifestError):
            SIGNING.verify(self.root)

    def test_a_key_other_than_the_configured_one_cannot_sign(self):
        SIGNING.keygen(self.key)
        self.configure(ED25519.public_key(bytes(32)))
        with self.assertRaises(SIGNING.ManifestError):
            SIGNING.sign(self.root, self.key)

    def test_a_readable_or_linked_key_is_refused(self):
        public = SIGNING.keygen(self.key)
        self.configure(public)
        os.chmod(self.key, 0o644)
        with self.assertRaises(SIGNING.ManifestError):
            SIGNING.sign(self.root, self.key)
        os.chmod(self.key, 0o600)
        link = self.root / 'link.key'
        link.symlink_to(self.key)
        with self.assertRaises(SIGNING.ManifestError):
            SIGNING.sign(self.root, link)

    def test_a_changed_or_non_canonical_manifest_fails(self):
        self.configure(SIGNING.keygen(self.key))
        SIGNING.sign(self.root, self.key)
        path = self.root / 'remote-config' / SIGNING.MANIFEST
        envelope = json.loads(path.read_text())
        changed = dict(envelope, manifest=envelope['manifest'].replace('"sequence":1', '"sequence":2'))
        path.write_text(json.dumps(changed))
        with self.assertRaises(SIGNING.ManifestError):
            SIGNING.verify(self.root)
        seed = self.key.read_bytes()
        loose = json.dumps(json.loads(envelope['manifest']), indent=1)
        path.write_text(json.dumps(dict(envelope, manifest=loose, signature=ED25519.sign(seed, loose.encode()).hex())))
        with self.assertRaises(SIGNING.ManifestError):
            SIGNING.verify(self.root)

    def test_repository_manifest_verifies(self):
        manifest = SIGNING.verify(ROOT)
        self.assertIn('production/chains/v13/chains.json', manifest['files'])
        self.assertNotIn(SIGNING.MANIFEST, manifest['files'])

    def test_command_line_never_prints_the_seed(self):
        import contextlib
        import io
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(0, SIGNING.main(['keygen', '--key', str(self.key)]))
            self.assertEqual(0, SIGNING.main(['public-key', '--key', str(self.key)]))
        self.assertNotIn(self.key.read_bytes().hex(), output.getvalue())


if __name__ == '__main__':
    unittest.main()
