#!/usr/bin/env python3
"""Validate Android-owned configuration inventory, URL closure and provenance."""
import hashlib
import json
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

PREFIX = 'https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/'
LEGACY = ('shared-features-utils', 'raw.githubusercontent.com/soramitsu/fearless-utils/', 'cdn.jsdelivr.net/gh/soramitsu/fearless-utils@')
PROVENANCE_ASSETS = {
    'runtime/src/main/assets/canonical-asset-groups.v1.json',
    'runtime/src/main/assets/default-visible-at-zero.v1.json',
    'runtime/src/main/assets/testnet-chain-ids.v1.json',
}


def values(value):
    if isinstance(value, dict):
        for child in value.values():
            yield from values(child)
    elif isinstance(value, list):
        for child in value:
            yield from values(child)
    elif isinstance(value, str):
        yield value


def safe_path(root, relative):
    if not relative or relative.startswith('/') or any(p in {'', '.', '..'} for p in relative.split('/')):
        raise ValueError('Invalid config path: ' + relative)
    path = root
    for part in relative.split('/'):
        path = path / part
        if path.is_symlink():
            raise ValueError('Symlink in config path: ' + relative)
    if not path.is_file():
        raise ValueError('Missing config file: ' + relative)
    return path


def verify_consumers(root, listed):
    paths = set()
    for pattern in ('*.gradle', '*/build.gradle', '*/build.gradle.kts', '*/src/main/**/*.kt', '*/src/main/**/*.java', '*/src/main/assets/**/*.json'):
        paths.update(path for path in root.glob(pattern) if path.is_file())
    for path in paths:
        text = path.read_text()
        if path.suffix == '.json':
            document = json.loads(text)
            # These snapshots name their historical input; the URL is metadata,
            # never a request target. Only this exact field is exempted.
            if path.relative_to(root).as_posix() in PROVENANCE_ASSETS:
                registry = document.get('registry', {})
                registry.pop('source', None)
            urls = values(document)
        else:
            urls = re.findall(r"https?://[^\s\"'\\<>]+", text)
        for value in urls:
            if value.startswith(('http://', 'https://')) and any(old in value for old in LEGACY):
                raise ValueError('Legacy runtime consumer URL in ' + str(path) + ': ' + value)
        for value in re.findall(re.escape(PREFIX) + r"[^\s\"'\\<>]+", text):
            suffix = unquote(value[len(PREFIX):])
            if suffix not in listed:
                raise ValueError('Unlisted consumer URL in ' + str(path) + ': ' + value)


def verify(root):
    manifest = json.loads(safe_path(root, 'config/remote-config-imports.json').read_text())
    if set(manifest) != {'schemaVersion', 'ownership', 'sources', 'changes'} or manifest['schemaVersion'] != 1 or manifest['ownership'] != 'Android application':
        raise ValueError('Invalid config import manifest')
    directory = root / 'remote-config'
    if directory.is_symlink() or not directory.is_dir():
        raise ValueError('Config directory must be app-owned regular source')
    expected = set()
    listed = {entry['path'] for entry in manifest['sources']}
    for entry in manifest['sources']:
        if set(entry) != {'path', 'repository', 'commit', 'sourcePath', 'upstreamSha256', 'ownedSha256'}:
            raise ValueError('Invalid provenance entry')
        if not re.fullmatch(r'soramitsu/[A-Za-z0-9_.-]+', entry['repository']) or not re.fullmatch(r'[0-9a-f]{40}', entry['commit']):
            raise ValueError('Invalid provenance revision')
        if not all(re.fullmatch(r'[0-9a-f]{64}', entry[k]) for k in ('upstreamSha256', 'ownedSha256')):
            raise ValueError('Invalid provenance digest')
        relative = entry['path']
        if relative in expected:
            raise ValueError('Duplicate config file: ' + relative)
        expected.add(relative)
        data = safe_path(directory, relative).read_bytes()
        if hashlib.sha256(data).hexdigest() != entry['ownedSha256']:
            raise ValueError('Config digest changed: ' + relative)
        if relative.endswith('.json') or relative.endswith('/feature_toggle'):
            document = json.loads(data)
            for value in values(document):
                if value.startswith(('https://', 'http://')) and any(old in value for old in LEGACY):
                    raise ValueError('Legacy config host: ' + value)
                if value.startswith(PREFIX):
                    suffix = unquote(urlsplit(value).path.split('/remote-config/', 1)[1])
                    if suffix not in listed:
                        raise ValueError('Unlisted case-sensitive asset URL: ' + value)
                    safe_path(directory, suffix)
    actual = set()
    for path in directory.rglob('*'):
        if path.is_symlink():
            raise ValueError('Config inventory contains a symlink')
        if path.is_file():
            actual.add(path.relative_to(directory).as_posix())
    if expected != actual:
        raise ValueError('Config inventory differs from manifest')
    verify_consumers(root, listed)
    return len(actual)


def main():
    try:
        count = verify(Path(__file__).resolve().parents[1])
    except (ValueError, KeyError, TypeError, OSError) as error:
        print('[owned-config][error] ' + str(error), file=sys.stderr)
        return 1
    print('[owned-config] Verified ' + str(count) + ' owned files and nested asset URLs')
    return 0


if __name__ == '__main__':
    sys.exit(main())
