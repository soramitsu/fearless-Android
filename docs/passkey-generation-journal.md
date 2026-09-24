# Local immutable generation journal candidate

`PasskeyBackupGenerationJournal.forApplication(context)` uses `Context.noBackupFilesDir/passkey-generations-v1`. It stores ciphertext and recovery metadata only; there is no phrase, PRF output, unwrapped key, network operation, owner-head update or automatic cleanup. Recovery remains disabled. Call these synchronous APIs on an IO executor.

`persistPrepared(operationId, candidate, expectedScope)` validates the complete canonical FPBKGEN1 bundle and writes its exact bytes, size, SHA-256, preallocated Drive ID, operation ID and expected context before returning. The scope must be supplied independently from authenticated wallet-owner state and the verified selected Google account; reading a file does not establish owner authority. The same operation with identical bytes is idempotent. Conflicting bytes, reusing the account/Drive file ID, or reusing an owner/namespace/generation ID under another operation are rejected.

`GoogleDrivePasskeyBackupGenerationStorage.createCandidate(operationId, journal, expectedScope)` requires the durable journal and independently supplied scope. It validates the prepared record, obtains and pins a verified selected-account bearer, and builds the exact request **before** invoking `markCreateAttempt` on an IO dispatcher. Token or account failure before admission leaves the same candidate retryable without a POST. The separate immutable attempt marker binds the prepared record digest, bundle digest and Drive ID, then is synchronized before the private transport call. A surviving marker prevents another admission after restart. A partial or malformed marker also fails closed and is preserved; it cannot be discarded to authorize another upload. Failure or cancellation after admission requires read-only reconciliation of the same Drive ID, even if the process may have stopped before actually sending the request. A prepared/read entry alone is not an upload permit. The public create API cannot accept an in-memory candidate or re-admit an existing marker.

`read` and `listPending` strictly validate file type, private permissions, ownership, link count, bounded canonical JSON, context, size, digest and nested FPBKGEN1 bytes. They apply the independently supplied owner/namespace/account scope. Missing or corrupt data is not permission to mint another Drive ID, replace a generation or assume that a remote operation never happened. The journal provides no implicit recovery/repair of malformed records.

Prepared files are `<operationId>.prepared.json`; attempt files are `<operationId>.attempt.json`. Operation IDs are canonical unpadded base64url encodings of 32 bytes. The canonical local JSON format is `FPBKJNL1`/schemaVersion 1 with decimal strings for sizes/revisions/epochs and canonical base64url bundle bytes. Attempt format `FPBKATT1` binds the complete prepared file SHA-256. These local journal bytes need not match iOS's local journal encoding; FPBKGEN1 cloud bytes and lifecycle semantics do match. Bounds are 1 MiB per prepared file, 1 KiB per marker and 64 prepared entries. At the limit, creation fails until a separately reviewed retention coordinator exists.

The directory is 0700 and files are 0600. Exclusive creation prevents replacement. An in-process mutex and an OS file lock serialize readers and writers across processes. The file is fully written and forced to storage, then the directory is synced before success. Android directory sync uses public `Os.open` with `O_RDONLY|O_NOFOLLOW|O_NONBLOCK`, checks the descriptor with `fstat`/`S_ISDIR`, then calls `Os.fsync`; no hidden numeric flags are used. Existing paths are validated without silently repairing permissions, following symbolic links, accepting hardlinks or truncating a lock file. A crashed partial write remains a blocking record. A surviving complete prepared record is re-synced before a create-attempt marker can be issued.

`PasskeyBackupGenerationReconciler` is the read-only next step after an admitted
create attempt. It requires a current owner/namespace/Google-account scope and
expected wallet identity supplied independently of the journal. It reads only
the journaled Drive ID, validates the exact canonical downloaded FPBKGEN1 bytes
against the durable candidate and calls a mandatory application-owned local
verifier. The verifier contract requires PRF unwrap, AES-GCM decryption,
restored public wallet identity, original-key signing and export. The
coordinator checks the returned wallet identity and all three proof flags,
re-reads the journal and rechecks the selected Google subject after that
asynchronous verification. A 404 is an unknown outcome, not permission for a
second POST or a new ID. The result is local round-trip evidence only; it does
not commit an owner head or mark backup complete. No production verifier is
wired yet, so these paths remain disabled.

Validation: all 200 backup-module JVM tests pass with zero failures/errors/skips, including 14 journal cases and five upload-admission integration cases. These exercise separate-process hard exits and contention, canonical corruption, private paths, bounded retention and no repeated POST. `detektAll` and the test APK build pass under strict offline dependency verification. Two native cases pass on an isolated read-only API 36 arm64 emulator, exercising the production no-backup factory, native Android directory sync, private file attributes, exact 785-byte vector reload and marker replay denial. Host process-crash/emulator tests do not prove physical-device power-loss behavior. Physical-device filesystem durability and remaining supported-API qualification, authenticated head/grant HTTP adapters, actual local decrypt/identity acceptance, enrollment/revocation/rotation and real replacement-device recovery remain release gates. App uninstall, privileged filesystem tampering or rollback are not prevented by this local journal; current authenticated server head/operation status remains necessary.

The matching read-only reconciler raises that JVM suite to 205 tests, with
zero failures/errors/skips and a clean `detektAll` run. Its tests cover restart
after a lost upload response, 404, wrong owner scope, tampered media, failed
local proof/cancellation, account switching and refusal of another POST. The
verifier in these tests is synthetic; no decrypt/sign/export claim is made for
production devices.

`PasskeyBackupGenerationCryptographicVerifier` now supplies the local unwrap
and AEAD-decryption half of that contract for one exact credential wrapper.
It rejects a wrong credential, PRF result, wallet identity, ciphertext or
incomplete evidence and clears temporary PRF, DEK and plaintext buffers.
Its application-owned plaintext callback must still derive the original wallet
identities and actually verify original-key signing and export. Four focused
cases use only synthetic wallet material; no production wallet callback,
authenticated owner-head HTTP adapter, provider ceremony or replacement-device
flow is wired, so recovery remains disabled.

## Disabled verified promotion candidate

`PasskeyBackupVerifiedGenerationPromotion` composes the fresh authenticated
owner head, the exact append-only journaled Drive generation, the read-only
reconciler, the local original-key verifier, and owner grant/commit/status
routes. A committed response alone is insufficient: it returns
`CurrentVerifiedHead` only after exact Drive download, local PRF unwrap,
AEAD decryption, original-key signing/export evidence, exact operation status,
and a fresh matching owner head. It never installs keys or tells the wallet UI
that a backup is complete. `AwaitingDriveReadback` and
`AwaitingOwnerOperation` never authorize another upload or CAS.

The new private `<operationId>.commit.json` marker (`FPBKCOM1`) binds the
prepared record digest, Drive ID, bundle digest and operation ID. The journal
syncs it before commit dispatch; any surviving or partial marker denies another
commit. The read-only reconciliation view can use an intact prepared/create
record even if the commit marker tore during a crash; marker presence still
forbids mutation. After a lost response or restart, status is reconstructed from the
same durable journal candidate and independently authenticated owner/Google
scope, even though a successful commit has already advanced the current head.
Status must describe the exact candidate, and fresh owner-head plus Drive
readback must show it current and byte-for-byte equal to the verified candidate.
A status of `absent` after a commit marker remains unresolved;
the client never retries the CAS. No prior encrypted Drive generation is
deleted or patched.

`PasskeyBackupExistingHeadCandidateVerifier` demonstrates the native proof
boundary for an existing owner head: it asks for a credential-directed
assertion, keeps the PRF result local and one-use, waits for server assertion
verification, then decrypts the candidate and invokes the application-owned
original-key signing/export verifier. A fresh verifier must be created for each
authenticated head, including after restart. An empty-head first enrollment
still needs a separate server-verified candidate assertion API. Neither that
API nor the production original-key callback is deployed/wired, and the
compiled recovery gate remains false. The new tests use synthetic identities
and test PRF material; they do not prove device/provider interoperability.

Primary references: [Android backup-excluded storage](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()), [exclusive NIO file creation](https://developer.android.com/reference/java/nio/file/StandardOpenOption#CREATE_NEW), [FileChannel force semantics](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean)), and [Android fsync](https://developer.android.com/reference/android/system/Os#fsync(java.io.FileDescriptor)).
