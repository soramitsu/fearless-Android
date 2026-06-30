package jp.co.soramitsu.common.utils

import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.math.BigInteger
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SolanaKeyDerivation {

    private const val ED25519_SEED_KEY = "ed25519 seed"
    private const val HARDENED_OFFSET = 0x80000000L
    private const val MAX_CHILD_INDEX = 0x7fffffffL
    private const val PUBLIC_KEY_LENGTH = 32
    private const val PRIVATE_KEY_LENGTH = 32
    private const val BIP39_SEED_LENGTH_BITS = 512
    private const val BIP39_ROUNDS = 2048
    private const val UINT_32_BYTES = 4
    private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()

    fun deriveAccount(
        mnemonic: String,
        passphrase: String = "",
        derivationPath: String = UniversalWalletDerivationPaths.SOLANA_DEFAULT
    ): SolanaAccount {
        val normalizedMnemonic = normalizeMnemonic(mnemonic)
        val seed = bip39Seed(normalizedMnemonic, passphrase)
        val key = derivePrivateKey(seed, derivationPath)
        val publicKey = publicKeyFromPrivateKey(key.privateKey)

        return SolanaAccount(
            derivationPath = derivationPath,
            privateKey = key.privateKey,
            chainCode = key.chainCode,
            publicKey = publicKey,
            address = addressFromPublicKey(publicKey)
        )
    }

    fun derivePrivateKey(seed: ByteArray, derivationPath: String = UniversalWalletDerivationPaths.SOLANA_DEFAULT): DerivedPrivateKey {
        require(seed.isNotEmpty()) { "Seed must not be empty" }

        val nodes = parseHardenedDerivationPath(derivationPath)
        var digest = hmacSha512(ED25519_SEED_KEY.toByteArray(Charsets.UTF_8), seed)
        var privateKey = digest.copyOfRange(0, PRIVATE_KEY_LENGTH)
        var chainCode = digest.copyOfRange(PRIVATE_KEY_LENGTH, digest.size)

        nodes.forEach { index ->
            val data = ByteArray(1 + PRIVATE_KEY_LENGTH + UINT_32_BYTES)
            data[0] = 0
            privateKey.copyInto(data, destinationOffset = 1)
            ByteBuffer
                .allocate(UINT_32_BYTES)
                .putInt((index + HARDENED_OFFSET).toInt())
                .array()
                .copyInto(data, destinationOffset = 1 + PRIVATE_KEY_LENGTH)

            digest = hmacSha512(chainCode, data)
            privateKey = digest.copyOfRange(0, PRIVATE_KEY_LENGTH)
            chainCode = digest.copyOfRange(PRIVATE_KEY_LENGTH, digest.size)
        }

        return DerivedPrivateKey(privateKey, chainCode)
    }

    fun publicKeyFromPrivateKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == PRIVATE_KEY_LENGTH) { "Solana Ed25519 private key seed must be 32 bytes" }

        return Ed25519PrivateKeyParameters(privateKey, 0)
            .generatePublicKey()
            .encoded
    }

    fun addressFromPublicKey(publicKey: ByteArray): String {
        require(publicKey.size == PUBLIC_KEY_LENGTH) { "Solana public key must be 32 bytes" }

        return base58Encode(publicKey)
    }

    private fun parseHardenedDerivationPath(derivationPath: String): List<Long> {
        require(derivationPath.isNotBlank()) { "Derivation path must not be empty" }
        require(derivationPath == "m" || derivationPath.startsWith("m/")) { "Derivation path must start with m" }

        if (derivationPath == "m") {
            return emptyList()
        }

        return derivationPath
            .removePrefix("m/")
            .split("/")
            .map { component ->
                require(component.endsWith("'")) { "Solana derivation only supports hardened components" }

                val index = component.dropLast(1)
                require(index.isNotEmpty() && index.all(Char::isDigit)) { "Derivation path contains an invalid index" }

                val parsed = index.toLong()
                require(parsed <= MAX_CHILD_INDEX) { "Derivation index is out of range" }
                parsed
            }
    }

    private fun normalizeMnemonic(mnemonic: String): String {
        val words = mnemonic.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        require(words.isNotEmpty()) { "Mnemonic must not be empty" }

        return words.joinToString(" ")
    }

    private fun bip39Seed(mnemonic: String, passphrase: String): ByteArray {
        val spec = PBEKeySpec(
            mnemonic.toCharArray(),
            "mnemonic$passphrase".toByteArray(Charsets.UTF_8),
            BIP39_ROUNDS,
            BIP39_SEED_LENGTH_BITS
        )

        return SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA512")
            .generateSecret(spec)
            .encoded
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))

        return mac.doFinal(data)
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

    data class SolanaAccount(
        val derivationPath: String,
        val privateKey: ByteArray,
        val chainCode: ByteArray,
        val publicKey: ByteArray,
        val address: String
    )

    data class DerivedPrivateKey(
        val privateKey: ByteArray,
        val chainCode: ByteArray
    )
}
