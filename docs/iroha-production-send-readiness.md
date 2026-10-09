# Iroha Production Send Readiness (Android)

Status: **BLOCKED / fail closed**. This is not an implemented production-send
claim. SORA Nexus remains `enabledByDefault = false`, and
`TransferServiceProvider` still injects `UnavailableIrohaTransferSigner` unless
a caller explicitly supplies a test signer. The staged bridge is not an app or
production runtime dependency.

## Pinned upstream evidence

The pinned integration target is the official Hyperledger Iroha release
[`v2.0.0-rc.2.1-fearless-mobile-sdk.3`](https://github.com/hyperledger/iroha/releases/tag/v2.0.0-rc.2.1-fearless-mobile-sdk.3),
published on 2026-06-25. Its only Android SDK delivery is:

- artifact: `iroha-mobile-sdk-android-v2.0.0-rc.2.1-fearless-mobile-sdk.3.zip`
- SHA-256: `50e37369e3b08e4435f15ae31d1baca28dd55eae82686000df8f39b85feee7e6`
- release-manifest size: `116231129` bytes
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
The adversarial materializer suite covers 63 negative scenarios. It rejects
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

The first-release Taira profile uses protocol chain id
`fc56984b-2be7-431d-840e-21514d1883f0` directly and identifies native XOR by
canonical Base58 definition id `6TEAJqbb8oEPmLncoNiMRbLEK6tw`. The wire,
balance, history, and submission boundaries accept no `name#domain`
asset-definition alias. Torii asset definitions are validated by exact
canonical id and must agree with the canonical native profile: symbol `XOR`,
scale 9, and 9 decimals. The pre-rollout live observation did not expose a
scale, so a live `spec.scale` of 9 must be recorded after rollout before release.

Torii reads retain and validate all six fanout completeness headers: attempted,
succeeded, failed, denied, unavailable, and not-found. If any appears, all six
must appear and prove full success. A partial or malformed successful response
is a typed degraded read and cannot reconcile a missing holding as zero or an
MCP failure as complete empty history. History
quantities accept canonical decimal strings and convert exactly to base units
using the validated asset precision; inexact, negative, or overflowing values
fail closed.

The unreachable submission seam invokes MCP
`iroha.transactions.submit_and_wait` with `body_base64`, the locally computed
canonical odd-marker hash, and `terminal_statuses: ["Applied"]`. It accepts
success only when the top-level hash, `tx_hash`, receipt `entrypoint_hash`, and
final-status hash all equal the local hash and both terminal kinds are
`Applied`; `Rejected`, `Expired`, a non-Applied result, a mismatched hash, or
timeout fails instead of returning success. Production DI still supplies
`UnavailableIrohaTransferSigner` because
the reviewed immutable codec artifact and live proof remain unavailable.

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
- The first-release chain identity, canonical native-XOR definition, Torii
  definition precision, fanout completeness, decimal history conversion, and
  receipt/finality rules are implemented and covered by focused tests. The
  authoritative fee policy must still be resolved and tested before signing;
  fee estimation currently fails closed with the typed
  `IrohaTransferFeeException` / `AUTHORITATIVE_POLICY_UNAVAILABLE` outcome
  instead of displaying a fabricated zero fee.
- Bouncy Castle's `Ed25519PrivateKeyParameters` makes an internal private-key
  copy and offers no destruction API. Caller and temporary arrays are wiped,
  but that provider-owned residual copy is a production risk that must be
  accepted or removed.
- Production DI still uses `UnavailableIrohaTransferSigner`. A future wallet
  adapter must derive locally, pass canonical identifiers/amounts, and pass
  Android release R8/device tests. The dormant submission path already requires
  local/top-level/transaction/receipt/final hash equality and an `Applied`
  MCP submit-and-wait result.
- The pinned SDK has a confirmed fixed-`u64` transaction-hasher defect. The
  staged compact hasher matches inspected release-tag Rust framing and the
  current local native-host diagnostic, but the host binary's build provenance
  is unproven. A fixed upstream SDK must be published and reviewed, and the
  Android result must match a funded live Taira Torii receipt. The local
  diagnostic is not deployment or live-network evidence.
- The 2026-08-23 read-only live observation reports build commit
  `7efcc118eb50e3369d004d092f9b9d0b4d31ac52`, only six blocks and two peers,
  roughly 41 minutes without chain progress, no reported chain id, and an
  absolute manifest-path disclosure. Canonical XOR reads reached only one of
  five fanout routes and returned a null scale; none of the four committed
  validator DNS names resolved. This mobile snapshot is fail-closed context;
  the workspace-root live gate remains deployment authority.
- Nexus stays disabled, and neither network may be enabled for production send
  until funded Taira and Nexus broadcasts are independently recorded.
