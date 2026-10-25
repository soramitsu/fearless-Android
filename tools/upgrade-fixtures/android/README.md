# Android upgrade fixtures

Tools that turn the official Fearless Android releases into test fixtures. The
upgrade-path test matrix (TM-A1, TM-A3) uses them to prove that no user loses a
wallet, a key or a preference whatever version they upgrade from.

APKs are never committed. Only synthetic fixtures and vectors are.

## Release inventory and Room schema fixtures (TM-A1)

`releases.json` lists every build distributed as `jp.co.soramitsu.fearless`:
all GitHub release assets (sha256 and signer pinned) and the Play-only tags.
For each one it records the tag commit, the Room schema version the build left
on a device, its identity hash, minSdk, the crypto library, and the evidence
for its schema. `fixtures` maps every release to exactly one schema fixture
under `core-db/src/androidTest/assets/migrations/<release>/`.

```sh
# 1. Download (or verify) the APKs into a local cache.
tools/upgrade-fixtures/android/fetch_release_apks.sh ~/fearless-apk-cache
tools/upgrade-fixtures/android/fetch_release_apks.sh ~/fearless-apk-cache --verify-only

# 2. Regenerate the fixtures, or check that the committed ones are current.
tools/upgrade-fixtures/android/extract_room_schema.py generate --apk-cache ~/fearless-apk-cache
tools/upgrade-fixtures/android/extract_room_schema.py check --apk-cache ~/fearless-apk-cache \
    --all-apks --git .

# One APK, for investigation.
tools/upgrade-fixtures/android/extract_room_schema.py extract some.apk

# Tests that need no APK (inventory consistency, parsers, committed fixtures).
python3 tools/upgrade-fixtures/android/test_extract_room_schema.py
```

`extract_room_schema.py` runs `dexdump -d` (build-tools 36.0.0 by default; set
`DEXDUMP` or `ANDROID_HOME`) and reads the SQL that Room's generated
`createAllTables` executes, plus the version and identity hash passed to Room.
It handles the three shapes that shipped: plain Room 2.x (1.x/2.0 builds), R8
builds that renamed `SupportSQLiteDatabase` (2.1.0-3.1.2), and the Room 2.7
open delegate (4.2.0). `check --all-apks` re-extracts all 72 APKs and fails if
any pinned hash, Room version or identity hash differs, or if a regenerated
fixture is not byte-identical. `--git` also checks each tag's commit and the
Room version its sources declare.

Where no APK exists the fixture records why its schema is still exact:

| Room | Releases | Source |
| --- | --- | --- |
| 9 | 0.4.0-1.0.1 | No APK; `ReleasedV04DatabaseFixture` rebuilds it from the tag v0.4.0 entities |
| 54 | 2.2.3 | 2.2.4 APK DDL without the `address_book` index `Migration_54_55` adds (the only entity change) |
| 73 | 4.0.1, 4.0.2 | `core-db/schemas/.../73.json`; its tables that did not change since 3.7.4 equal the 3.7.4 APK |
| 74 | 4.1.1 | `core-db/schemas/.../74.json`, identical to the file exported at tag v4.2.0 |

Play-only releases that share a schema with a GitHub release are mapped to it
only when their `core-db` sources (or at least their Room entities and
`@Database` list) and their Room/Kotlin toolchain are identical. The toolchain
matters: 1.9.12-1.9.13 (Kotlin 1.4.31) and 1.9.14-1.9.15 (Kotlin 1.6.10) ship
the same Room 27 identity hash with a different physical column order in
`nodes`, so DB 27 has two fixtures.

`AllReleasedSchemasUpgradeTest` (core-db androidTest) builds a database from
every fixture, inserts era-typical synthetic rows and secrets, upgrades it
through `AppDatabase.create`, and checks the wallets, signing material and
wallet-owned rows twice (schema-scoped U1, U2, U5, U8). The second open stays
in the same process with the same in-memory preferences; it does not prove
U9 interrupted-process recovery or U7 startup UI. The separate shipped
migration-script fixture uses `4.x-shipped/migration-manifest.json` so it
cannot enter released-schema discovery. `ReleasedSchemaFixtureCoverageTest`
fails if a shipped Room version has no fixture.

## Crypto golden vectors (TM-A3)

`convert_parity_vectors.py` converts the AL-4 production-parity vectors
(shared-features 1.1.1.44-FLW, the library 4.1.1 and 4.2.0 signed with; see
the root `docs/android-production-parity-oracle.md`) into test resources:

- `common/src/test/resources/crypto-golden/<library>.json`:
  `HistoricalCryptoGoldenVectorTest`, `HistoricalTonPhraseAcceptanceTest` (JVM)
- `common/src/test/resources/crypto-golden/secret-encodings-<library>.json`:
  `HistoricalSecretEncodingAcceptanceTest`, `HistoricalTonPhraseAcceptanceTest` (JVM)
- `core-db/src/androidTest/assets/crypto-golden/<library>-sr25519.json`:
  `HistoricalSr25519GoldenVectorTest`, which needs the shipped native sr25519
  library that core-db packages for its instrumentation tests

```sh
node tools/upgrade-fixtures/android/generate_address_anchors.mjs \
    <fearless-wallet-web checkout> <al4-dir>/05-keypairs.json > anchors.json
tools/upgrade-fixtures/android/convert_parity_vectors.py --al4 <al4-dir> --anchors anchors.json [--check]
```

The anchors are SS58 (prefixes 0, 2, 42, 69) and EIP-55 encodings produced by
polkadot-js, an implementation unrelated to both Fearless libraries.

Two findings the vectors recorded are fixed, and the tests that listed them
as expected rejections now require acceptance:

- `HistoricalTonPhraseAcceptanceTest.validatorAcceptsHistoricalTonSecretsUnchanged`:
  every stored TON phrase, typed variants included, passes the production
  validator unchanged and is assessed as verified as typed (A4, K1).
- `HistoricalSr25519GoldenVectorTest.productionValidatorsAcceptStoredSr25519SecretsUnchanged`:
  every stored sr25519 secret, soft paths included, passes unchanged with its
  recovery material verified (F5, AL-5).

Older libraries (fearless-utils 1.0.96-1.0.109, earlier shared-features) come
from the TM-A2 binary oracle; add their files in the same layout and list them
in `CryptoGoldenVectors.LIBRARIES`.
