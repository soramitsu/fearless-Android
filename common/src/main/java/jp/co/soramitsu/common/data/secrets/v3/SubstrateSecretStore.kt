package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.mapKeypairStructToKeypair
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.authorizedReads
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.readValidatedWalletSecretOrQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.readWalletSecretOrQuarantine
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.fearless_utils.scale.toHexString

private const val SUBSTRATE_SECRETS = "SUBSTRATE_SECRETS"

class SubstrateSecretStore internal constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val walletRootSecretValidation: WalletRootSecretValidation
) : SecretStore<SubstrateSecrets> {

    constructor(encryptedPreferences: EncryptedPreferences) : this(
        encryptedPreferences = encryptedPreferences,
        walletRootSecretValidation = WalletRootSecretValidator
    )

    override fun put(metaId: Long, secrets: EncodableStruct<SubstrateSecrets>) {
        encryptedPreferences.putEncryptedString("$metaId:$SUBSTRATE_SECRETS", secrets.toHexString())
    }

    @Deprecated(
        message = "Unvalidated access is reserved for historical migrations; runtime callers must bind the durable identity"
    )
    override fun get(metaId: Long): EncodableStruct<SubstrateSecrets>? {
        return encryptedPreferences.readWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            decode = { encoded ->
                WalletSecretScalePreflight.requireSubstrateV3(encoded)
                SubstrateSecrets.read(encoded)
            }
        )
    }

    /**
     * Signing read: proves only that the stored keypair is the durable
     * identity. Recovery material is returned unproven; use
     * [getWithVerifiedRecovery] before exporting or deriving from it.
     */
    fun get(
        metaId: Long,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?,
        expectedAccountId: ByteArray?
    ): EncodableStruct<SubstrateSecrets>? {
        return encryptedPreferences.readValidatedWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            isLocalCorruption = { it is WalletRootSecretCorruptionException }
        ) { encoded ->
            val publicKey = expectedPublicKey ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable public key"
            )
            val cryptoType = expectedCryptoType ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable crypto type"
            )
            val accountId = expectedAccountId ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable account id"
            )
            val canonical = walletRootSecretValidation
                .validateSubstrateAndSanitize(
                    encoded = encoded,
                    expectedPublicKey = publicKey,
                    expectedCryptoType = cryptoType,
                    expectedAccountId = accountId
            )
            WalletSecretScalePreflight.requireSubstrateV3(canonical)
            SubstrateSecrets.read(canonical)
        }
    }

    /**
     * Like [get], plus whether the stored entropy, seed and path re-derive
     * the keypair. Only a signing-integrity failure quarantines the secret.
     */
    fun getWithAssessment(
        metaId: Long,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?,
        expectedAccountId: ByteArray?
    ): Pair<EncodableStruct<SubstrateSecrets>, RecoveryAssessment>? {
        return encryptedPreferences.readValidatedWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            isLocalCorruption = { it is WalletRootSecretCorruptionException }
        ) { encoded ->
            val publicKey = expectedPublicKey ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable public key"
            )
            val cryptoType = expectedCryptoType ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable crypto type"
            )
            val accountId = expectedAccountId ?: throw WalletPublicIdentityIntegrityException(
                "An active Substrate secret has no durable account id"
            )
            val validated = walletRootSecretValidation.validateSubstrate(
                encoded = encoded,
                expectedPublicKey = publicKey,
                expectedCryptoType = cryptoType,
                expectedAccountId = accountId
            )
            WalletSecretScalePreflight.requireSubstrateV3(validated.canonical)
            SubstrateSecrets.read(validated.canonical) to validated.assessment
        }
    }

    /**
     * For phrase, seed and backup consumers: always the signing keypair, but
     * the entropy, seed and path only when they re-derive it. Exporting
     * material that re-derives another key would restore a different account.
     */
    fun getWithVerifiedRecovery(
        metaId: Long,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?,
        expectedAccountId: ByteArray?
    ): EncodableStruct<SubstrateSecrets>? {
        val (secrets, assessment) = getWithAssessment(
            metaId = metaId,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        ) ?: return null
        return if (assessment is RecoveryAssessment.Unverified) {
            SubstrateSecrets(
                substrateKeyPair = mapKeypairStructToKeypair(secrets[SubstrateSecrets.SubstrateKeypair])
            )
        } else {
            secrets
        }
    }

    /** Presence only; actual mutation reads still validate identity and quarantine under their lease. */
    fun hasSecret(metaId: Long): Boolean = encryptedPreferences.hasKey(activeKey(metaId))

    fun getAuthorized(
        metaId: Long, expectedPublicKey: ByteArray?, expectedCryptoType: CryptoType?, expectedAccountId: ByteArray?,
        guard: MutationExecutionGuard, intentSha256: String
    ): EncodableStruct<SubstrateSecrets>? = SubstrateSecretStore(encryptedPreferences.authorizedReads(guard, intentSha256), walletRootSecretValidation)
        .get(metaId, expectedPublicKey, expectedCryptoType, expectedAccountId)

    private fun activeKey(metaId: Long) = "$metaId:$SUBSTRATE_SECRETS"
}

object SubstrateSecrets : Schema<SubstrateSecrets>() {
    val Entropy by byteArray().optional()
    val Seed by byteArray().optional()

    val SubstrateKeypair by schema(KeyPairSchema)
    val SubstrateDerivationPath by string().optional()
}

fun SubstrateSecrets(substrateKeyPair: Keypair,
                     entropy: ByteArray? = null,
                     seed: ByteArray? = null,
                     substrateDerivationPath: String? = null): EncodableStruct<SubstrateSecrets> = SubstrateSecrets { secrets ->
    secrets[Entropy] = entropy
    secrets[Seed] = seed

    secrets[SubstrateKeypair] = KeyPairSchema { keypair ->
        keypair[PublicKey] = substrateKeyPair.publicKey
        keypair[PrivateKey] = substrateKeyPair.privateKey
        keypair[Nonce] = (substrateKeyPair as? Sr25519Keypair)?.nonce
    }
    secrets[SubstrateDerivationPath] = substrateDerivationPath
}
