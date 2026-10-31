# Android Drive and OAuth dependency refresh

This refresh was qualified on the 4.2.0 build 231 line on 23 October 2026 and
ported to `master`, where the Drive backup engine lives in `core-backup`. The
runtime library sources are owned in this repository (`runtime-utils`,
`runtime-websocket`), so the 231 line's separate fearless-utils pin does not
apply here.

## Drive and OAuth family

| Component | Previous | Integrated candidate |
| --- | --- | --- |
| Drive API v3 generated client | v3-rev197-1.25.0 | v3-rev20261011-2.0.0 |
| Google API core and Android clients | 1.26.0 | 2.9.2 |
| Google HTTP Gson client | 1.43.3 | 2.2.0 |
| Google OAuth client | 1.26.0 | 1.39.0 |
| Runtime Guava Android variant | 33.3.1-android | 33.4.8-android |
| Resolved Gson | 2.10.1 | 2.11.0 |
| Firebase App Distribution Gradle plugin | 4.2.0 | 5.3.1 |
| Buildscript OAuth client | 1.31.0 | 1.36.0 |

Versions and POM dependencies were checked against canonical Maven Central.
The generated API now uses the supported Java client 2.x family. OAuth 1.39.0
supersedes the releases affected by CVE-2020-7692 and CVE-2021-22573. The wallet
uses GoogleAccountCredential with Play Services access tokens rather than those
advisories' authorization-code/ID-token-verifier entry points; the obsolete
package is nevertheless removed from the runtime graph.

Backup storage retains the existing GoogleSignIn session and native consent
flow, DRIVE_APPDATA scope, validating NetHttpTransport, Gson serialization,
resumable upload, folder layout, download and deletion semantics. HTTP transport
injection is internal and lets compatibility tests execute the actual storage,
generated requests, and credential handler with local synthetic responses.
No actual account, token, Google Drive file or consent screen is used by these
tests. Live account/device qualification still requires an authorized configured
release environment; public placeholder OAuth/Firebase settings cannot prove it.

Google API core and Android clients are explicitly aligned. Android compile/runtime classpaths select Guava’s Android artifact, including when a Java library requests the JRE variant; build tooling retains its separate JRE graph. Firebase App Distribution 5.3.1 removes the vulnerable OAuth 1.31.0 and obsolete Jackson 2.10.2 tooling dependencies. No distribution task is run.

Unused Apache transport modules and their HTTP artifacts are excluded. The
existing narrow TrustAllX509TrustManager lint exception is re-reviewed and tied
to the exact HTTP 2.2.0 jar: its opt-in doNotValidateCertificate helper remains
unused; default certificate and hostname verification are retained.

## Verification

- `core-backup` compatibility tests run the actual storage, generated Drive
  requests and credential handler against local synthetic responses, including
  401 refresh, retry and failure. No real account, token, Drive file or consent
  screen is used; live account and device qualification stays separate.
- The release and Internal App Sharing locks and the strict verification
  metadata carry every new coordinate; each checksum was matched to Google Maven
  or Maven Central on the 231 line.

## Rollback

Revert the dependency commit as a unit, including locks, checksums and the
narrow lint exception. Never restore dependencies by disabling provenance
checks.
