package jp.co.soramitsu.common.data.storage.envelope

import java.math.BigInteger
import jp.co.soramitsu.common.data.secrets.WalletMetaAccountScaleLayout
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.legacy.LegacyWalletV04Secrets
import jp.co.soramitsu.common.data.secrets.v1.SourceInternal
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

/**
 * Reads what the envelope header and the backup wraps need from a wallet
 * secret plaintext. Parsing never throws: a payload that does not parse has
 * no public identity and no root key, and it is still sealed as it is.
 */
object WalletSecretPayloads {

    /** One signing key a backup can prove. [privateKey] is secret: zero it after use. */
    class RootKey(val kind: WalletRootKeyKind, val privateKey: ByteArray, val publicKey: ByteArray) {
        override fun toString(): String = "WalletSecretPayloads.RootKey(kind=$kind)"
    }

    /** The public key (or account id) that the value's signing key proves; empty when it does not parse. */
    fun publicIdentity(scope: WalletSecretEnvelopeScope, plaintext: String): ByteArray {
        val keys = rootKeys(scope, plaintext)
        return try {
            keys.firstOrNull()?.publicKey?.copyOf() ?: ByteArray(0)
        } finally {
            keys.forEach { it.privateKey.fill(0) }
        }
    }

    /**
     * The signing keys stored in [plaintext]. A pre-V3 ACCESS payload holds up
     * to three (Substrate, Ethereum, TON); every other wallet secret holds one.
     */
    fun rootKeys(scope: WalletSecretEnvelopeScope, plaintext: String): List<RootKey> = try {
        decodeRootKeys(scope, plaintext)
    } catch (_: Exception) {
        emptyList()
    }

    private fun decodeRootKeys(scope: WalletSecretEnvelopeScope, plaintext: String): List<RootKey> {
        if (plaintext.isEmpty() || WalletSecretEnvelope.isSealed(plaintext)) return emptyList()
        return when (scope) {
            WalletSecretEnvelopeScope.SUBSTRATE_ROOT -> {
                val hex = WalletSecretScalePreflight.normalizePersistedHex(plaintext)
                WalletSecretScalePreflight.requireSubstrateV3(hex)
                listOfNotNull(substrateKey(SubstrateSecrets.read(hex)[SubstrateSecrets.SubstrateKeypair]))
            }

            WalletSecretEnvelopeScope.ETHEREUM_ROOT -> {
                val hex = WalletSecretScalePreflight.normalizePersistedHex(plaintext)
                WalletSecretScalePreflight.requireEthereumV3(hex)
                listOfNotNull(keyOf(WalletRootKeyKind.SECP256K1, EthereumSecrets.read(hex)[EthereumSecrets.EthereumKeypair]))
            }

            WalletSecretEnvelopeScope.TON_ROOT -> {
                val hex = WalletSecretScalePreflight.normalizePersistedHex(plaintext)
                WalletSecretScalePreflight.requireTonV3(hex)
                val secrets = TonSecrets.read(hex)
                listOfNotNull(rootKey(WalletRootKeyKind.ED25519, secrets[TonSecrets.PrivateKey], secrets[TonSecrets.PublicKey]))
            }

            WalletSecretEnvelopeScope.CHAIN_ACCOUNT -> {
                val hex = WalletSecretScalePreflight.normalizePersistedHex(plaintext)
                WalletSecretScalePreflight.requireChainAccountV2(hex)
                listOfNotNull(substrateKey(ChainAccountSecrets.read(hex)[ChainAccountSecrets.Keypair]))
            }

            WalletSecretEnvelopeScope.LEGACY_META_ACCOUNT -> metaAccountKeys(plaintext)

            WalletSecretEnvelopeScope.LEGACY_V1 -> {
                val hex = WalletSecretScalePreflight.normalizePersistedHex(plaintext)
                WalletSecretScalePreflight.requireSourceV1(hex)
                val source = SourceInternal.read(hex)
                listOfNotNull(
                    rootKey(
                        inferSubstrateKind(source[SourceInternal.PublicKey], source[SourceInternal.Nonce]),
                        source[SourceInternal.PrivateKey],
                        source[SourceInternal.PublicKey]
                    )
                )
            }

            WalletSecretEnvelopeScope.LEGACY_V04_SIGNING -> {
                val data = LegacyWalletV04Secrets.decodeSigningData(WalletSecretScalePreflight.normalizePersistedHex(plaintext))
                listOfNotNull(rootKey(inferSubstrateKind(data.publicKey, data.nonce), data.privateKey, data.publicKey))
            }

            WalletSecretEnvelopeScope.LEGACY_V04_TEXT -> emptyList()
        }
    }

    private fun metaAccountKeys(plaintext: String): List<RootKey> {
        val hex = WalletSecretScalePreflight.normalizePersistedHex(plaintext)
        return when (WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(hex)) {
            WalletMetaAccountScaleLayout.CURRENT_V2 -> {
                val secrets = MetaAccountSecrets.read(hex)
                listOfNotNull(
                    substrateKey(secrets[MetaAccountSecrets.SubstrateKeypair]),
                    secrets[MetaAccountSecrets.EthereumKeypair]?.let { keyOf(WalletRootKeyKind.SECP256K1, it) },
                    secrets[MetaAccountSecrets.TonKeypair]?.let { keyOf(WalletRootKeyKind.ED25519, it) }
                )
            }

            WalletMetaAccountScaleLayout.LEGACY_V69 -> {
                val secrets = MetaAccountSecretsV69.read(hex)
                listOfNotNull(
                    substrateKey(secrets[MetaAccountSecretsV69.SubstrateKeypair]),
                    secrets[MetaAccountSecretsV69.EthereumKeypair]?.let { keyOf(WalletRootKeyKind.SECP256K1, it) }
                )
            }
        }
    }

    private fun substrateKey(keypair: EncodableStruct<KeyPairSchema>): RootKey? = rootKey(
        inferSubstrateKind(keypair[KeyPairSchema.PublicKey], keypair[KeyPairSchema.Nonce]),
        keypair[KeyPairSchema.PrivateKey],
        keypair[KeyPairSchema.PublicKey]
    )

    private fun keyOf(kind: WalletRootKeyKind, keypair: EncodableStruct<KeyPairSchema>): RootKey? =
        rootKey(kind, keypair[KeyPairSchema.PrivateKey], keypair[KeyPairSchema.PublicKey])

    private fun rootKey(kind: WalletRootKeyKind, privateKey: ByteArray, publicKey: ByteArray): RootKey? =
        if (privateKey.size < MIN_PRIVATE_KEY_BYTES || publicKey.isEmpty()) null else RootKey(kind, privateKey, publicKey)

    /** An sr25519 keypair stores a nonce; a compressed or uncompressed secp256k1 key is 33, 64 or 65 bytes. */
    fun inferSubstrateKind(publicKey: ByteArray, nonce: ByteArray?): WalletRootKeyKind = when {
        nonce != null && nonce.isNotEmpty() -> WalletRootKeyKind.SR25519
        publicKey.size in SECP256K1_PUBLIC_KEY_SIZES -> WalletRootKeyKind.SECP256K1
        else -> WalletRootKeyKind.ED25519
    }

    private const val MIN_PRIVATE_KEY_BYTES = 16
    private val SECP256K1_PUBLIC_KEY_SIZES = setOf(33, 64, 65)

    /** The six-field ACCESS payload written before TON (database 69). */
    private object MetaAccountSecretsV69 : Schema<MetaAccountSecretsV69>() {
        val Entropy by byteArray().optional()
        val Seed by byteArray().optional()
        val SubstrateKeypair by schema(KeyPairSchema)
        val SubstrateDerivationPath by string().optional()
        val EthereumKeypair by schema(KeyPairSchema).optional()
        val EthereumDerivationPath by string().optional()
    }
}

/**
 * How a root private key is brought to the one form every proof of it
 * reaches. Mnemonic, raw-seed, JSON and raw-key imports of one account store
 * the same signing key; reducing it this way removes the representation
 * differences a valid backup could still carry (an sr25519 nonce, which no
 * public key binds; an unreduced scalar; a 64-byte ed25519 secret; a
 * secp256k1 key with a sign byte).
 */
enum class WalletRootKeyKind {
    /** The 32-byte secret scalar of the 64-byte expanded key, reduced mod ℓ; the nonce is not used. */
    SR25519,

    /** The 32-byte RFC 8032 seed (TON keys too). */
    ED25519,

    /** The 32-byte big-endian scalar mod n (Substrate ECDSA and Ethereum). */
    SECP256K1;

    /** The canonical 32 bytes, or null for a key that has none. Zero the result after use. */
    fun canonical(privateKey: ByteArray): ByteArray? = when (this) {
        SR25519 -> if (privateKey.size < KEY_BYTES) null else reduceLittleEndian(privateKey.copyOfRange(0, KEY_BYTES), ED25519_ORDER)
        ED25519 -> if (privateKey.size < KEY_BYTES) null else privateKey.copyOfRange(0, KEY_BYTES)
        SECP256K1 -> if (privateKey.size > KEY_BYTES + 1) {
            null
        } else {
            val scalar = BigInteger(1, privateKey).mod(SECP256K1_ORDER)
            if (scalar.signum() == 0) null else toFixedBigEndian(scalar)
        }
    }

    private companion object {
        const val KEY_BYTES = 32
        val ED25519_ORDER: BigInteger = BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))
        val SECP256K1_ORDER = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)

        fun reduceLittleEndian(bytes: ByteArray, order: BigInteger): ByteArray {
            val reversed = bytes.reversedArray()
            bytes.fill(0)
            val value = BigInteger(1, reversed).mod(order)
            reversed.fill(0)
            return toFixedBigEndian(value).also { it.reverse() }
        }

        fun toFixedBigEndian(value: BigInteger): ByteArray {
            val raw = value.toByteArray()
            val out = ByteArray(KEY_BYTES)
            val start = maxOf(0, raw.size - KEY_BYTES)
            raw.copyInto(out, KEY_BYTES - (raw.size - start), start, raw.size)
            raw.fill(0)
            return out
        }
    }
}
