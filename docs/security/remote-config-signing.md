# Signed remote configuration

From 4.3.2 the app uses a configuration file fetched from
`https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/` only when
`remote-config/remote-config-manifest.json` lists exactly its SHA-256 and the manifest's Ed25519
signature verifies with the public key compiled into the app. This covers the chain list (node URLs),
the type registries that decide how calls are encoded, the feature switches, the Polkaswap settings, the
dApp list, fiat and version-support files, onboarding and the phishing list. Images are not signed.

## After every change under `remote-config/`

1. Commit the configuration change as before: update its `ownedSha256` in
   `config/remote-config-imports.json` and add a `changes` line.
2. On the machine that holds the signing key, sign the manifest:

   ```
   python3 scripts/sign-remote-config.py sign
   python3 scripts/sign-remote-config.py verify
   ```

   `sign` hashes every configuration file, raises the sequence by one and rewrites
   `remote-config/remote-config-manifest.json`. Commit that file together with the change.
3. Run the gates. `verify-owned-remote-config.py` fails while the manifest is stale or unsigned:

   ```
   python3 scripts/test-remote-config-signing.py
   python3 scripts/test-owned-remote-config.py
   python3 scripts/verify-owned-remote-config.py
   ```

4. Merge to `master` in one commit or pull request, so the files and the manifest go public together.
   Until the new manifest is public, 4.3.2+ clients keep the last configuration that verified.

A change merged without a new signature does not reach 4.3.2+ clients; they keep the last verified copy,
or the snapshot bundled into their APK. Older clients read the same URLs without checking and are not
affected by the manifest. Never move or rename a published file: installed clients still read it.

This includes emergency vetoes in `feature_toggle`: sign them like any other change. An unsigned veto
reaches only clients before 4.3.2, and a device that saw it there loses it after updating to 4.3.2 if the
verified copy it then reads says `true`.

## The key

- Private key: `~/.fearless-release/remote-config-signing-ed25519.key`, a 32-byte Ed25519 seed, mode 0600.
  It is never printed, committed or copied into the build. `sign` refuses a key file that other users can
  read and a key that does not match the configured public key.
- Public key: `config/remote-config-signing-key.json` and `RemoteConfigTrust.kt`
  (`common/.../data/network/config/signed`). `verify-owned-remote-config.py` checks they are equal.
  Key id `292d0fd4c8376d1f`.
- `python3 scripts/sign-remote-config.py public-key` prints the public key of the key file.
- The tool uses `scripts/remote_config_ed25519.py`, a standard-library RFC 8032 implementation tested
  against the RFC vectors and libsodium. It is not constant-time: sign only on a machine no other user can
  observe.

Rotating the key needs an app release: generate a new key (`keygen --key <new path>`), put its public key
in both places, sign, and ship the app before publishing manifests signed by the new key. A lost key
cannot be replaced for installed 4.3.2+ apps without a release; they keep their last verified files.

## What the app does

`RemoteConfigInterceptor` (on the shared OkHttp client and the TON API client) checks every request for a
signed path below the URL prefix above:

- The fetched bytes are used when the newest signed manifest lists their SHA-256. They are stored as the
  last verified copy in app-private storage (`files/remote-config-verified`).
- Otherwise (offline, an unsigned or changed file, a missing or invalid manifest) the app uses the last
  verified copy, or else the snapshot bundled into the APK, and the response carries
  `X-Fearless-Remote-Config: last_verified` or `bundled`. Without either, the request fails (HTTP 503).
- A fetched manifest replaces the known one only if its signature verifies and its sequence is not lower,
  so an old signed manifest cannot bring back old files. The manifest bundled with the APK sets the first
  floor.

The bundled snapshot (`bundleRemoteConfigSnapshot` in `common/build.gradle`) copies the files the app
fetches from this repository's `remote-config`, each pinned to its `ownedSha256`; the build fails if a
file differs from its pin. `test-owned-remote-config.py` fails when the app fetches a configuration URL that
is not bundled. `production/chains/xn.json` is read by the xnetworking client, not OkHttp, and is not
checked at runtime.
