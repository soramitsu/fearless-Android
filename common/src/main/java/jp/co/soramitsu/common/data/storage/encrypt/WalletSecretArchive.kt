package jp.co.soramitsu.common.data.storage.encrypt

/**
 * Retained, never-active location for wallet ciphertext that left active use
 * without being proven corrupt: a legacy `ACCESS_SECRETS` payload after its
 * conversion to V3, the secrets a deleted wallet left behind, quarantines an
 * earlier build wrote for wallets that no longer exist, and the stored value
 * of a V3 or chain-account payload the upgrade rewrites in canonical form.
 *
 * Values are moved here by exact ciphertext (a value rewritten in place is
 * kept as its exact plaintext) and are never read for signing or counted as
 * recovery state. Nothing here is ever deleted by an upgrade; a
 * later start may move an archived value back to its active key when its
 * wallet exists and the value proves the wallet's public identity.
 *
 * The prefix starts with a letter, so the numeric active-secret namespaces
 * scanned by the orphan inventory never include an archive key.
 */
object WalletSecretArchive {

    const val KEY_PREFIX = "wallet_secret_archive:v1:"

    /** Distinct values ever archived for one source key; far above any real need. */
    const val MAX_SLOTS = 64

    /**
     * Slot 0 is `<prefix><sourceKey>`; later slots append `:<slot>`. No source
     * key ends in `:<digits>`, so the slot suffix is unambiguous.
     */
    fun keyFor(sourceKey: String, slot: Int = 0): String {
        require(sourceKey.isNotBlank()) {
            "A wallet-secret archive source key cannot be blank"
        }
        require(
            !sourceKey.startsWith(KEY_PREFIX) &&
                !sourceKey.startsWith(WalletSecretQuarantine.KEY_PREFIX)
        ) {
            "A wallet-secret archive is named after the original active key"
        }
        require(!sourceKey.hasSlotSuffix()) {
            "A wallet-secret archive source key cannot end in a slot suffix"
        }
        require(slot in 0 until MAX_SLOTS) {
            "A wallet-secret archive slot is out of range"
        }
        return if (slot == 0) {
            "$KEY_PREFIX$sourceKey"
        } else {
            "$KEY_PREFIX$sourceKey:$slot"
        }
    }

    /** The original active key an archive key was named after, or null. */
    fun sourceKeyOf(archiveKey: String): String? {
        if (!archiveKey.startsWith(KEY_PREFIX)) return null
        val named = archiveKey.removePrefix(KEY_PREFIX)
        val separator = named.lastIndexOf(':')
        val slot = if (separator > 0) {
            named.substring(separator + 1).toCanonicalSlotOrNull()
        } else {
            null
        }
        val sourceKey = if (slot != null) named.substring(0, separator) else named
        return sourceKey.takeIf { it.isNotBlank() }
    }

    private fun String.hasSlotSuffix(): Boolean {
        val separator = lastIndexOf(':')
        return separator > 0 && substring(separator + 1).toCanonicalSlotOrNull() != null
    }

    private fun String.toCanonicalSlotOrNull(): Int? {
        val slot = toIntOrNull() ?: return null
        return slot.takeIf { it in 1 until MAX_SLOTS && it.toString() == this }
    }
}

/**
 * Plans the exact move of [expectedSnapshot] from [sourceKey] into the
 * archive named after [archiveNameKey]. Returns the first slot that is free or
 * already holds this exact ciphertext, so a retried move completes instead of
 * conflicting with a value archived earlier.
 */
fun EncryptedPreferences.planArchiveMove(
    sourceKey: String,
    expectedSnapshot: EncryptedPreferenceSnapshot,
    archiveNameKey: String = sourceKey,
    reservedDestinations: Set<String> = emptySet()
): EncryptedPreferenceSnapshotMove {
    for (slot in 0 until WalletSecretArchive.MAX_SLOTS) {
        val destination = WalletSecretArchive.keyFor(archiveNameKey, slot)
        if (destination in reservedDestinations) continue
        if (!hasKey(destination)) {
            return EncryptedPreferenceSnapshotMove(
                sourceKey = sourceKey,
                destinationKey = destination,
                expectedSnapshot = expectedSnapshot
            )
        }
        val archived = getDecryptedStringSnapshot(destination)
            ?: throw WalletSecretConcurrentMutationException(
                "A wallet-secret archive disappeared during preparation"
            )
        if (expectedSnapshot.matchesExactSnapshot(archived)) {
            return EncryptedPreferenceSnapshotMove(
                sourceKey = sourceKey,
                destinationKey = destination,
                expectedSnapshot = expectedSnapshot
            )
        }
    }
    throw WalletRecoveryStateIntegrityException(
        "Every wallet-secret archive slot for one key holds another value"
    )
}

/**
 * Where the value [plaintext] of [sourceKey] is kept before that key is
 * rewritten in place. [expectedState] is the slot's current state for the
 * compare-and-set; [valueToPut] is null when the slot already holds this
 * exact value, so a retried rewrite never archives it twice.
 */
class WalletSecretArchiveCopy(
    val destinationKey: String,
    val expectedState: EncryptedPreferenceSnapshot?,
    val valueToPut: String?
)

/**
 * Plans keeping [plaintext], the current value of [sourceKey], in the first
 * archive slot that is free or already holds that exact value. Returns null
 * when every slot holds another value; the caller then leaves [sourceKey] as
 * it is instead of rewriting it.
 */
fun EncryptedPreferences.planArchiveCopy(
    sourceKey: String,
    plaintext: String
): WalletSecretArchiveCopy? {
    for (slot in 0 until WalletSecretArchive.MAX_SLOTS) {
        val destination = WalletSecretArchive.keyFor(sourceKey, slot)
        if (!hasKey(destination)) {
            return WalletSecretArchiveCopy(
                destinationKey = destination,
                expectedState = null,
                valueToPut = plaintext
            )
        }
        val archived = getDecryptedStringSnapshot(destination)
            ?: throw WalletSecretConcurrentMutationException(
                "A wallet-secret archive disappeared during preparation"
            )
        if (archived.plaintext == plaintext) {
            return WalletSecretArchiveCopy(
                destinationKey = destination,
                expectedState = archived,
                valueToPut = null
            )
        }
    }
    return null
}
