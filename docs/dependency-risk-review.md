# Android dependency review — 23 October 2026

## Scope and source

This review starts at vendor-cleanup commit `8207803efe281ecb58bf10127c7b8448fbba3432`.
That commit is ahead of the Play testing integration's SDK removal work: removing the
Beacon UI alone leaves its SDK and transitive libraries in the app graph. The baseline
release runtime has 439 exact Maven coordinates. An OSV query on 23 October 2026 finds
20 advisory records affecting six artifacts. This is an advisory inventory, not twenty
demonstrated exploits. Native binaries, private overlays and source forks also need
source review because a Maven-only scan cannot attest them.

## Changes

| Runtime dependency | Baseline | Candidate | Reason |
| --- | --- | --- | --- |
| Jackson annotations/core/databind/BOM | 2.14.2 | 2.18.11 | RPC JSON parsing, nesting limit and subsequent 2026 parser/databind fixes |
| Guava | 30.1.1-android | 33.3.1-android | CVE-2020-8908 and CVE-2023-2976 temporary-file handling |
| Commons Lang | 3.12.0 | 3.18.0 | CVE-2025-48924 class-name recursion |

The existing vendor change additionally removes Beacon 3.2.4, lazysodium and Ktor
2.2.3 from the app graph, updates bcprov to 1.85 and BeanUtils to 1.11.0, and replaces
unmaintained `org.lz4:lz4-java` 1.7.1 with the compatible `at.yawk.lz4` fork at 1.11.4.
Those changes and their source/pin checks must travel with this follow-up. The staged
Iroha bridge remains outside production app dependencies with its own pinned graph.

`runtime/.../connection/EthereumConnectionPool.kt` constructs web3j `HttpService`
and `WebSocketService`; both parse RPC peer data using Jackson. A malicious or
compromised RPC peer can return deeply nested JSON. The
[Jackson maintainer advisory](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-h46c-h94j-95f3)
documents the missing nesting limit before 2.15.0. The candidate stays on the Jackson
2.x API family and adds a regression through web3j's actual HTTP deserializer. Normal
block numbers and block/transaction containers are positive controls; nested RPC
error data must raise a parsing exception caused by `StreamConstraintsException`. Further 2026 Jackson records
often require specific polymorphic types, fields or parser modes; their reachability
is not asserted here.

No direct application calls to Guava `Files.createTempDir`, `FileBackedOutputStream`
or Commons Lang `ClassUtils.getClass` were found. These runtime packages remain in
the transitive graph, so the compatible upgrades remove the obsolete implementations
without treating a source-import search as proof that all indirect paths are safe.
The Guava JRE version used by build tooling remains separate from the Android runtime
variant.

## Remaining work

| Area | Evidence and current limit | Closure |
| --- | --- | --- |
| Google OAuth Client 1.26.0 | `public-shared-features-backup/.../data/GoogleDriveStorage.kt` uses Google Play Services sign-in and `GoogleAccountCredential.usingOAuth2`, then `NetHttpTransport` and `GsonFactory`. No direct `IdTokenVerifier` or authorization-code/PKCE implementation was found. These are the APIs involved in CVE-2021-22573 and CVE-2020-7692. | Upgrade the Drive/OAuth family together and qualify backup round trips and actual native sign-in. |
| EdDSA 0.3.0 | Pinned Utils `encrypt/Signer.kt:132` passes signatures straight to the provider without enforcing `S < L`. The API is used by account/chain/root secret proof validators, usually on locally generated signatures. CVE-2020-36843 permits alternate encodings of an existing valid signature; it does not by itself disclose a private key. | Add canonical-scalar and length checks with RFC 8032 and `S + L` regression vectors in public Utils; preserve signing bytes; review and repin exact source. |
| Spongy Castle 1.58.0.0 | Remains through Utils and kethereum; Utils imports its provider, EC types, PBKDF2/SHA512 and encoders. Updating `org.bouncycastle` does not update the distinct `org.spongycastle` namespace. No absence-of-CVE guarantee follows from the Maven scan. | Migrate or maintain the used primitive surface with wallet/key derivation vectors before dropping the provider. |
| Native crypto | Four unused libsodium 1.0.19 binaries remain in the explicit binary allowlist after Beacon removal. sr25519 binaries are bound to source commit `7500809f33243ee47ecb2ec8563fc284ac4de0d6`, while the Rust manifest uses version ranges and no tracked Cargo.lock. | Remove unused sodium through both provenance and AAB allowlists; recover/lock and scan the native graph before any rebuild. |
| WebSocket source fork | Guarded transport is pinned to `18a94e2db0f67a8387414bc7db53aac48ded18d7`; TLS hostname checks default on. Endpoint probes set incoming frame/stream limits, but long-lived Utils `SocketTransportPolicy.createSocket` returns the default socket with unlimited incoming sizes. No compression extension is enabled by the app source found here. | Establish explicit per-message limits compatible with large runtime metadata and fragmented messages; retain transport regression tests. |
| Public/private and release source | The private Android repo passes the overlay-only audit. Six formerly private compatibility modules are local public substitutions. Utils/transport are exact-source pins rather than Maven fallbacks. | Publish/review any required pinned source commits before hosted CI and release qualification; local source checks alone do not prove hosted fetchability. |

Primary references: [Google OAuth signature-verifier advisory](https://github.com/googleapis/google-oauth-java-client/security/advisories/GHSA-hw42-3568-wj87),
[Google OAuth PKCE issue](https://github.com/googleapis/google-oauth-java-client/issues/469),
[EdDSA maintainer issue](https://github.com/str4d/ed25519-java/issues/82),
[Guava temporary-file issue](https://github.com/google/guava/issues/6532),
[Commons Lang fix](https://github.com/apache/commons-lang/commit/b424803abdb2bec818e4fbcb251ce031c22aca53).

## Validation record

The vendor baseline passed strict Gradle provenance, its adversarial suite (2 positive
and 65 negative cases), and the public artifact audit with strict provenance. The
candidate's metadata bootstrap completed and all 20 added artifact/checksum entries
were independently checked against Maven Central bytes. An attempt to combine the
dependency-report and Internal App Sharing verification tasks was correctly rejected
by the existing exact-task guard; the two lock refreshes must run separately.

Release and Internal App Sharing lock refreshes both completed; their runtime graphs
match. A fresh OSV query of the candidate's 439 coordinates leaves only two artifacts
with three advisory records: Google OAuth Client and EdDSA. The added Jackson,
Guava and Commons Lang versions have no matching advisory in that dated response.

All 383 distinct targeted cases passed, with zero failures/errors/skips: 3 new RPC
parser cases, 4 existing TLS hostname cases, 3 Ethereum signing/balance cases,
1 phishing CSV case and all 372 public backup cases. The standalone baseline web3j
probe accepts 2,000 nested error arrays with Jackson 2.14.2; the candidate 2.18.11
throws a JsonMappingException caused by StreamConstraintsException while ordinary
block replies still parse. Final strict provenance and public artifact audits pass,
as does the provenance suite's 2 positive and 65 negative cases. The new RPC test
file passes scoped Detekt with the repository config and formatting plugin. The
standard root task excludes runtime, so the scoped run explicitly selects this
file; it is not a full-repository lint claim.

A separate Utils candidate `a23ca0595df12eb4015b209825d7737fbbc29b41` adds the
Ed25519 canonical-scalar guard. Its baseline reproduces S + L acceptance and its
10 targeted tests pass, preserving RFC 8032 signing bytes. This app candidate
still uses the original exact Utils pin; adopting the guard requires updating all
reviewed commit/tree/workflow/documentation pins and rerunning consumer checks.
The package advisory match persists until EdDSA itself is replaced; only the
verified API boundary is mitigated by that patch.

AAB identity regression needs an exact current AAB fixture and was not qualified
by this source-only review. Full application build, full test/lint, release and
native acceptance remain separate gates.
