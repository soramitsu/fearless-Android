# Immutable backup generation candidate

This disabled storage primitive adds canonical FPBKGEN1 records and append-only Google Drive operations. It does not authenticate an owner, commit a server head, unwrap a key, decrypt a downloaded wallet or enable recovery. The legacy FPBKAEAD and FPBKWRP1 formats and storageKey/AAD remain unchanged; the random owner backupNamespace is a separate field.

## Canonical bytes

All integers are signed big-endian. A length prefix is an int32 count of bytes, followed by exactly those bytes; strings use strict UTF-8 without normalization. Fields occur in this order:

1. ASCII `FPBKGEN1` (8 bytes), int32 version `1`.
2. Length-prefixed ownerSubject, backupNamespace, generationId.
3. int64 parentHeadRevision; byte parentDigestPresent (`0` or `1`); raw 32-byte parent digest only when present.
4. int64 keyEpoch; raw 32-byte storageAccountBinding.
5. Length-prefixed original envelope storageKey, walletId, accountName; int64 createdAtMillis; int32 envelope schemaVersion; length-prefixed unchanged FPBKAEAD bytes.
6. int32 wrapper count; for each wrapper in strictly increasing canonical credential-ID ASCII order: length-prefixed credentialId then length-prefixed canonical FPBKWRP1 bytes. No duplicate credentials or trailing bytes.

Total size is at most 524288 bytes; individual UTF-8 fields are at most 2048 bytes, each wrapper at most 8192 bytes, and wrapper count is 1 through 32. Existing envelope, metadata and credential validators apply too. The FPBKAEAD limit stays 262144 bytes. Parent revision is nonnegative; its digest is absent exactly at revision zero. Epoch is positive. ownerSubject and backupNamespace have `owner:` and `backup:` prefixes followed by canonical unpadded base64url of 32 bytes; generationId is canonical unpadded base64url of 32 bytes. Exposed digests are lowercase hex64. Each wrapper must match the owner, epoch and original envelope metadata.

storageAccountBinding is SHA-256 of ASCII `FPBK-GOOGLE-SUB-v1`, one NUL byte, then the verified Google OIDC subject in ASCII. It describes the storage account, not a Fearless owner. Decode requires an expected context and digest from an authenticated owner-head response or an intact pending-operation journal. Constructing that expected context does not authenticate it. A matching bundle hash is not proof that any wrapper can recover its DEK.

The independent Node vector is [passkey-generation-v1.json](../public-shared-features-backup/src/test/resources/passkey-generation-v1.json): 785 bytes, SHA-256 `1c92b544dc25c687c202317d0e5747b5690a1056cf72e61d1dfab84c07c057a4`. All keys/identities in that fixture are synthetic public test data. It retains the FPBKWRP1 vector digest `2ac784e30e93efb4a7fe2505724e1c67ae6f4f16e9aa834029e0bb08c27509dc`.

## Drive operations and uncertainty

`GoogleDrivePasskeyBackupGenerationStorage` pins the verified Google subject at each token acquisition. Its default transport forbids redirects and retries, uses a 30-second deadline and no cache/interceptors. No list-by-name, PATCH or DELETE operation exists in this primitive.

- Allocate with `GET /drive/v3/files/generateIds?count=1&space=appDataFolder&type=files`. Require one valid ID in the returned appData space.
- Prepare and durably journal the file ID, exact bytes/digest/context and owner operation ID before attempting an upload. The separate [local journal candidate](passkey-generation-journal.md) now persists the candidate and a single create-attempt marker; the public create API requires that journal and independent scope and consumes admission before its private POST helper.
- Create with a one-shot multipart `POST /upload/drive/v3/files?uploadType=multipart`, including the preallocated `id`, a generation-specific name, `application/octet-stream` MIME type, `parents:[appDataFolder]`, and the exact bundle. appProperties hold only format=`FPBKGEN1`, namespaceSha256, generationId and bundleSha256, each below Google's key+value limit. Long original account metadata stays in the bounded authenticated bundle, never a truncated property.
- A valid upload response only yields ACKNOWLEDGED. Any non-success (including409), malformed success or lost response requires reconciliation. Cancellation after dispatch also has an unknown outcome. This primitive never allocates another ID or retries automatically.
- Read exact file ID metadata (`id,name,mimeType,spaces,appProperties,size`), then `alt=media`, checking the account again before each request. Require appData space, exact ID/name/properties/size, full-byte SHA-256, expected context and canonical nested formats. A404 is only an observation, not proof that a pending upload never completed. Returning a parsed record does not prove local decryption.

Google documents [appData ID allocation](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/generateIds), [409 on repeated creation with a pre-generated ID](https://developers.google.com/workspace/drive/api/guides/create-file#generate-ids), [file spaces](https://developers.google.com/workspace/drive/api/reference/rest/v3/files), and [124-byte private-property limits](https://developers.google.com/workspace/drive/api/guides/properties). Immutability is enforced by application behavior and authenticated digests; Google-account access can still modify or destroy Drive files.

## Disabled owner-head read adapter

`PasskeyBackupOwnerHeadHttpClient` now makes a read-only `POST /api/passkey-backup/v1/owner/backup/head` with exactly `{"schemaVersion":1}` and a fresh owner-session bearer obtained from a verified discoverable-passkey authentication. It rejects an expired session before and after the network request. The response is limited to 8 KiB; strict UTF-8 and closed JSON parsing reject duplicate decoded keys, coercion, extra fields and trailing data. The adapter obtains the selected Google account's verified `sub` from the same token provider used for Drive and rechecks it after the network request; it never accepts a caller-supplied binding digest or sends the Drive token to the owner service. The existing authenticated-head model then checks the exact owner, namespace, expected Google subject binding, current/previous generation chain and Drive file identities. The adapter neither reads Drive data nor unwraps or installs a wallet, and the compiled release switch still rejects its use.

## Remaining coordinator and release gates

A separate durable coordinator must reconcile the exact journaled candidate after crashes, download and locally unwrap/decrypt it, validate recovered wallet identity, then request an owner-authorized atomic head comparison/update. Keep owner revocation generation, head revision, random generationId and DEK keyEpoch distinct. The authority must recheck the live credential/session and owner generation in the same transaction as head CAS and idempotency. A lost commit response must query authenticated status; a conflicting writer must recover/merge the winning generation before creating a new candidate, not blindly retry against a new revision. Client verification claims do not prove decryption to the server.

Retain prior accepted and unresolved generations. There is no automatic deletion or final-credential removal in this increment. Rotating a DEK requires verified surviving credentials and cannot invalidate a DEK already learned by an old credential holder. Owner-head readback is only a disabled metadata adapter: grant/commit HTTP integration, owner bootstrap, native round-trip and enrollment/revocation coordination, same-project Google configuration/consent, provider/device acceptance and cross-platform restore remain incomplete. All release flags remain off.
