#!/usr/bin/env python3
"""Sign the remote-config manifest that 4.3.2+ apps verify before using a fetched configuration file.

Commands (run from any directory; paths are resolved from this repository):
  keygen    Create the Ed25519 signing key (mode 0600) and print its public key. Refuses to overwrite a key.
  sign      Hash every protected file under remote-config/, sign the manifest and write
            remote-config/remote-config-manifest.json. The sequence grows by one on every signing.
  verify    Check the committed manifest: signature, key, and that it lists exactly the current protected files.
  public-key  Print the public key of the signing key.

The private key never leaves the key file and is never printed. See docs/security/remote-config-signing.md.
"""
import argparse
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import stat
import sys

ROOT = Path(__file__).resolve().parents[1]
# Loading a sibling script must not leave scripts/__pycache__/, which the release source-tree check refuses.
sys.dont_write_bytecode = True
_SPEC = importlib.util.spec_from_file_location('remote_config_ed25519', Path(__file__).resolve().parent / 'remote_config_ed25519.py')
ED25519 = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(ED25519)

DEFAULT_KEY = Path.home() / '.fearless-release' / 'remote-config-signing-ed25519.key'
KEY_CONFIG = 'config/remote-config-signing-key.json'
MANIFEST = 'remote-config-manifest.json'
SCHEMA = 'fearless-remote-config-manifest/1'
REPOSITORY = 'soramitsu/fearless-Android'
# Images are shown, never interpreted; everything else under remote-config/ is configuration and is signed.
UNSIGNED_SUFFIXES = ('.svg', '.png', '.jpg', '.jpeg', '.gif', '.webp')


class ManifestError(ValueError):
    pass


def key_id(public):
    return hashlib.sha256(public).hexdigest()[:16]


def protected_files(root):
    directory = root / 'remote-config'
    files = {}
    for path in sorted(directory.rglob('*')):
        if path.is_symlink():
            raise ManifestError('Symlink in remote-config: ' + str(path))
        if not path.is_file():
            continue
        relative = path.relative_to(directory).as_posix()
        if relative == MANIFEST or relative.lower().endswith(UNSIGNED_SUFFIXES):
            continue
        files[relative] = hashlib.sha256(path.read_bytes()).hexdigest()
    return files


def canonical(document):
    return json.dumps(document, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode('ascii')


def configured_public_key(root):
    config = json.loads((root / KEY_CONFIG).read_text())
    if set(config) != {'algorithm', 'publicKey', 'keyId'} or config['algorithm'] != 'ed25519':
        raise ManifestError('Invalid ' + KEY_CONFIG)
    public = bytes.fromhex(config['publicKey'])
    if len(public) != ED25519.PUBLIC_KEY_SIZE or config['keyId'] != key_id(public):
        raise ManifestError('Invalid public key in ' + KEY_CONFIG)
    return public


def read_manifest(root, public):
    """Returns the verified inner manifest of the committed signed manifest."""
    path = root / 'remote-config' / MANIFEST
    if path.is_symlink() or not path.is_file():
        raise ManifestError('Missing signed manifest remote-config/' + MANIFEST)
    outer = json.loads(path.read_text())
    if set(outer) != {'algorithm', 'manifest', 'signature'} or outer['algorithm'] != 'ed25519':
        raise ManifestError('Invalid signed manifest envelope')
    signed = outer['manifest'].encode('ascii')
    signature = bytes.fromhex(outer['signature'])
    if not ED25519.verify(public, signed, signature):
        raise ManifestError('Remote-config manifest signature is invalid')
    manifest = json.loads(signed)
    if set(manifest) != {'files', 'keyId', 'repository', 'schema', 'sequence', 'signedAt'} or manifest['schema'] != SCHEMA:
        raise ManifestError('Invalid manifest fields')
    if manifest['repository'] != REPOSITORY or manifest['keyId'] != key_id(public):
        raise ManifestError('Manifest is for another repository or key')
    if not isinstance(manifest['sequence'], int) or manifest['sequence'] < 1:
        raise ManifestError('Invalid manifest sequence')
    if canonical(manifest) != signed:
        raise ManifestError('Manifest is not in canonical form')
    return manifest


def verify(root):
    public = configured_public_key(root)
    manifest = read_manifest(root, public)
    current = protected_files(root)
    if manifest['files'] != current:
        stale = sorted(set(manifest['files'].items()) ^ set(current.items()))
        raise ManifestError('Signed manifest is stale for: ' + ', '.join(sorted({path for path, _ in stale})))
    return manifest


def read_seed(key_path):
    info = key_path.lstat()
    if stat.S_ISLNK(info.st_mode) or not stat.S_ISREG(info.st_mode):
        raise ManifestError('The signing key must be a regular file')
    if info.st_mode & 0o077:
        raise ManifestError('The signing key file must have mode 0600')
    seed = key_path.read_bytes()
    if len(seed) != ED25519.SEED_SIZE:
        raise ManifestError('The signing key file must hold a 32-byte Ed25519 seed')
    return seed


def keygen(key_path):
    if key_path.exists() or key_path.is_symlink():
        raise ManifestError('A signing key already exists at ' + str(key_path) + '; it is never overwritten')
    key_path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    seed = os.urandom(ED25519.SEED_SIZE)
    descriptor = os.open(key_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        os.write(descriptor, seed)
    finally:
        os.close(descriptor)
    os.chmod(key_path, 0o600)
    return ED25519.public_key(seed)


def sign(root, key_path, sequence=None, now=None):
    seed = read_seed(key_path)
    public = ED25519.public_key(seed)
    if public != configured_public_key(root):
        raise ManifestError('The signing key does not match ' + KEY_CONFIG)
    previous = 0
    if (root / 'remote-config' / MANIFEST).exists():
        previous = read_manifest(root, public)['sequence']
    next_sequence = previous + 1 if sequence is None else sequence
    if next_sequence <= previous:
        raise ManifestError('The sequence must grow: the committed manifest has ' + str(previous))
    signed_at = (now or datetime.datetime.now(datetime.timezone.utc)).strftime('%Y-%m-%dT%H:%M:%SZ')
    manifest = {
        'files': protected_files(root),
        'keyId': key_id(public),
        'repository': REPOSITORY,
        'schema': SCHEMA,
        'sequence': next_sequence,
        'signedAt': signed_at,
    }
    signed = canonical(manifest)
    envelope = {'algorithm': 'ed25519', 'manifest': signed.decode('ascii'), 'signature': ED25519.sign(seed, signed).hex()}
    (root / 'remote-config' / MANIFEST).write_text(json.dumps(envelope, indent=1, sort_keys=True) + '\n')
    return manifest


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('command', choices=['keygen', 'sign', 'verify', 'public-key'])
    parser.add_argument('--key', type=Path, default=DEFAULT_KEY, help='Ed25519 seed file (default: %(default)s)')
    parser.add_argument('--sequence', type=int, help='explicit manifest sequence (default: previous + 1)')
    parser.add_argument('--root', type=Path, default=ROOT, help=argparse.SUPPRESS)
    arguments = parser.parse_args(argv)
    try:
        if arguments.command == 'keygen':
            public = keygen(arguments.key)
            print(json.dumps({'algorithm': 'ed25519', 'publicKey': public.hex(), 'keyId': key_id(public)}, indent=1))
        elif arguments.command == 'public-key':
            public = ED25519.public_key(read_seed(arguments.key))
            print(json.dumps({'algorithm': 'ed25519', 'publicKey': public.hex(), 'keyId': key_id(public)}, indent=1))
        elif arguments.command == 'sign':
            manifest = sign(arguments.root, arguments.key, arguments.sequence)
            print('[remote-config] Signed sequence ' + str(manifest['sequence']) + ' over ' + str(len(manifest['files'])) + ' files')
        else:
            manifest = verify(arguments.root)
            print('[remote-config] Signed manifest sequence ' + str(manifest['sequence']) + ' covers ' + str(len(manifest['files'])) + ' files')
    except (ManifestError, OSError, ValueError, KeyError) as error:
        print('[remote-config][error] ' + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
