package jp.co.soramitsu.common.data.secrets.v2

import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.authorizedReads
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.quarantineEncryptedStringSnapshotDurably
import jp.co.soramitsu.common.data.storage.encrypt.readWalletSecretOrQuarantine
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ACCESS_SECRETS = "ACCESS_SECRETS"

class SecretStoreV2 internal constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val chainAccountSecretValidation: ChainAccountSecretValidation
) {

    constructor(encryptedPreferences: EncryptedPreferences) : this(
        encryptedPreferences = encryptedPreferences,
        chainAccountSecretValidation = ChainAccountSecretValidator
    )

    suspend fun putMetaAccountSecrets(metaId: Long, secrets: EncodableStruct<MetaAccountSecrets>) = withContext(Dispatchers.IO) {
        encryptedPreferences.putEncryptedString(metaAccountKey(metaId, ACCESS_SECRETS), secrets.toHexString())
    }

    suspend fun getMetaAccountSecrets(metaId: Long): EncodableStruct<MetaAccountSecrets>? = withContext(Dispatchers.IO) {
        encryptedPreferences.readWalletSecretOrQuarantine(
            activeSecretKey = metaAccountKey(metaId, ACCESS_SECRETS),
            decode = { encoded ->
                WalletSecretScalePreflight.requireMetaAccountV2(encoded)
                MetaAccountSecrets.read(encoded)
            }
        )
    }

    suspend fun putChainAccountSecrets(metaId: Long, accountId: ByteArray, secrets: EncodableStruct<ChainAccountSecrets>) = withContext(Dispatchers.IO) {
        encryptedPreferences.putEncryptedString(chainAccountKey(metaId, accountId, ACCESS_SECRETS), secrets.toHexString())
    }

    suspend fun getChainAccountSecrets(metaId: Long, accountId: ByteArray): EncodableStruct<ChainAccountSecrets>? = withContext(Dispatchers.IO) {
        readValidatedChainAccountSecrets(
            metaId = metaId,
            accountId = accountId,
            expectedPublicKey = null,
            expectedCryptoType = null
        )
    }

    suspend fun getChainAccountSecrets(
        metaId: Long,
        accountId: ByteArray,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType
    ): EncodableStruct<ChainAccountSecrets>? = withContext(Dispatchers.IO) {
        readValidatedChainAccountSecrets(
            metaId = metaId,
            accountId = accountId,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType
        )
    }

    /**
     * Like [getChainAccountSecrets], plus whether the stored entropy, seed and
     * path re-derive the keypair. Only a signing-integrity failure quarantines.
     */
    suspend fun getChainAccountSecretsWithAssessment(
        metaId: Long,
        accountId: ByteArray,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType
    ): Pair<EncodableStruct<ChainAccountSecrets>, RecoveryAssessment>? = withContext(Dispatchers.IO) {
        readValidatedChainAccountSecret(metaId, accountId) { encoded ->
            val validated = chainAccountSecretValidation.validate(
                encoded = encoded,
                expectedAccountId = accountId,
                expectedPublicKey = expectedPublicKey,
                expectedCryptoType = expectedCryptoType
            )
            ChainAccountSecrets.read(validated.canonical) to validated.assessment
        }
    }

    /**
     * For phrase, seed and backup consumers: always the signing keypair, but
     * the entropy, seed and path only when they re-derive it. Exporting
     * material that re-derives another key would restore a different account.
     */
    suspend fun getChainAccountSecretsWithVerifiedRecovery(
        metaId: Long,
        accountId: ByteArray,
        expectedPublicKey: ByteArray,
        expectedCryptoType: CryptoType
    ): EncodableStruct<ChainAccountSecrets>? {
        val (secrets, assessment) = getChainAccountSecretsWithAssessment(
            metaId = metaId,
            accountId = accountId,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType
        ) ?: return null
        return if (assessment is RecoveryAssessment.Unverified) {
            ChainAccountSecrets(
                keyPair = mapKeypairStructToKeypair(secrets[ChainAccountSecrets.Keypair])
            )
        } else {
            secrets
        }
    }

    /** Recheck only after switching dispatchers, directly around the synchronous secret read. */
    suspend fun getAuthorizedChainAccountKeypair(
        metaId: Long, accountId: ByteArray, expectedPublicKey: ByteArray, expectedCryptoType: CryptoType,
        guard: MutationExecutionGuard, intentSha256: String
    ): Keypair = withContext(Dispatchers.IO) {
        guard.check(intentSha256)
        val store = SecretStoreV2(encryptedPreferences.authorizedReads(guard, intentSha256), chainAccountSecretValidation)
        val secrets = requireNotNull(store.readValidatedChainAccountSecrets(metaId, accountId, expectedPublicKey, expectedCryptoType))
        mapKeypairStructToKeypair(secrets[ChainAccountSecrets.Keypair])
    }

    suspend fun hasChainSecrets(metaId: Long, accountId: ByteArray) = withContext(Dispatchers.Default) {
        encryptedPreferences.hasKey(chainAccountKey(metaId, accountId, ACCESS_SECRETS))
    }

    suspend fun clearSecrets(metaId: Long, chainAccountIds: List<AccountId>) = withContext(Dispatchers.Default) {
        chainAccountIds.map { chainAccountKey(metaId, it, ACCESS_SECRETS) }
            .onEach(encryptedPreferences::removeKey)

        encryptedPreferences.removeKey(metaAccountKey(metaId, ACCESS_SECRETS))
    }

    private fun chainAccountKey(metaId: Long, accountId: ByteArray, secretName: String) = "$metaId:${accountId.toHexString()}:$secretName"

    private fun metaAccountKey(metaId: Long, secretName: String) = "$metaId:$secretName"

    /** Signing read: proves only that the stored keypair is the durable identity. */
    private fun readValidatedChainAccountSecrets(
        metaId: Long,
        accountId: ByteArray,
        expectedPublicKey: ByteArray?,
        expectedCryptoType: CryptoType?
    ): EncodableStruct<ChainAccountSecrets>? {
        return readValidatedChainAccountSecret(metaId, accountId) { encoded ->
            val canonical = chainAccountSecretValidation.validateAndSanitize(
                encoded = encoded,
                expectedAccountId = accountId,
                expectedPublicKey = expectedPublicKey,
                expectedCryptoType = expectedCryptoType
            )
            ChainAccountSecrets.read(canonical)
        }
    }

    private fun <T : Any> readValidatedChainAccountSecret(
        metaId: Long,
        accountId: ByteArray,
        decodeAndValidate: (String) -> T
    ): T? {
        val activeKey = chainAccountKey(metaId, accountId, ACCESS_SECRETS)
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        if (encryptedPreferences.hasKey(quarantineKey)) {
            throw WalletRecoveryRequiredException()
        }
        if (!encryptedPreferences.hasKey(activeKey)) return null

        // Storage/provider access stays outside the payload-validation catch.
        // A transient read failure is not evidence that this ciphertext is
        // corrupt and must never move one wallet into quarantine.
        val snapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(activeKey)
        ) {
            "A chain-account secret disappeared during validation"
        }
        val encoded = snapshot.plaintext

        val validated = try {
            decodeAndValidate(encoded)
        } catch (failure: ChainAccountSecretCorruptionException) {
            null
        }

        if (validated == null) {
            // Keep the durable move outside the validation catch: a commit
            // failure is global/transient and must propagate unchanged.
            encryptedPreferences.quarantineEncryptedStringSnapshotDurably(
                sourceKey = activeKey,
                quarantineKey = quarantineKey,
                expectedSnapshot = snapshot
            )
            throw WalletRecoveryRequiredException()
        }

        return validated
    }
}

suspend fun SecretStoreV2.getChainAccountKeypair(
    metaId: Long,
    accountId: ByteArray,
    expectedPublicKey: ByteArray,
    expectedCryptoType: CryptoType
): Keypair = withContext(Dispatchers.Default) {
    val secrets = getChainAccountSecrets(
        metaId = metaId,
        accountId = accountId,
        expectedPublicKey = expectedPublicKey,
        expectedCryptoType = expectedCryptoType
    ) ?: error("No secrets found for meta account $metaId for account ${accountId.toHexString()}")

    val keypairStruct = secrets[ChainAccountSecrets.Keypair]

    mapKeypairStructToKeypair(keypairStruct)
}


fun mapKeypairStructToKeypair(struct: EncodableStruct<KeyPairSchema>): Keypair {
    return Keypair(
        publicKey = struct[KeyPairSchema.PublicKey],
        privateKey = struct[KeyPairSchema.PrivateKey],
        nonce = struct[KeyPairSchema.Nonce]
    )
}
