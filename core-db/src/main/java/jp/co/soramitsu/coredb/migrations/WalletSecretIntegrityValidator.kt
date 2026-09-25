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

internal data class LegacySecretReplacement(
    val substratePlaintext: String,
    val ethereumPlaintext: String?
)

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
 */
internal object WalletSecretIntegrityValidator {

    fun validateLegacyAndPrepareReplacement(
        encoded: String,
        identity: WalletPublicIdentity,
        walletRootSecretValidation: WalletRootSecretValidation =
            WalletRootSecretValidator
    ): LegacySecretReplacement {
        return try {
            validateLegacyAndPrepareReplacementUnchecked(
                encoded = encoded,
                identity = identity,
                walletRootSecretValidation = walletRootSecretValidation
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: WalletPublicIdentityIntegrityException) {
            throw failure
        } catch (failure: WalletRootSecretCorruptionException) {
            throw WalletSecretIntegrityCorruptionException(
                "A legacy wallet secret failed identity-bound validation",
                failure
            )
        }
    }

    private fun validateLegacyAndPrepareReplacementUnchecked(
        encoded: String,
        identity: WalletPublicIdentity,
        walletRootSecretValidation: WalletRootSecretValidation
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
        val substrateStruct = oldSecrets.substrateKeypair
        val substrateKeypair = substrateStruct.toKeypair()

        val rawSubstratePath = oldSecrets.substrateDerivationPath
        val substratePath = rawSubstratePath.takeIf {
            entropy != null || seed != null
        }
        val ethereumStruct = oldSecrets.ethereumKeypair
        val ethereumKeypair = ethereumStruct?.toKeypair()
        val hasEthereumPublicKey = identity.ethereumPublicKey != null
        val hasEthereumAddress = identity.ethereumAddress != null
        ensureLegacyLocal(
            hasEthereumPublicKey == hasEthereumAddress,
            "A legacy public Ethereum identity is only partially present"
        )
        ensureLegacyLocal(
            (ethereumKeypair != null) == hasEthereumPublicKey,
            "Legacy Ethereum secret presence does not match its public identity"
        )
        if (
            hasEthereumPublicKey &&
            !checkNotNull(identity.ethereumPublicKey)
                .isValidEthereumCompressedPublicKey()
        ) {
            throw WalletPublicIdentityIntegrityException(
                "A legacy Ethereum database public key is not a valid " +
                    "compressed secp256k1 point"
            )
        }

        val rawEthereumPath = oldSecrets.ethereumDerivationPath
        val ethereumPath = rawEthereumPath.takeIf { entropy != null }
        if (ethereumKeypair == null) {
            ensureLegacyLocal(
                rawEthereumPath == null,
                "A legacy Ethereum path exists without an Ethereum keypair"
            )
        }

        val substratePlaintext = walletRootSecretValidation
            .validateSubstrateAndSanitize(
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
        val ethereumPlaintext = ethereumKeypair?.let {
            walletRootSecretValidation.validateEthereumAndSanitize(
                encoded = EthereumSecrets(
                    entropy = entropy,
                    seed = it.privateKey,
                    ethereumKeypair = it,
                    ethereumDerivationPath = ethereumPath
                ).toHexString(),
                expectedPublicKey = identity.ethereumPublicKey
                    ?: throw WalletPublicIdentityIntegrityException(
                        "A legacy Ethereum secret has no public identity"
                    ),
                expectedAddress = identity.ethereumAddress
                    ?: throw WalletPublicIdentityIntegrityException(
                        "A legacy Ethereum secret has no public address"
                    )
            )
        }

        return LegacySecretReplacement(
            substratePlaintext = substratePlaintext,
            ethereumPlaintext = ethereumPlaintext
        )
    }

    /**
     * Returns a canonical, sanitized plaintext. The signing keypair is always
     * retained; a path with no entropy or seed is removed because it cannot
     * prove any recovery relationship.
     */
    fun validateSubstrateV3(
        encoded: String,
        identity: WalletPublicIdentity
    ): String {
        return WalletRootSecretValidator.validateSubstrateAndSanitize(
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
        identity: WalletPublicIdentity
    ): String {
        return WalletRootSecretValidator.validateEthereumAndSanitize(
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
        identity: WalletPublicIdentity
    ): String {
        return WalletRootSecretValidator.validateTonAndSanitize(
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
