# Guided recovery acceptance

This is an acceptance protocol, not a record that every item has passed. Use only public test wallets in an isolated emulator. Keep original app data and historical fixture copies; never remove a funded installation to run these checks.

## K5: repair an existing key

1. Open a wallet with one unusable key and a second working key. The banner must open Wallet recovery; Settings → Security → Wallet recovery must reach the same screen. Back returns to the invoking tab.
2. Check that the affected wallet and key scope are named. A network account must show its public account ID. The other key and other wallets must remain usable.
3. Choose phrase, raw seed/private key, or JSON recovery. The existing PIN check must precede the input screen. Cancel authentication and confirm that no recovery screen or key write occurs.
4. Enter a matching public fixture backup. The same wallet ID/address must become usable, the original damaged/quarantined data must stay retained, and no second wallet must be created. Repeat with a different valid phrase: the inline mismatch explanation must appear and stored values must remain unchanged.
5. For a non-default Substrate or Ethereum mnemonic path, expand Advanced and use the path that owns the recorded account. Raw Ethereum private-key recovery must not ask for a path. TON must accept the exact historical phrase spelling and spacing.
6. Recover ECDSA Substrate JSON, Ethereum JSON, and an empty-password JSON fixture. The durable account identity must determine the required signing type. An oversized or unreadable file must give an error and leave stored wallet data intact.
7. Switch input modes, put the app in the background, return, rotate, and leave the screen while derivation runs. Recovery input must not be restored from Android view state or SavedStateHandle. The recovery screen must block screenshots and autofill; backgrounding must clear inputs and cancel pending derivation.
8. For an unverified root backup, open the working-key backup choices. For a network-key advisory, the authenticated JSON export must refer to that network account. A TON as-typed warning must explain exact spelling/spacing. Exact stored-data export remains available separately.
9. Verify all eight shipped locales, narrow screens, 200% text, TalkBack reading order, visible keyboard, inline-error announcements, and 48 dp actions. Scroll to every action without clipping its text.

Automated coverage is in `ImportAccountRecoveryViewModelTest`, `WalletKeyRecoveryTest`, `WalletRecoveryInventoryTest`, `RecoveryCenterViewModelTest`, `MainNavigationContractTest`, `RecoveryInputPrivacyInstrumentedTest`, and `WalletRecoveryUiInstrumentedTest`. Read the dated evidence in `docs/status.md` to distinguish executed checks from this manual protocol.

## Gates still open

## K5: retained wallet records

1. Open Recovery Center → Open retained wallet data. Cancel the existing PIN prompt: no retained keys or addresses may be read. Authenticate and confirm that records appear independently, including unrecognized or undecryptable records.
2. Select Restore beside a derived Substrate, Ethereum or TON address. Confirm the address and the separate-wallet explanation. The new wallet must sign for that exact public identity; the original archive must remain byte-for-byte unchanged. For multi-key and network records, each selected key becomes a separate wallet; networks may need selection again.
3. Restore an as-typed historical TON record and a legacy record whose recovery phrase cannot be verified. A proved signing key must remain usable without claiming the wallet is backed up. The Recovery Center should continue offering the applicable backup advisory.
4. Open exact stored data for a readable malformed/unknown record. The export must include the full original string, including unknown fields and whitespace. It must show the private-key warning before Share. Unreadable ciphertext and oversized fingerprint tokens must never be labeled as exported plaintext.
5. Select Delete permanently. The first confirmation explains that the entire record and unshown keys will be removed; the second explicitly says the action cannot be undone. Cancel at either stage: nothing changes. Confirm both: only the exact selected record is removed. Mutate the archive between preview and confirmation: restore/deletion must fail without replacing the changed record.
6. Background the screen during inspection, export, or derivation. Returning must show an unlock action, with addresses/export cleared. Reauthentication is required. Cancellation before journal staging must leave the archive and active wallets unchanged; an already journaled restore must replay safely after restart.
7. Repeat at 320 dp width, double font scale, and in each shipped language. Restore, export, both delete confirmations, Cancel, and Back must stay readable, scrollable, and tappable. Check screenshot protection on the real authenticated fragment, not only the test harness.

K6 unreadable-PIN reset and explicit fresh secure-storage recovery are separate required work. Debug emulator evidence does not qualify physical-device or signed production upgrade acceptance.
