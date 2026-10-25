package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.WalletMetaAccountScaleLayout
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.isValidEthereumCompressedPublicKey
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.toHexString

internal data class WalletPublicIdentity(
    val metaId: Long,
    val substratePublicKey: ByteArray?,
    val substrateCryptoType: CryptoType?,
    val substrateCryptoTypeWasPresent: Boolean,
    val substrateAccountId: ByteArray?,
    val ethereumPublicKey: ByteArray?,
    val ethereumAddress: ByteArray?,
    val tonPublicKey: ByteArray?
) {
    val hasCompleteSubstrateIdentity: Boolean
        get() = substratePublicKey != null &&
            substrateCryptoType != null &&
            substrateAccountId != null

    val hasPartialSubstrateIdentity: Boolean
        get() = (
            substratePublicKey != null ||
                substrateCryptoTypeWasPresent ||
                substrateAccountId != null
            ) && !hasCompleteSubstrateIdentity

    val hasCompleteEthereumIdentity: Boolean
        get() = ethereumPublicKey != null && ethereumAddress != null

    val hasPartialEthereumIdentity: Boolean
        get() = (ethereumPublicKey != null || ethereumAddress != null) &&
            !hasCompleteEthereumIdentity
}

/**
 * What the legacy conversion produced for one root ecosystem. Each ecosystem
 * is judged on its own, so a key that fails signing integrity never stops the
 * other ecosystem's key from converting.
 */
internal sealed interface LegacyRootOutcome {

    /** The legacy keypair proves the durable identity; [plaintext] is its V3 payload. */
    data class Converted(val plaintext: String) : LegacyRootOutcome

    /**
     * The legacy payload holds no key this ecosystem can use: none was stored
     * (a raw-seed import that declined Ethereum, or a 4.x wallet whose
     * Ethereum key lives in its own V3 entry), or the stored key belongs to no
     * recorded identity. Whatever was stored stays in the archived payload.
     */
    data object Absent : LegacyRootOutcome

    /** The legacy keypair for this ecosystem fails signing integrity. */
    data class Corrupt(val cause: Exception) : LegacyRootOutcome
}

internal data class LegacySecretReplacement(
    val substrate: LegacyRootOutcome,
    val ethereum: LegacyRootOutcome
) {
    val substratePlaintext: String?
        get() = (substrate as? LegacyRootOutcome.Converted)?.plaintext

    val ethereumPlaintext: String?
        get() = (ethereum as? LegacyRootOutcome.Converted)?.plaintext
}

internal class WalletSecretIntegrityCorruptionException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Pure, side-effect-free validation shared by the historical 71 -> 72
 * conversion and the 76 -> 77 integrity pass.
 *
 * Callers deliberately perform preference writes and quarantines only after
 * these methods return or throw so a durable-storage failure can never be
 * misclassified as corruption in one wallet payload.
 *
 * Only signing integrity is checked: a keypair that is its durable public
 * identity and proves ownership converts and stays active even when its
 * entropy, seed or path does not re-derive it (a TON phrase stored as typed,
 * an SR25519 soft path, a historical writer quirk). The recovery material is
 * kept verbatim; export and backup judge it by its RecoveryAssessment.
 *
 * A legacy payload is judged per ecosystem: the Substrate and Ethereum
 * keypairs convert or fail independently, and a path stored without the
 * keypair it would derive is dropped rather than treated as corruption.
 */
internal object WalletSecretIntegrityValidator {

    /**
     * Throws [WalletSecretIntegrityCorruptionException] only when the payload
     * itself cannot be decoded, and [WalletPublicIdentityIntegrityException]
     * when the durable identity cannot judge it. Provider failures propagate
     * as [WalletSecureStorageUnavailableException]. Everything else is a
     * per-ecosystem [LegacyRootOutcome].
     */
    fun validateLegacyAndPrepareReplacement(
        encoded: String,
        identity: WalletPublicIdentity,
        walletRootSecretValidation: WalletRootSecretValidation =
            WalletRootSecretValidator
    ): LegacySecretReplacement {
        ensureLegacyLocal(
            encoded.isNotEmpty() &&
                encoded.length <= MAX_SECRET_PLAINTEXT_CHARS,
            "A legacy wallet secret is empty or exceeds the safe decode limit"
        )
        val oldSecrets = decodeLegacySecrets(encoded)

        val substratePublicKey = identity.substratePublicKey
            ?: throw WalletPublicIdentityIntegrityException(
                "A legacy Substrate secret has no public identity"
            )
        val substrateCryptoType = identity.substrateCryptoType
            ?: throw WalletPublicIdentityIntegrityException(
                "A legacy Substrate secret has no public crypto type"
            )
        val substrateAccountId = identity.substrateAccountId
            ?: throw WalletPublicIdentityIntegrityException(
                "A legacy Substrate secret has no public account id"
            )

        val entropy = oldSecrets.entropy
        val seed = oldSecrets.seed
        val substrateKeypair = oldSecrets.substrateKeypair.toKeypair()

        val rawSubstratePath = oldSecrets.substrateDerivationPath
        val substratePath = rawSubstratePath.takeIf {
            entropy != null || seed != null
        }
        val ethereumKeypair = oldSecrets.ethereumKeypair?.toKeypair()
        val ethereumPublicKey = identity.ethereumPublicKey
        val ethereumAddress = identity.ethereumAddress
        if ((ethereumPublicKey == null) != (ethereumAddress == null)) {
            throw WalletPublicIdentityIntegrityException(
                "A legacy public Ethereum identity is only partially present"
            )
        }
        if (
            ethereumPublicKey != null &&
            !ethereumPublicKey.isValidEthereumCompressedPublicKey()
        ) {
            throw WalletPublicIdentityIntegrityException(
                "A legacy Ethereum database public key is not a valid " +
                    "compressed secp256k1 point"
            )
        }

        val substrate = convertedOrCorrupt {
            walletRootSecretValidation.validateSubstrateAndSanitize(
                encoded = SubstrateSecrets(
                    substrateKeyPair = substrateKeypair,
                    entropy = entropy,
                    seed = seed,
                    substrateDerivationPath = substratePath
                ).toHexString(),
                expectedPublicKey = substratePublicKey,
                expectedCryptoType = substrateCryptoType,
                expectedAccountId = substrateAccountId
            )
        }
        // 2.0.2-3.7.4 raw-seed imports stored the default Ethereum path even
        // when the user declined the Ethereum account, and 4.x could add an
        // Ethereum key to a pre-4.0 wallet in its own V3 entry. Neither is
        // corruption: with no legacy keypair, or no identity to bind it to,
        // there is nothing to convert, and the archived payload keeps
        // whatever was stored.
        val ethereum = if (
            ethereumKeypair == null ||
            ethereumPublicKey == null ||
            ethereumAddress == null
        ) {
            LegacyRootOutcome.Absent
        } else {
            convertedOrCorrupt {
                walletRootSecretValidation.validateEthereumAndSanitize(
                    encoded = EthereumSecrets(
                        entropy = entropy,
                        seed = ethereumKeypair.privateKey,
                        ethereumKeypair = ethereumKeypair,
                        ethereumDerivationPath =
                        oldSecrets.ethereumDerivationPath.takeIf { entropy != null }
                    ).toHexString(),
                    expectedPublicKey = ethereumPublicKey,
                    expectedAddress = ethereumAddress
                )
            }
        }

        return LegacySecretReplacement(
            substrate = substrate,
            ethereum = ethereum
        )
    }

    private inline fun convertedOrCorrupt(
        validate: () -> String
    ): LegacyRootOutcome {
        return try {
            LegacyRootOutcome.Converted(validate())
        } catch (failure: WalletRootSecretCorruptionException) {
            LegacyRootOutcome.Corrupt(failure)
        }
    }

    /**
     * Returns a canonical, sanitized plaintext. The signing keypair is always
     * retained; a path with no entropy or seed is removed because it cannot
     * prove any recovery relationship.
     */
    fun validateSubstrateV3(
        encoded: String,
        identity: WalletPublicIdentity,
        walletRootSecretValidation: WalletRootSecretValidation =
            WalletRootSecretValidator
    ): String {
        return walletRootSecretValidation.validateSubstrateAndSanitize(
            encoded = encoded,
            expectedPublicKey = identity.substratePublicKey
                ?: throw WalletPublicIdentityIntegrityException(
                    "A complete Substrate public identity is required for validation"
                ),
            expectedCryptoType = identity.substrateCryptoType
                ?: throw WalletPublicIdentityIntegrityException(
                    "A complete Substrate crypto type is required for validation"
                ),
            expectedAccountId = identity.substrateAccountId
                ?: throw WalletPublicIdentityIntegrityException(
                    "A complete Substrate account id is required for validation"
                )
        )
    }

    /**
     * Returns a canonical, sanitized plaintext. Ethereum derivation paths are
     * meaningful only with entropy; direct-import private-key signing remains
     * intact while an unbound path is removed.
     */
    fun validateEthereumV3(
        encoded: String,
        identity: WalletPublicIdentity,
        walletRootSecretValidation: WalletRootSecretValidation =
            WalletRootSecretValidator
    ): String {
        return walletRootSecretValidation.validateEthereumAndSanitize(
            encoded = encoded,
            expectedPublicKey = identity.ethereumPublicKey
                ?: throw WalletPublicIdentityIntegrityException(
                    "A complete Ethereum public identity is required for validation"
                ),
            expectedAddress = identity.ethereumAddress
                ?: throw WalletPublicIdentityIntegrityException(
                    "A complete Ethereum address is required for validation"
                )
        )
    }

    fun validateTonV3(
        encoded: String,
        identity: WalletPublicIdentity,
        walletRootSecretValidation: WalletRootSecretValidation =
            WalletRootSecretValidator
    ): String {
        return walletRootSecretValidation.validateTonAndSanitize(
            encoded = encoded,
            expectedPublicKey = identity.tonPublicKey
                ?: throw WalletPublicIdentityIntegrityException(
                    "A TON public identity is required for validation"
                )
        )
    }

    private fun EncodableStruct<KeyPairSchema>.toKeypair(): FearlessKeypair {
        return Keypair(
            publicKey = this[KeyPairSchema.PublicKey],
            privateKey = this[KeyPairSchema.PrivateKey],
            nonce = this[KeyPairSchema.Nonce]
        )
    }

    private fun decodeLegacySecrets(encoded: String): DecodedLegacySecrets {
        return try {
            when (
                WalletSecretScalePreflight
                    .requireMetaAccountV2OrLegacyV69(encoded)
            ) {
                WalletMetaAccountScaleLayout.LEGACY_V69 -> {
                    val decoded = TonMigration.MetaAccountSecretsV69.read(encoded)
                    DecodedLegacySecrets(
                        entropy =
                        decoded[TonMigration.MetaAccountSecretsV69.Entropy],
                        seed = decoded[TonMigration.MetaAccountSecretsV69.Seed],
                        substrateKeypair =
                        decoded[
                            TonMigration.MetaAccountSecretsV69.SubstrateKeypair
                        ],
                        substrateDerivationPath =
                        decoded[
                            TonMigration.MetaAccountSecretsV69
                                .SubstrateDerivationPath
                        ],
                        ethereumKeypair =
                        decoded[
                            TonMigration.MetaAccountSecretsV69.EthereumKeypair
                        ],
                        ethereumDerivationPath =
                        decoded[
                            TonMigration.MetaAccountSecretsV69
                                .EthereumDerivationPath
                        ]
                    )
                }

                WalletMetaAccountScaleLayout.CURRENT_V2 -> {
                    val decoded = MetaAccountSecrets.read(encoded)
                    ensureLegacyLocal(
                        decoded[MetaAccountSecrets.TonKeypair] == null,
                        "A pre-TON wallet payload contains a TON keypair"
                    )
                    DecodedLegacySecrets(
                        entropy = decoded[MetaAccountSecrets.Entropy],
                        seed = decoded[MetaAccountSecrets.Seed],
                        substrateKeypair =
                        decoded[MetaAccountSecrets.SubstrateKeypair],
                        substrateDerivationPath =
                        decoded[MetaAccountSecrets.SubstrateDerivationPath],
                        ethereumKeypair =
                        decoded[MetaAccountSecrets.EthereumKeypair],
                        ethereumDerivationPath =
                        decoded[MetaAccountSecrets.EthereumDerivationPath]
                    )
                }
            }
        } catch (failure: WalletSecretIntegrityCorruptionException) {
            throw failure
        } catch (failure: Exception) {
            throw WalletSecretIntegrityCorruptionException(
                "Unable to decode a bounded legacy wallet secret",
                failure
            )
        }
    }

    private data class DecodedLegacySecrets(
        val entropy: ByteArray?,
        val seed: ByteArray?,
        val substrateKeypair: EncodableStruct<KeyPairSchema>,
        val substrateDerivationPath: String?,
        val ethereumKeypair: EncodableStruct<KeyPairSchema>?,
        val ethereumDerivationPath: String?
    )

    private fun ensureLegacyLocal(condition: Boolean, message: String) {
        if (!condition) {
            throw WalletSecretIntegrityCorruptionException(message)
        }
    }

    private const val MAX_SECRET_PLAINTEXT_CHARS = 1_048_576
}
