# Android source ownership and dependency audit

Android owns its runtime, UI, networking, chain, XCM and backup modules directly.
All internal dependencies use Gradle `project(...)`; no internal Maven artifact,
Git source-control fallback, sibling checkout or cross-platform shared package is
needed. Existing Kotlin package names and serialized wallet formats are retained.

| Module | Origin and current contract |
| --- | --- |
| runtime-utils | Imported source/license from soramitsu/fearless-utils-Android 3d22dad159ca5304d199acd08c5a622dea3c1577, tree 165380bed84bb1dd69f868660e57f9ebca899932 |
| runtime-websocket | Imported source/license from soramitsu/fearless-nv-websocket-client 18a94e2db0f67a8387414bc7db53aac48ded18d7, tree e5bd65d129bd50463eeeb41d8239b28ed2a3dc1f |
| core-foundation, core-ui, core-networking | Existing application-owned APIs, now direct project dependencies |
| core-chain, core-xcm, core-backup | Existing Android chain, XCM and backup implementation, now direct project dependencies |

The runtime production source is unchanged from the reviewed imports. Gradle
configuration is app-native; upstream sample/publishing/Rust Gradle machinery is
not imported. The owned Rust source, Cargo.lock and 16 KB linker configuration are
available for intentional rebuilds. The eight existing native binaries and their
hashes are unchanged. The existing upstream Polkaj SCALE JAR is imported byte for
byte; binary provenance covers ten artifacts including it and the Gradle wrapper.
Third-party Maven dependencies retain dependency locks and verification metadata.
The optional local staged Iroha developer path remains separately guarded; CI and
release builds reject local repository resolution. Private Nexus is removed.

`config/android-runtime-source-pins.json` records original repository/commit/tree
and every current source file digest. `scripts/verify-android-runtime-sources.sh`
checks a complete, symlink-free owned inventory before and after Gradle. The
release guard binds the full clean application commit/tree; separate runtime
checkouts are forbidden. Legacy FEARLESS_UTILS_COMMIT/EFFECTIVE_TREE receipt names
are kept only for release/IAS protocol compatibility and identify original import
provenance, not a separate source authority.

`remote-config` includes production/development catalogs, runtime types, XCM,
feature switches, Android app support, fiat and artwork. Its import manifest
records reviewed route retirement and lcDOT GeneralKey length five, MWR-819's
active xn.json, original and owned digests, and explicit artwork fallback changes.
Frozen historical migration fixture URLs are deliberately unchanged. The legacy
host must remain available to installed clients. Current Android URLs require
this change to land on public master before distribution.

Verification commands:

```
python3 scripts/test-android-runtime-source-pins.py
bash scripts/verify-android-runtime-sources.sh
python3 scripts/test-owned-remote-config.py
python3 scripts/verify-owned-remote-config.py
bash scripts/test-public-dependency-upstream-delta-export.sh
bash scripts/test-public-artifact-provenance-audit.sh
bash scripts/audit-public-artifacts.sh --strict-provenance
./gradlew runTest detektAll :app:assembleDebug :app:lintDebug
```

XCM remains disabled in release: approved/required executable routes are 0/0;
27 discovery destinations represent 39 assets. No funded network writes are part
of consolidation tests. Imported runtime integration tests that submit extrinsics
are excluded from host unit runs; device/native ABI, release signing, store
publication and funded per-route qualification are separate release gates.
