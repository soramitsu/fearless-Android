# Google Play Internal App Sharing Publication Evidence

## Recorded outcome

On 26 July 2026, the Android Internal App Sharing bundle was selected in the
Japanese Google Play Console using Safari. Google displayed package
`jp.co.soramitsu.fearless`, version name `4.2.0-ias`, upload text
`7月26日 10:49にアップロードしました`, and expiry date/time `2026年9月24日`
and `10:49`. Google did not display version code `230`; that value belongs only
to the selected local artifact record. The upload label did not display a year
and neither timestamp displayed a timezone. The manifest therefore records
2026 as an explicit inference from the observation/expiry year and checks only
the 60-day displayed wall-clock interval; it does not invent UTC.

The console showed `リンクを共有したユーザーはダウンロードできます` selected,
`メーリング リストへのアクセスの制限` unselected, and the Save control
disabled. A signed-in tester page showed Fearless Wallet: DeFi Wallet by
Soramitsu and instructed the tester to open the link on an Android device.
These are operator-observed UI facts. They do not prove an
unauthenticated download, a successful device install, or an independent
tester. At capture time, installs by devices or independent accounts had not
been verified and the observed tester count was zero.

## Artifact correlation and limits

The selected local file was
`app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab`:

- SHA-256:
  `e963c993afb013451b3c241a6a40f9f267dbb0e9740ae1b79ad2e808b6650dad`
- Bytes: `42215593`
- Package: `jp.co.soramitsu.fearless`
- Version: `4.2.0-ias` (`230`)

Google did not display a server-side AAB digest, byte count, version code, or
signing certificate. Internal App Sharing also re-signs delivered APKs. The
binding is therefore the operator-observed file selection plus matching
package, version name, and time—not a Google-provided cryptographic binding. At
evidence review, the source tree was dirty and no source commit was bound to
this artifact, so it is not reproducible from a reviewed commit.

This build is debug-signed and uses the public placeholder Firebase
configuration. It is limited to UI and installation testing. It is not
production-equivalent and does not validate production signing, Play App
Signing, Play Integrity, app-link certificate association, production
Firebase, production upgrades, a normal Play testing track, or production
readiness.

## Link handling and tester prerequisites

The exact Internal App Sharing URL is intentionally absent from tracked files.
`config/google-play-internal-app-sharing-publication.json` pins its SHA-256,
canonical origin, path shape, observed token lengths, defensive local-validator
bounds, and expiry. Those bounds are not presented as a Google protocol. The unlisted URL has a
100-distinct-downloader ceiling, expires after 60 days, and would remain in Git
history after expiry if committed. It is retained locally only in the ignored,
mode-0600 handoff file:

`build/reports/google-play-internal-app-sharing/public-link.json`

After copying the current link in Google Play Console, write or replace that
private handoff without placing the URL in shell history or logs:

```bash
pbpaste | node ./scripts/write-google-play-internal-app-sharing-handoff.js
```

The writer accepts the URL only on standard input, verifies the exact HTTPS
origin, path shape, token bounds, digest, and recorded expiry, and atomically
writes a mode-0600 file. Its output contains only the URL digest and expiry.

“Anyone with the shared link” is not anonymous public access. A tester still
needs an eligible Google account, an Android device with Google Play Store, and
Internal App Sharing opt-in; Play Store listing eligibility also applies. Share
the exact URL directly with intended testers rather than publishing it. A
private Safari request for the same link led to Google sign-in, and no
unauthenticated tester page was observed. Google documents the 60-day lifetime,
100-distinct-downloader ceiling, re-signing, and tester opt-in behavior in its
[Internal App Sharing guidance](https://support.google.com/googleplay/android-developer/answer/9844679).

## Verification

Run the tracked, offline contract and adversarial suite:

```bash
node ./scripts/test-google-play-internal-app-sharing-handoff-writer.js
bash ./scripts/test-google-play-internal-app-sharing-publication-audit.sh
bash ./scripts/audit-google-play-internal-app-sharing-publication.sh --manifest-only
```

The private-handoff writer test executes exactly 2 positive and 17
negative/adversarial cases. The publication self-test executes exactly 6
positive fixtures and 184 negative/adversarial fixtures. Both fail if their
runtime totals drift.

On the publication workstation, verify the ignored handoff and the exact local
AAB as well:

```bash
bash ./scripts/audit-google-play-internal-app-sharing-publication.sh
```

The default command rejects missing, substituted, oversized, or symlinked
handoff/AAB paths; checks link origin, path, token bounds, and digest; validates
the exact AAB byte count and SHA-256; and runs the existing fail-closed native
alignment verifier. `--manifest-only` is explicit because CI checkouts do not
contain ignored build output. Neither mode queries Google, so current server
availability must be checked separately before sharing the link. For a
timezone-free temporal non-expiry check, supply an observed local wall-clock
value:

```bash
bash ./scripts/audit-google-play-internal-app-sharing-publication.sh \
  --require-within-recorded-expiry-window \
  --as-of-displayed-local 2026-07-26T11:00
```

The injected time must be on or after upload and strictly before expiry. A
date-only input fails closed for the entire expiry date because Google did not
display a timezone. This offline comparison cannot prove that Google has not
revoked the link or that the 100-user ceiling is unexhausted.
