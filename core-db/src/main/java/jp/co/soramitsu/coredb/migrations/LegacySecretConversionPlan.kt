package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.planArchiveMove
import jp.co.soramitsu.common.data.storage.encrypt.replaceEncryptedStringsForStatesDurably
import jp.co.soramitsu.common.data.storage.encrypt.requireSnapshotMovesReady

/**
 * One durable replacement for a legacy `<id>:ACCESS_SECRETS` payload: the V3
 * entries to publish and where the exact legacy ciphertext goes.
 *
 * The legacy ciphertext is never removed. It is archived once every
 * ecosystem it served has a key that proves its identity, converted from it
 * or already active. Otherwise it moves to the quarantine of the ecosystem
 * whose key failed signing integrity, so the other ecosystem keeps signing.
 */
internal class LegacySecretConversionPlan(
    val expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
    val valuesToPut: Map<String, String>,
    /** Null when the legacy payload already sits where it belongs. */
    val legacyMove: EncryptedPreferenceSnapshotMove?
) {

    val isNoOp: Boolean
        get() = valuesToPut.isEmpty() && legacyMove == null

    fun requireReady(encryptedPreferences: EncryptedPreferences) {
        legacyMove?.let {
            encryptedPreferences.requireSnapshotMovesReady(listOf(it))
        }
        expectedStates.forEach { (key, expected) ->
            val actual = if (encryptedPreferences.hasKey(key)) {
                encryptedPreferences.getDecryptedStringSnapshot(key)
                    ?: throw WalletSecretConcurrentMutationException(
                        "A legacy wallet-secret target disappeared during preparation"
                    )
            } else {
                null
            }
            val matches = if (expected == null) {
                actual == null
            } else {
                actual != null && expected.matchesExactSnapshot(actual)
            }
            if (!matches) {
                throw WalletSecretConcurrentMutationException(
                    "A legacy wallet-secret target changed during preparation"
                )
            }
        }
    }

    fun commit(encryptedPreferences: EncryptedPreferences) {
        if (isNoOp) return
        encryptedPreferences.replaceEncryptedStringsForStatesDurably(
            expectedStates = expectedStates,
            valuesToPut = valuesToPut,
            keysToRemove = emptySet(),
            snapshotMoves = listOfNotNull(legacyMove)
        )
    }
}

/**
 * Plans the conversion of the legacy payload [legacySnapshot], stored under
 * [legacySourceKey] (the active `ACCESS_SECRETS` key, or a quarantine or
 * archive key that holds that payload), for the wallet [identity].
 *
 * An existing V3 entry is never overwritten. When it differs from the
 * conversion it stays active if it proves the identity; an existing entry
 * that proves nothing leaves that ecosystem unconverted, so the legacy
 * payload is kept in quarantine rather than failing the upgrade. With
 * [legacyMovable] false the legacy payload stays where it is and only the
 * missing V3 entries are published.
 */
internal fun EncryptedPreferences.planLegacySecretConversion(
    identity: WalletPublicIdentity,
    legacySourceKey: String,
    legacySnapshot: EncryptedPreferenceSnapshot,
    replacement: LegacySecretReplacement,
    walletRootSecretValidation: WalletRootSecretValidation,
    legacyMovable: Boolean = true
): LegacySecretConversionPlan {
    val metaId = identity.metaId
    val accessKey = "$metaId:ACCESS_SECRETS"
    val substrateKey = "$metaId:SUBSTRATE_SECRETS"
    val ethereumKey = "$metaId:ETHEREUM_SECRETS"
    val expectedStates = linkedMapOf<String, EncryptedPreferenceSnapshot?>()
    val valuesToPut = linkedMapOf<String, String>()

    fun resolve(
        targetKey: String,
        outcome: LegacyRootOutcome,
        provesIdentity: (String) -> Boolean
    ): Boolean {
        if (outcome is LegacyRootOutcome.Absent) return true
        val existing = if (hasKey(targetKey)) {
            getDecryptedStringSnapshot(targetKey)
                ?: throw WalletSecretConcurrentMutationException(
                    "A legacy wallet-secret target disappeared during preparation"
                )
        } else {
            null
        }
        val converted = (outcome as? LegacyRootOutcome.Converted)?.plaintext
        return when {
            existing == null && converted != null -> {
                expectedStates[targetKey] = null
                valuesToPut[targetKey] = converted
                true
            }

            existing == null -> false

            existing.plaintext == converted || provesIdentity(existing.plaintext) -> {
                // The active V3 entry wins; the legacy copy is retained below.
                expectedStates[targetKey] = existing
                true
            }

            // An entry that proves nothing is never overwritten, and it must
            // not stop the upgrade either: the legacy payload is quarantined
            // for this ecosystem, the 76 -> 77 pass quarantines the failing
            // entry, and the startup revalidation then converts the payload
            // into the key slot that entry left free.
            else -> false
        }
    }

    val substrateSatisfied = resolve(substrateKey, replacement.substrate) { plaintext ->
        provesIdentity {
            walletRootSecretValidation.validateSubstrateAndSanitize(
                encoded = plaintext,
                expectedPublicKey = checkNotNull(identity.substratePublicKey),
                expectedCryptoType = checkNotNull(identity.substrateCryptoType),
                expectedAccountId = checkNotNull(identity.substrateAccountId)
            )
        }
    }
    val ethereumSatisfied = resolve(ethereumKey, replacement.ethereum) { plaintext ->
        provesIdentity {
            walletRootSecretValidation.validateEthereumAndSanitize(
                encoded = plaintext,
                expectedPublicKey = identity.ethereumPublicKey
                    ?: throw WalletPublicIdentityIntegrityException(
                        "An Ethereum secret has no durable public key"
                    ),
                expectedAddress = identity.ethereumAddress
                    ?: throw WalletPublicIdentityIntegrityException(
                        "An Ethereum secret has no durable address"
                    )
            )
        }
    }

    val accessQuarantineKey = WalletSecretQuarantine.keyFor(accessKey)
    val ethereumQuarantineKey = WalletSecretQuarantine.keyFor(ethereumKey)
    val legacyDestination = when {
        substrateSatisfied && ethereumSatisfied -> null
        // Only the Ethereum key failed: scope the payload to Ethereum so
        // the converted Substrate key keeps signing.
        substrateSatisfied &&
            !hasKey(ethereumKey) &&
            canReceive(ethereumQuarantineKey, legacySnapshot) ->
            ethereumQuarantineKey

        else -> accessQuarantineKey
    }
    val legacyMove = when {
        !legacyMovable || legacyDestination == legacySourceKey -> null
        legacyDestination != null -> EncryptedPreferenceSnapshotMove(
            sourceKey = legacySourceKey,
            destinationKey = legacyDestination,
            expectedSnapshot = legacySnapshot
        )

        WalletSecretArchive.sourceKeyOf(legacySourceKey) == accessKey -> null
        else -> planArchiveMove(
            sourceKey = legacySourceKey,
            expectedSnapshot = legacySnapshot,
            archiveNameKey = accessKey
        )
    }
    return LegacySecretConversionPlan(
        expectedStates = expectedStates,
        valuesToPut = valuesToPut,
        legacyMove = legacyMove
    )
}

private fun EncryptedPreferences.canReceive(
    destinationKey: String,
    snapshot: EncryptedPreferenceSnapshot
): Boolean {
    if (!hasKey(destinationKey)) return true
    val existing = getDecryptedStringSnapshot(destinationKey) ?: return false
    return snapshot.matchesExactSnapshot(existing)
}

private inline fun provesIdentity(validate: () -> String): Boolean {
    return try {
        validate()
        true
    } catch (_: WalletRootSecretCorruptionException) {
        false
    }
}
