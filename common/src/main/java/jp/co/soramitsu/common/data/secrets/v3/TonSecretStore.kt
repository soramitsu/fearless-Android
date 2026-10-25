package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.readValidatedWalletSecretOrQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.readWalletSecretOrQuarantine
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.toHexString

private const val TON_SECRETS = "TON_SECRETS"

class TonSecretStore internal constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val walletRootSecretValidation: WalletRootSecretValidation
) : SecretStore<TonSecrets> {

    constructor(encryptedPreferences: EncryptedPreferences) : this(
        encryptedPreferences = encryptedPreferences,
        walletRootSecretValidation = WalletRootSecretValidator
    )

    override fun put(metaId: Long, secrets: EncodableStruct<TonSecrets>) {
        encryptedPreferences.putEncryptedString("$metaId:$TON_SECRETS", secrets.toHexString())
    }

    @Deprecated(
        message = "Unvalidated access is reserved for historical migrations; runtime callers must bind the durable identity"
    )
    override fun get(metaId: Long): EncodableStruct<TonSecrets>? {
        return encryptedPreferences.readWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            decode = { encoded ->
                WalletSecretScalePreflight.requireTonV3(encoded)
                TonSecrets.read(encoded)
            }
        )
    }

    /**
     * Signing read: proves only that the stored keypair is the durable
     * identity. The stored phrase is returned unproven; use
     * [getWithVerifiedRecovery] before exporting or deriving from it.
     */
    fun get(
        metaId: Long,
        expectedPublicKey: ByteArray?
    ): EncodableStruct<TonSecrets>? {
        return encryptedPreferences.readValidatedWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            isLocalCorruption = { it is WalletRootSecretCorruptionException }
        ) { encoded ->
            val publicKey = expectedPublicKey ?: throw WalletPublicIdentityIntegrityException(
                "An active TON secret has no durable public key"
            )
            val canonical = walletRootSecretValidation.validateTonAndSanitize(
                encoded = encoded,
                expectedPublicKey = publicKey
            )
            WalletSecretScalePreflight.requireTonV3(canonical)
            TonSecrets.read(canonical)
        }
    }

    /**
     * Like [get], plus whether the stored phrase re-derives the keypair. Only
     * a signing-integrity failure quarantines the secret.
     */
    fun getWithAssessment(
        metaId: Long,
        expectedPublicKey: ByteArray?
    ): Pair<EncodableStruct<TonSecrets>, RecoveryAssessment>? {
        return encryptedPreferences.readValidatedWalletSecretOrQuarantine(
            activeSecretKey = activeKey(metaId),
            isLocalCorruption = { it is WalletRootSecretCorruptionException }
        ) { encoded ->
            val publicKey = expectedPublicKey ?: throw WalletPublicIdentityIntegrityException(
                "An active TON secret has no durable public key"
            )
            val validated = walletRootSecretValidation.validateTon(
                encoded = encoded,
                expectedPublicKey = publicKey
            )
            WalletSecretScalePreflight.requireTonV3(validated.canonical)
            TonSecrets.read(validated.canonical) to validated.assessment
        }
    }

    /**
     * For phrase and backup consumers: the stored text exactly as it was
     * typed, whenever it re-derives the keypair. 4.x derived TON keys from the
     * text as typed, so a phrase with extra spaces or capitals
     * ([RecoveryAssessment.VerifiedAsTyped]) is the only phrase that restores
     * this account and is handed out byte for byte, never normalized; its
     * export should tell the user to type it exactly. A TON secret has no
     * keypair-only form, so a phrase that re-derives nothing is withheld; the
     * key itself keeps signing through [get].
     */
    fun getWithVerifiedRecovery(
        metaId: Long,
        expectedPublicKey: ByteArray?
    ): EncodableStruct<TonSecrets>? {
        val (secrets, assessment) = getWithAssessment(
            metaId = metaId,
            expectedPublicKey = expectedPublicKey
        ) ?: return null
        return secrets.takeUnless { assessment is RecoveryAssessment.Unverified }
    }

    private fun activeKey(metaId: Long) = "$metaId:$TON_SECRETS"
}

object TonSecrets : Schema<TonSecrets>() {
    val Seed by byteArray()
    val PrivateKey by byteArray()
    val PublicKey by byteArray()
}

fun TonSecrets(seed: ByteArray, tonKeypair: Keypair): EncodableStruct<TonSecrets> = TonSecrets { secrets ->
    secrets[Seed] = seed
    secrets[PrivateKey] = tonKeypair.privateKey
    secrets[PublicKey] = tonKeypair.publicKey
}
