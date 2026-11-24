# Carrying Reown WalletConnect sessions over to the in-repo client

Fearless Android 4.3.1 and earlier ran WalletConnect on the Reown SDK (`com.reown` 1.6.9), which is
under the WalletConnect Community License, a source-available licence. 4.3.2 replaces the SDK with the
in-repo client (`feature-walletconnect-impl/.../protocol`). On the first start after the update,
`ReownSessionImporter` reads what the SDK stored and adds it to the client's own state, before the
client starts. Every dApp the user had connected keeps working, with the same topics and keys. The
dApp side notices nothing.

Code: `feature-walletconnect-impl/src/main/java/jp/co/soramitsu/walletconnect/impl/data/legacy/`.

## What is carried over

- **Every keyed, complete session, settled or not.** Each keeps:
  - its symmetric key (the key must hash to the topic);
  - its self and peer public keys;
  - its session, required and optional namespaces;
  - its pairing topic, expiry and the dApp's metadata.
- **Sessions that expired while the app was off.** These are imported too, so the client reports them
  through its own expiry path when it starts, instead of their vanishing silently.
- **Pairings**, so a dApp can propose again over an existing pairing.
- **The relay identity** (`key_did_keypair`), unless the client already made its own.

Proposals and requests that were in flight are not carried over, as in the web wallet's SDK import.
Records the client already has always win, so importing twice changes nothing. The client's limits
apply, live records first: sessions, pairings, and metadata sizes and icons.

## Where the SDK kept it, and how it is read

| What | Where | How |
|---|---|---|
| Sessions, namespaces | `databases/WalletConnectV2.db` (SessionDao, NamespaceDao, ProposalNamespaceDao, OptionalNamespaceDao) | SQLCipher 4 |
| Pairings, metadata, requests, Verify | `databases/WalletConnectAndroidCore.db` (Pairing, MetaData, JsonRpcHistoryDao, VerifyContext) | SQLCipher 4 |
| Key chain | `shared_prefs/wc_key_store.xml` | EncryptedSharedPreferences, Keystore key `wc_keystore_key` |
| Database passphrase | `shared_prefs/db_key_store.xml`, `encryptedDBKey` | EncryptedSharedPreferences and AES-GCM, Keystore key `_wc_db_key_` |

`SqlCipherDatabaseDecoder` decrypts SQLCipher 4 in Kotlin, so no native library is added:
- **Keys:** PBKDF2-HMAC-SHA512 with 256,000 iterations derives the page key; a second derivation over
  the salt XOR 0x3a gives the HMAC key.
- **Pages:** 4 KiB AES-256-CBC pages, each with an HMAC-SHA512 tag over the ciphertext, the IV and the
  page number.
- **Journal and WAL:** a hot rollback journal or a write-ahead log beside a database is applied first,
  as SQLite would on open.

`SqliteDatabaseImage` then reads the tables straight from the decrypted image, following the SQLite
file format:
- table b-trees and overflow chains;
- column defaults for rows written before an `ALTER TABLE`;
- REAL affinity.

## Nothing of the SDK's is changed until the import succeeded

- **Databases** are read as bytes and decrypted in memory. They are never opened with SQLite, which
  would roll back their journal or checkpoint their log. No plaintext is written anywhere.
- **Preferences** are opened only when their Keystore key and both Tink keysets already exist. Opening
  an incomplete file would write new keysets into it, and Tink would create a missing key.
- **Logs and results** carry only fixed codes (`ReownSkipReason`, `ReownImportFailure`), never a topic,
  key, payload or exception text.

**Crash safety.** The import and its marker are one atomic write of the client's state. The marker
sits among the state's `handled` entries and is never pruned, because its "forget after" time is
Long.MAX_VALUE. As a result:
- an app killed before that write imports again from nothing;
- an app killed after it never imports twice;
- merging never replaces a topic the state already has, so a repeat can neither duplicate a session nor
  mix up keys.

The state is read back right after the write, as the client's next start will load it. The import counts
only when the marker and every pairing, session and key are there. Otherwise it reports `NOT_SAVED`, puts
the previous state back as far as the storage allows (so no marker stays that was not read back with
everything), and the SDK's files stay.

**The starter's timeout.** `importInto` reads the SDK's data off the caller's coroutine. A caller that stops
waiting, such as the starter at its 60 s timeout, is released at once, even when a Keystore call hangs.
From then on the import writes nothing, so the client the starter goes on to start has its state to itself.
A write already under way is finished first, up to 2 s. The outcome is `CANCELLED`, and the SDK's files stay.

**Failure policy.** On any failure, every SDK file stays where it is. WalletConnect still starts without
the old sessions, and the outcome is logged as counts and codes. A later start tries again:
- each attempt is counted before the SDK's data is read, so an import that crashes the app still uses one
  up and cannot become a crash loop;
- after three attempts it waits for the next app version;
- an attempt that failed only because the client's storage could not be written is given back. One whose
  save did not read back, or that was cancelled, is not, so a storage or device that keeps failing cannot
  make every start repeat the work.

The worst case is that the user reconnects their dApps.

**When cleanup runs.** `LegacyWalletConnectSdkCleanup` deletes the SDK's files only after the import was
saved, or when there was nothing to import. If a live session or pairing had to be left out over a limit,
the import is incomplete: the marker records it, and the files are never deleted. Records that cannot be
used at all are not counted as left out: a missing key, a key that does not match its topic, or no
namespaces.

## Tests

- **Fixtures.** `feature-walletconnect-impl/fixtures/make-reown-legacy-fixtures.py` writes the
  fixtures in `src/test/resources/reown-legacy/` with the real SQLCipher library (synthetic keys only):
  - Reown-shaped sign and core databases;
  - a transaction cut off mid-write;
  - committed and uncommitted WAL frames;
  - a plain database covering every record and b-tree shape.
- **Unit tests** check:
  - the decoder and reader against the rows SQLCipher itself returns;
  - the import rules;
  - the importer at each point the app can be killed, and its failure handling;
  - that the marker survives the client's own writes;
  - the end-to-end path. In the end-to-end test,
  `ReownMigrationClientTest` starts the in-repo client on imported state and answers dApp requests over
  the migrated sessions. It also checks that a session which expired while the app was off is reported
  once a listener exists.
