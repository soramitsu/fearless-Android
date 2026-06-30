package jp.co.soramitsu.common.utils

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigInteger

object SolanaSigner {
    private const val PRIVATE_KEY_LENGTH = 32
    private const val PUBLIC_KEY_LENGTH = 32
    private const val SIGNATURE_LENGTH = 64
    private const val MAX_MESSAGE_LENGTH = 64 * 1024
    private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()

    fun signMessage(
        mnemonic: String,
        message: ByteArray,
        passphrase: String = "",
        derivationPath: String = jp.co.soramitsu.common.model.UniversalWalletDerivationPaths.SOLANA_DEFAULT
    ): SolanaSignature {
        val account = SolanaKeyDerivation.deriveAccount(
            mnemonic = mnemonic,
            passphrase = passphrase,
            derivationPath = derivationPath
        )
        val signature = signMessage(account.privateKey, message)

        return SolanaSignature(
            derivationPath = account.derivationPath,
            address = account.address,
            publicKey = account.publicKey,
            message = message.copyOf(),
            signature = signature,
            signatureBase58 = base58Encode(signature),
            signatureHex = signature.toHex()
        )
    }

    fun signMessage(privateKey: ByteArray, message: ByteArray): ByteArray {
        require(privateKey.size == PRIVATE_KEY_LENGTH) { "Solana Ed25519 private key seed must be 32 bytes" }
        require(message.isNotEmpty()) { "Solana message must not be empty" }
        require(message.size <= MAX_MESSAGE_LENGTH) { "Solana message is too large" }

        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(message, 0, message.size)

        return signer.generateSignature()
    }

    fun verifyMessage(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        require(publicKey.size == PUBLIC_KEY_LENGTH) { "Solana public key must be 32 bytes" }
        require(signature.size == SIGNATURE_LENGTH) { "Solana signature must be 64 bytes" }
        require(message.isNotEmpty()) { "Solana message must not be empty" }
        require(message.size <= MAX_MESSAGE_LENGTH) { "Solana message is too large" }

        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(message, 0, message.size)

        return verifier.verifySignature(signature)
    }

    private fun base58Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            return ""
        }

        var value = BigInteger(1, bytes)
        val output = StringBuilder()
        val radix = BigInteger.valueOf(BASE58_ALPHABET.size.toLong())

        while (value > BigInteger.ZERO) {
            val divRem = value.divideAndRemainder(radix)
            output.append(BASE58_ALPHABET[divRem[1].toInt()])
            value = divRem[0]
        }

        bytes.takeWhile { it == 0.toByte() }.forEach { _ ->
            output.append(BASE58_ALPHABET[0])
        }

        return output.reverse().toString()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    data class SolanaSignature(
        val derivationPath: String,
        val address: String,
        val publicKey: ByteArray,
        val message: ByteArray,
        val signature: ByteArray,
        val signatureBase58: String,
        val signatureHex: String
    )
}
