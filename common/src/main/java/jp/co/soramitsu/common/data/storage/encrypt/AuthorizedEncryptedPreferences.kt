package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard

/** Keeps existing validation/quarantine behavior while replacing only the physical read. */
internal fun EncryptedPreferences.authorizedReads(guard: MutationExecutionGuard, intentSha256: String): EncryptedPreferences {
    val source = this
    return object : EncryptedPreferences by source {
        override fun getDecryptedStringSnapshot(field: String) = source.getAuthorizedDecryptedStringSnapshot(field, guard, intentSha256)
        override fun getDecryptedString(field: String) = getDecryptedStringSnapshot(field)?.plaintext
    }
}
