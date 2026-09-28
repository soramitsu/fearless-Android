# Iroha Production Send Readiness (Android)

Status: **BLOCKED / fail closed**. This is not an implemented production-send
claim. SORA Nexus remains `enabledByDefault = false`, and
`TransferServiceProvider` still injects `UnavailableIrohaTransferSigner` unless
a caller explicitly supplies a test signer. The staged bridge is not an app or
production runtime dependency.

## Pinned upstream evidence

The pinned integration target is the official Hyperledger Iroha release
[`v2.0.0-rc.2.1-fearless-mobile-sdk.3`](https://github.com/hyperledger-iroha/iroha/releases/tag/v2.0.0-rc.2.1-fearless-mobile-sdk.3),
published on 2026-07-26. Its only Android SDK delivery is:

- artifact: `iroha-mobile-sdk-android-v2.0.0-rc.2.1-fearless-mobile-sdk.3.zip`
- SHA-256: `24bb47552977cc2610512f59b8bccfd0b047b179302ed972600a66ddc1a01de6`
- release-manifest size: `116992564` bytes
- contents: raw `core-jvm`, `client-android`, and `offline-wallet-android`
  artifacts, a local Maven repository, and native
  `libconnect_norito_bridge.so` slices

The archive is larger than the repository's `100000000`-byte mandatory review
threshold. Its structural and dependency review is now complete for the narrow
`core-jvm` transfer path. `scripts/materialize-iroha-core-jvm.sh` verifies the
exact outer size and SHA-256, applies bounded outer and nested ZIP validation,
and atomically extracts only this coordinate into ignored build output:

```text
org.hyperledger.iroha.sdk:core-jvm:2.0.0-rc.2.1-fearless-mobile-sdk.3
```

The materialized core JAR SHA-256 is
`33f449700948641c73eeaa4e4a8ab9e39d3d6a642a50b6b7d8c30a9f6aff19ce`.
The adversarial materializer suite covers 68 cross-platform negative scenarios
plus 2 macOS filesystem-alias cases (70 on macOS). It rejects
client/offline AARs, native bridges, unsafe paths and types, duplicate or
encrypted entries, unsupported methods, CRC errors, bombs, races, unsafe
output roots, incomplete checksums, and extra artifacts. No SDK binary is
vendored, and ordinary builds never download the archive implicitly.

## Staged core bridge

`iroha-sdk-bridge` is a Java-only, test-stage boundary around the reviewed core
JAR. Its public API exposes no SDK, Kotlin, or provider types. The core JAR is a
non-transitive implementation dependency, Kotlin stays at 2.1.10, and the
three-file runtime graph is locked, checksum-pinned, and scanned for native
content before Java compilation. A separate Kotlin 2.1 smoke module proves the
SDK's Kotlin 2.3 metadata does not enter a Kotlin compile classpath. App debug
and release runtime graphs are resolved and checked to exclude both staged
modules and `org.hyperledger.iroha.sdk`.

The bridge is deliberately Taira-only. It requires canonical Taira I105 input,
binds the authority controller to the derived Ed25519 key, creates a wire-framed
transfer with the upstream builder, signs the upstream Iroha prehash, emits
versioned Norito, and computes the current source entrypoint hash. Nexus, multisig,
non-Ed25519, key mismatch, malformed amount, legacy asset aliases, and
noncanonical inputs fail closed. Golden, tamper, concurrency, cleanup, and
wrong-network tests run only after explicit materialization:

```bash
bash scripts/verify-staged-iroha-core-bridge.sh --download
```

The SDK-neutral signing request now carries a closed, immutable metadata value.
Ordinary wallet transfers use empty transaction metadata. The only non-empty
shape is the exact four-string Android wallet-smoke metadata contract:

```json
{
  "evidence_role": "wallet-smoke",
  "route_governance_action_hash": "sha256:<64 lowercase hex>",
  "wallet_platform": "android",
  "wallet_commit": "<40 lowercase git hex>"
}
```

The wallet-side factory snapshots input, enforces the exact key set, and rejects
non-string, missing, extra, wrong-case, noncanonical, control-character,
Unicode-confusable, and all-zero sentinel values. Wallet-smoke metadata is
Nexus-only: construction requires `network=nexus` and
`chainId=sora:nexus:global`. The operator-only
`transferWalletSmokeEvidence` seam additionally requires the canonical SORA
Nexus Minamoto endpoint from `UniversalWalletRegistry.nexus`, then passes the
immutable value to the generic signer request. It cannot silently fall back to
Taira or an operator-supplied Torii endpoint, and rejects either route before
accessing the selected account or mnemonic root material.

The staged Java bridge is deliberately Taira-only and is not the Nexus metadata
serializer. It rejects every non-empty transaction metadata map before clock
access, Ed25519 key construction, signing, or submission, including the valid
Nexus wallet-smoke contract. Its Kotlin-consumer test pins the immutable
cross-language snapshot and that early rejection. The bridge never signs Nexus
metadata into a Taira transaction.

The pinned SDK's `JsonValue` is a Kotlin value class without a stable
Java-callable string factory. A reviewed Nexus codec adapter therefore remains
a production blocker; no reflective constructor is used by the Taira bridge.
Upstream must publish a reviewed Java-callable factory, or the Kotlin toolchain
and isolation design must be separately reviewed, before production DI can be
enabled. The explicit wallet-smoke request path does not enable production send:
production DI still selects `UnavailableIrohaTransferSigner`, Nexus remains
disabled by default, and the staged bridge remains outside the app runtime graph.

The deterministic release-tag fixture uses canonical asset definition
`61CtjvNd9T3THAR65GsMVHr82Bjc`; it is test data, not a production mapping
target. A 2026-07-11 read of the currently deployed Taira asset-definition
registry reported native XOR as `6TEAJqbb8oEPmLncoNiMRbLEK6tw`. Production
must resolve the authoritative live ID, precision, and fee policy at the
reviewed registry boundary. Neither identifier may be hard-coded as the
production answer.

The exact 565-byte Java golden decodes and re-encodes in the current local
native host; payload prehash and Ed25519 signature parity pass. Separate source
inspection at release-tag commit `4f8cfbdd17aa6a3b049e619f23ec02501e5297b6`
shows that the Rust entrypoint uses a variant-0 `u32`, minimal ULEB128 length,
and the bare signed transaction. Both paths produce
`2332d0004eb24d97fd965fe68f6f31b0e51339764b4dd80f3ea50a3b6f7e5003`,
which the staged bridge's independently reviewed compact hasher now matches.

The pinned Java SDK's `SignedTransactionHasher` is defective: it writes the
bare transaction length as a fixed little-endian `u64` and produces
`2b5e69a0a3d333756f4ac2a54bf7eaff88a2c87484d89e6e7da98abea659662d`.
Production bridge source is forbidden from importing that hasher, while tests
pin its wrong result so an accidental regression is visible. The local native
host's exact build provenance has not been demonstrated. Its match is
diagnostic evidence only; it is neither release-tag binary provenance,
deployed-device proof, nor a funded live Torii receipt. Live Taira receipt
parity remains a release blocker.

The independent standard-library verifier recomputes Blake2b-256 plus the
Iroha prehash marker from the shared 565-byte fixture. Its self-test covers 29
malformed, drifted, fixed-`u64`, and overlong cases plus 11 ULEB128 boundaries;
the same fixture is consumed by the Java golden test.

## Enforced blocker

The machine-readable state is
`config/iroha-production-send-readiness.json`. The blocker code is
`android_staged_bridge_production_gates_required`. Run:

```bash
bash scripts/test-iroha-production-send-readiness-audit.sh
bash scripts/audit-iroha-production-send-readiness.sh
```

The audit fails if the manifest changes without review, Nexus becomes enabled
by default, the unavailable signer is replaced or bypassed, the staged SDK
leaks into production, a native/AAR binary is added, the bridge loses its
canonical asset/key/secret-risk guards, the negative signer tests disappear,
or this evidence loses its exact tag/digest/size markers.

Validating the upstream archive with
`check-iroha-mobile-sdk-release-assets.sh` proves artifact integrity only. It
does **not** make Iroha send production-ready.

## Remaining production blockers

- The release archive has no bundled LICENSE/NOTICE, SBOM, provenance, or
  `core-jvm` sources JAR, and the release tag is not cryptographically signed.
  Source/license/provenance publication and review remain required.
- Wallet assets still use the display alias `xor#sora`; the wire encoder
  requires a canonical Base58 definition. The release-tag fixture uses
  `61CtjvNd9T3THAR65GsMVHr82Bjc`, while the current live Taira registry reports
  `6TEAJqbb8oEPmLncoNiMRbLEK6tw`. A reviewed authoritative registry mapping must
  replace hard-coded test knowledge without hard-coding either ID. Display
  amounts must likewise use live authoritative precision, and fee policy must
  be resolved and tested before signing.
- Bouncy Castle's `Ed25519PrivateKeyParameters` makes an internal private-key
  copy and offers no destruction API. Caller and temporary arrays are wiped,
  but that provider-owned residual copy is a production risk that must be
  accepted or removed.
- Production DI still uses `UnavailableIrohaTransferSigner`. A future wallet
  adapter must derive locally, pass canonical identifiers/amounts, compare the
  local source hash with Torii's receipt, and pass Android release R8/device
  tests.
- The pinned SDK has a confirmed fixed-`u64` transaction-hasher defect. The
  staged compact hasher matches inspected release-tag Rust framing and the
  current local native-host diagnostic, but the host binary's build provenance
  is unproven. A fixed upstream SDK must be published and reviewed, and the
  Android result must match a funded live Taira Torii receipt. The local
  diagnostic is not deployment or live-network evidence.
- The deployed Taira node reports version `2.0.0-rc.2.0` at commit prefix
  `039af2d`, while the staged SDK release tag points at
  `4f8cfbdd17aa6a3b049e619f23ec02501e5297b6`. Wire, hash, registry, and fee
  compatibility across that revision gap has not been proven.
- Nexus stays disabled, and neither network may be enabled for production send
  until funded Taira and Nexus broadcasts are independently recorded.
