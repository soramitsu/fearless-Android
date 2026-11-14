package jp.co.soramitsu.fearless_utils.encrypt

import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.hash.Hasher.keccak256
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign
import java.math.BigInteger

object Signer {

    private enum class MessageHashing(val hasher: (ByteArray) -> ByteArray) {

        SUBSTRATE(hasher = { it.blake2b256() }),
        ETHEREUM(hasher = { it.keccak256() })
    }

    fun sign(
        multiChainEncryption: MultiChainEncryption,
        message: ByteArray,
        keypair: Keypair
    ): SignatureWrapper {
        return when (multiChainEncryption) {
            is MultiChainEncryption.Ethereum -> {
                signEcdsa(message, keypair, MessageHashing.ETHEREUM.hasher)
            }
            is MultiChainEncryption.Substrate -> {
                when (multiChainEncryption.encryptionType) {
                    EncryptionType.SR25519 -> {
                        require(keypair is Sr25519Keypair) {
                            "Sr25519Keypair is needed to sign with SR25519"
                        }

                        signSr25519(message, keypair)
                    }

                    EncryptionType.ED25519 -> signEd25519(message, keypair)

                    EncryptionType.ECDSA -> signEcdsa(
                        message,
                        keypair,
                        MessageHashing.SUBSTRATE.hasher
                    )
                }
            }
        }
    }

    /** Performs hashing, provider setup and native loading before final authorization. */
    fun prepareSigning(
        encryption: MultiChainEncryption,
        message: ByteArray,
        keypair: Keypair
    ): PreparedSignature {
        val frozenMessage = message.copyOf()
        return when (encryption) {
            MultiChainEncryption.Ethereum -> prepareEcdsa(frozenMessage, keypair, MessageHashing.ETHEREUM.hasher)
            is MultiChainEncryption.Substrate -> when (encryption.encryptionType) {
                EncryptionType.ED25519 -> {
                    val seed = keypair.privateKey.copyOf()
                    require(seed.size == Ed25519Crypto.SEED_SIZE) { "Ed25519 seed must be ${Ed25519Crypto.SEED_SIZE} bytes" }
                    PreparedSignature { SignatureWrapper.Ed25519(Ed25519Crypto.sign(seed, frozenMessage)) }
                }
                EncryptionType.ECDSA -> prepareEcdsa(frozenMessage, keypair, MessageHashing.SUBSTRATE.hasher)
                EncryptionType.SR25519 -> {
                    require(keypair is Sr25519Keypair) { "Sr25519Keypair is needed to sign with SR25519" }
                    val publicKey = keypair.publicKey.copyOf()
                    val secret = keypair.privateKey + keypair.nonce
                    Sr25519.ensureInitialized()
                    PreparedSignature { SignatureWrapper.Sr25519(Sr25519.sign(publicKey, secret, frozenMessage)) }
                }
            }
        }
    }

    private fun prepareEcdsa(message: ByteArray, keypair: Keypair, hasher: (ByteArray) -> ByteArray): PreparedSignature {
        val privateKey = BigInteger(1, keypair.privateKey.copyOf())
        val publicKey = Sign.publicKeyFromPrivate(privateKey)
        val messageHash = hasher(message)
        val key = ECKeyPair(privateKey, publicKey)
        return PreparedSignature {
            val signature = Sign.signMessage(messageHash, key, false)
            SignatureWrapper.Ecdsa(signature.v, signature.r, signature.s)
        }
    }

    private fun signSr25519(message: ByteArray, keypair: Sr25519Keypair): SignatureWrapper {
        val sign = Sr25519.sign(keypair.publicKey, keypair.privateKey + keypair.nonce, message)

        return SignatureWrapper.Sr25519(signature = sign)
    }

    fun verifySr25519(
        message: ByteArray,
        signature: ByteArray,
        publicKeyBytes: ByteArray
    ): Boolean {
        return Sr25519.verify(signature, message, publicKeyBytes)
    }

    private fun signEd25519(message: ByteArray, keypair: Keypair): SignatureWrapper {
        return SignatureWrapper.Ed25519(signature = Ed25519Crypto.sign(keypair.privateKey, message))
    }

    /** RFC 8032 requires S < L; signatures with S + L (CVE-2020-36843) or non-canonical points are rejected. */
    fun verifyEd25519(
        message: ByteArray,
        signature: ByteArray,
        publicKeyBytes: ByteArray
    ): Boolean {
        return Ed25519Crypto.verify(message, signature, publicKeyBytes)
    }

    private fun signEcdsa(
        message: ByteArray,
        keypair: Keypair,
        hasher: (ByteArray) -> ByteArray
    ): SignatureWrapper {
        val privateKey = BigInteger(Hex.toHexString(keypair.privateKey), 16)
        val publicKey = Sign.publicKeyFromPrivate(privateKey)

        val messageHash = hasher(message)

        val sign = Sign.signMessage(messageHash, ECKeyPair(privateKey, publicKey), false)

        return SignatureWrapper.Ecdsa(v = sign.v, r = sign.r, s = sign.s)
    }
}
