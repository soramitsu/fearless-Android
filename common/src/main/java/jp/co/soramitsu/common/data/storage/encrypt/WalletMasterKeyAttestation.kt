package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.secrets.WalletMetaAccountScaleLayout
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v1.SourceInternal
import jp.co.soramitsu.common.data.secrets.v1.SourceType
import jp.co.soramitsu.common.data.secrets.legacy.LegacyWalletV04Secrets
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import org.bouncycastle.util.encoders.Hex

/**
 * Authenticates the unwrapped device-wide AES key before Room is allowed to
 * migrate wallet rows.
 *
 * Older releases did not have a sentinel, so the first hardened launch may
 * prove the key against a PIN or a structurally valid wallet-secret payload.
 * Once proven, later launches use the authenticated GCM sentinel.
 */
internal object WalletMasterKeyAttestation {

    const val SENTINEL_KEY = "wallet_master_key_attestation_v1"
    const val SENTINEL_PLAINTEXT = "fearless-wallet-master-key:v1"

    private val legacySecretKey = Regex("^security_source_.+")
    private val metaAccessSecretKey = Regex("^\\d+:ACCESS_SECRETS$")
    private val chainAccessSecretKey = Regex("^\\d+:[^:]+:ACCESS_SECRETS$")
    private val substrateSecretKey = Regex("^\\d+:SUBSTRATE_SECRETS$")
    private val ethereumSecretKey = Regex("^\\d+:ETHEREUM_SECRETS$")
    private val tonSecretKey = Regex("^\\d+:TON_SECRETS$")
    private val tonConnectKey = Regex("^TON_CONNECT_.+")
    private val legacyV1QuarantineKey =
        Regex("^legacy_v1_public_([0-9a-f]+)$")
    private val legacyV1MetaQuarantineKey =
        Regex("^legacy_v1_meta_\\d+_public_([0-9a-f]+)$")
    private val legacyV04MetaQuarantineKey =
        Regex("^legacy_v04_meta_\\d+_public_([0-9a-f]+)$")

    fun isProtectedPayloadKey(key: String): Boolean {
        return key == SENTINEL_KEY ||
            key == PIN_CODE_KEY ||
            legacySecretKey.matches(key) ||
            metaAccessSecretKey.matches(key) ||
            chainAccessSecretKey.matches(key) ||
            substrateSecretKey.matches(key) ||
            ethereumSecretKey.matches(key) ||
            tonSecretKey.matches(key) ||
            tonConnectKey.matches(key) ||
            LegacyWalletV04Secrets.isLegacyKey(key) ||
            key == MUTATION_JOURNAL_KEY ||
            key.startsWith(MUTATION_STAGE_PREFIX) ||
            key.startsWith(MUTATION_BACKUP_PREFIX) ||
            key.startsWith(WalletPublicIdentityRecovery.KEY_PREFIX) ||
            key.startsWith(QUARANTINE_PREFIX)
    }

    fun isAuthenticationCandidateKey(key: String): Boolean {
        return key != SENTINEL_KEY &&
            isProtectedPayloadKey(key)
    }

    /**
     * Returns the original active key whose schema can validate a legacy CBC
     * payload. Modern GCM payloads authenticate the key cryptographically and
     * do not need this schema lookup.
     */
    fun semanticValidationKey(key: String): String? {
        val candidate = if (key.startsWith(QUARANTINE_PREFIX)) {
            key.removePrefix(QUARANTINE_PREFIX)
        } else {
            key
        }

        if (
            legacyV1QuarantineKey.matches(candidate) ||
            legacyV1MetaQuarantineKey.matches(candidate) ||
            legacyV04MetaQuarantineKey.matches(candidate)
        ) {
            return candidate
        }

        return candidate.takeIf {
            it != SENTINEL_KEY &&
                !it.startsWith(QUARANTINE_PREFIX) &&
                isProtectedPayloadKey(it)
        }
    }

    fun isSemanticallyValidLegacyCandidate(
        key: String,
        plaintext: String
    ): Boolean {
        if (plaintext.isEmpty() || plaintext.length > MAX_ATTESTATION_PLAINTEXT_CHARS) {
            return false
        }

        if (key == PIN_CODE_KEY) {
            return plaintext.length == PIN_CODE_LENGTH &&
                plaintext.all { it in '0'..'9' }
        }

        if (!isCanonicalHex(plaintext)) return false

        return try {
            when {
                legacyV1QuarantineKey.matches(key) ||
                    legacyV1MetaQuarantineKey.matches(key) -> {
                    WalletSecretScalePreflight.requireSourceV1(plaintext)
                    val source = SourceInternal.read(plaintext)
                    SourceType.valueOf(source[SourceInternal.Type])
                    val expectedPublicKeyHex = checkNotNull(
                        legacyV1QuarantineKey.matchEntire(key)
                            ?: legacyV1MetaQuarantineKey.matchEntire(key)
                    ).groupValues[1]
                    validKeyPair(
                        publicKey = source[SourceInternal.PublicKey],
                        privateKey = source[SourceInternal.PrivateKey],
                        nonce = source[SourceInternal.Nonce]
                    ) &&
                        Hex.toHexString(
                            source[SourceInternal.PublicKey]
                        ) == expectedPublicKeyHex
                }

                legacyV04MetaQuarantineKey.matches(key) -> {
                    val signingData =
                        LegacyWalletV04Secrets.decodeSigningData(plaintext)
                    val expectedPublicKeyHex = checkNotNull(
                        legacyV04MetaQuarantineKey.matchEntire(key)
                    ).groupValues[1]
                    validKeyPair(
                        publicKey = signingData.publicKey,
                        privateKey = signingData.privateKey,
                        nonce = signingData.nonce
                    ) &&
                        Hex.toHexString(signingData.publicKey) ==
                        expectedPublicKeyHex
                }

                legacySecretKey.matches(key) -> {
                    WalletSecretScalePreflight.requireSourceV1(plaintext)
                    val source = SourceInternal.read(plaintext)
                    SourceType.valueOf(source[SourceInternal.Type])
                    validKeyPair(
                        publicKey = source[SourceInternal.PublicKey],
                        privateKey = source[SourceInternal.PrivateKey],
                        nonce = source[SourceInternal.Nonce]
                    )
                }

                LegacyWalletV04Secrets.isSigningDataKey(key) -> {
                    val signingData =
                        LegacyWalletV04Secrets.decodeSigningData(plaintext)
                    validKeyPair(
                        publicKey = signingData.publicKey,
                        privateKey = signingData.privateKey,
                        nonce = signingData.nonce
                    )
                }

                metaAccessSecretKey.matches(key) -> {
                    validMetaAccountSecrets(plaintext)
                }

                chainAccessSecretKey.matches(key) -> {
                    WalletSecretScalePreflight.requireChainAccountV2(plaintext)
                    val secrets = ChainAccountSecrets.read(plaintext)
                    validKeyPair(secrets[ChainAccountSecrets.Keypair])
                }

                substrateSecretKey.matches(key) -> {
                    WalletSecretScalePreflight.requireSubstrateV3(plaintext)
                    val secrets = SubstrateSecrets.read(plaintext)
                    validKeyPair(secrets[SubstrateSecrets.SubstrateKeypair])
                }

                ethereumSecretKey.matches(key) -> {
                    WalletSecretScalePreflight.requireEthereumV3(plaintext)
                    val secrets = EthereumSecrets.read(plaintext)
                    validKeyPair(secrets[EthereumSecrets.EthereumKeypair])
                }

                tonSecretKey.matches(key) -> {
                    WalletSecretScalePreflight.requireTonV3(plaintext)
                    val secrets = TonSecrets.read(plaintext)
                    secrets[TonSecrets.Seed].isNotEmpty() &&
                        secrets[TonSecrets.PrivateKey].isNotEmpty() &&
                        secrets[TonSecrets.PublicKey].isNotEmpty()
                }

                tonConnectKey.matches(key) -> {
                    WalletSecretScalePreflight.requireKeyPairV2(plaintext)
                    val keypair = KeyPairSchema.read(plaintext)
                    keypair[KeyPairSchema.PrivateKey].size == 32 &&
                        keypair[KeyPairSchema.PublicKey].size == 32 &&
                        keypair[KeyPairSchema.Nonce] == null
                }

                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun validKeyPair(keyPair: EncodableStruct<KeyPairSchema>): Boolean {
        return validKeyPair(
            publicKey = keyPair[KeyPairSchema.PublicKey],
            privateKey = keyPair[KeyPairSchema.PrivateKey],
            nonce = keyPair[KeyPairSchema.Nonce]
        )
    }

    private fun validMetaAccountSecrets(plaintext: String): Boolean {
        return try {
            when (
                WalletSecretScalePreflight
                    .requireMetaAccountV2OrLegacyV69(plaintext)
            ) {
                WalletMetaAccountScaleLayout.CURRENT_V2 -> {
                    val secrets = MetaAccountSecrets.read(plaintext)
                    validKeyPair(secrets[MetaAccountSecrets.SubstrateKeypair])
                }

                WalletMetaAccountScaleLayout.LEGACY_V69 -> {
                    val secrets = LegacyMetaAccountSecretsV69.read(plaintext)
                    validKeyPair(
                        secrets[LegacyMetaAccountSecretsV69.SubstrateKeypair]
                    )
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun validKeyPair(
        publicKey: ByteArray,
        privateKey: ByteArray,
        nonce: ByteArray?
    ): Boolean {
        return publicKey.size in MIN_KEY_BYTES..MAX_KEY_BYTES &&
            privateKey.size in MIN_KEY_BYTES..MAX_KEY_BYTES &&
            (nonce == null || nonce.size in MIN_KEY_BYTES..MAX_KEY_BYTES)
    }

    private fun isCanonicalHex(value: String): Boolean {
        val payload = if (value.startsWith(HEX_PREFIX)) {
            value.drop(HEX_PREFIX.length)
        } else {
            value
        }

        return payload.length >= MIN_SCALE_HEX_CHARS &&
            payload.length % 2 == 0 &&
            payload.all { character ->
                character in '0'..'9' ||
                    character in 'a'..'f' ||
                    character in 'A'..'F'
            }
    }

    const val PIN_CODE_KEY = "pin_code"
    private const val PIN_CODE_LENGTH = 6
    private const val QUARANTINE_PREFIX = "wallet_secret_quarantine:"
    private const val MUTATION_JOURNAL_KEY =
        "wallet_secret_mutation_journal_v1"
    private const val MUTATION_STAGE_PREFIX = "wallet_secret_mutation_stage:"
    private const val MUTATION_BACKUP_PREFIX = "wallet_secret_mutation_backup:"
    private const val HEX_PREFIX = "0x"
    private const val MIN_SCALE_HEX_CHARS = 16
    private const val MIN_KEY_BYTES = 16
    private const val MAX_KEY_BYTES = 128
    private const val MAX_ATTESTATION_PLAINTEXT_CHARS = 1_048_576

    /**
     * Exact six-field ACCESS_SECRETS format shipped before the TON keypair was
     * appended in database version 71. Direct upgrades from supported older
     * schemas must be able to authenticate this CBC-encrypted payload.
     */
    private object LegacyMetaAccountSecretsV69 :
        Schema<LegacyMetaAccountSecretsV69>() {
        val Entropy by byteArray().optional()
        val Seed by byteArray().optional()
        val SubstrateKeypair by schema(KeyPairSchema)
        val SubstrateDerivationPath by string().optional()
        val EthereumKeypair by schema(KeyPairSchema).optional()
        val EthereumDerivationPath by string().optional()
    }
}
