# Android app instrumentation profiles

`scripts/run-android-migration-full.sh` runs the ordinary API 34 migration and
startup suites, then requires two additional app profiles. The emulator lifecycle
runner creates the disposable emulator and exports its serial and ownership
acknowledgement. The app helper binds the actual AVD, boot identity and installed
debug-app UID before providing fixture arguments. Never point this invocation
at a personal wallet installation.

For an independently created disposable API 34 emulator, set `ANDROID_SERIAL`,
`ANDROID_HOME` and
`ANDROID_APP_OWNED_DEVICE=exclusively-owned-disposable-emulator`, then run:

```sh
bash scripts/run-android-migration-full.sh
bash scripts/verify-android-migration-instrumentation-results.sh
```

The ordinary run keeps the production `legacy` presentation flags. The loading
rollback check expects moving frames for this artifact. A separate debug build
enables only `FW_PALETTE=v1`, `FW_RESTYLE_RECOVERY=true` and
`FW_RESTYLE_LOADING=true`; all five focus, recovery-button and loading-frame
tests must pass. Production Gradle defaults remain unchanged.

The journal profile invokes individual Join/Create fixture methods in ordered
fresh processes. It observes the admitted fake transaction, kills that exact
live debug-app PID as the app UID, then requires a different reader PID with
identical journal bytes. Acknowledged and finalized records are also checked
across restarts. The Join v1-format compatibility case uses the supported legacy
encoder; it does not claim installation or execution of a historical APK.
Only test-specific preferences and evidence directories are used. No signer or
network operation runs in this profile.

`config/android-app-instrumentation-profiles.json` enumerates the qualifications
that cannot run in the generic all-method invocation:

- Twelve historical or dedicated installed-device classes require their original
  empty/retained UID, exact receipt hashes, old schema, installed public-wallet
  fixture, or API 26/36 contract. Their safety checks remain intact. An operator
  must satisfy each class's explicit arguments and historical prerequisites;
  arbitrary replacement arguments are not valid qualification.
- `NativePrivateChainSendInstrumentedTest` requires isolated Bitcoin and Solana
  validators. Its dedicated entry point is
  `scripts/validation/test-native-private-chain-sends.mjs`, with
  `FEARLESS_WEB_CHECKOUT` optionally selecting the canonical browser checkout.
  It uses public vectors and loopback RPC only.

These categories do not count as passing migration, real PIN/lifecycle,
private-chain, physical-device or production-release qualification. Existing
Room store tests, navigation/revocation unit tests and isolated UI tests provide
separate behavior coverage. The two journal process classes are excluded only
from the unordered ordinary invocation and are mandatory in the host profile.

Evidence is written to `build/reports/android-app-profiles/`: source content and
commit identities, legacy/v1 debug and test APK SHA-256 digests, the manifest and
limits, device metadata, ordinary and v1 XML, actual process logs, seven journal
transitions and the completion receipt. The ordinary XML is restored to its
standard location for the unchanged zero-failure/error/skip migration verifier.
The artifact packager includes the additional profile evidence. Radios and
battery overrides used by these tests are restored; runtime release gates and
required migration identities/count floors remain enforced.
