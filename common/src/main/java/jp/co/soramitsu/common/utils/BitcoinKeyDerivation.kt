package jp.co.soramitsu.common.utils

import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BitcoinKeyDerivation {

    private const val BIP32_SEED_KEY = "Bitcoin seed"
    private const val HARDENED_OFFSET = 0x80000000L
    private const val MAX_CHILD_INDEX = 0x7fffffffL
    private const val PRIVATE_KEY_LENGTH = 32
    private const val PUBLIC_KEY_LENGTH = 33
    private const val CHAIN_CODE_LENGTH = 32
    private const val BIP39_SEED_LENGTH_BITS = 512
    private const val BIP39_ROUNDS = 2048
    private const val XPUB_VERSION = 0x0488B21E
    private const val XPUB_PAYLOAD_LENGTH = 78
    private const val UINT_32_BYTES = 4

    private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
    private val BECH32_ALPHABET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l".toCharArray()
    private val BECH32_GENERATORS = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    private val SECP256K1 = CustomNamedCurves.getByName("secp256k1")
    private val SECP256K1_N = SECP256K1.n

    fun deriveAccount(
        mnemonic: String,
        passphrase: String = "",
        network: Network = Network.Mainnet
    ): BitcoinAccount {
        val normalizedMnemonic = normalizeMnemonic(mnemonic)
        val seed = bip39Seed(normalizedMnemonic, passphrase)
        val accountPath = accountPath(network)
        val firstReceivePath = firstReceivePath(network)
        val accountNode = derivePrivateKey(seed, accountPath)
        val receiveNode = derivePrivateKey(seed, firstReceivePath)
        val receivePublicKey = publicKeyFromPrivateKey(receiveNode.privateKey)

        return BitcoinAccount(
            network = network,
            accountPath = accountPath,
            firstReceivePath = firstReceivePath,
            accountXpub = serializeXpub(accountNode),
            privateKey = receiveNode.privateKey,
            chainCode = receiveNode.chainCode,
            publicKey = receivePublicKey,
            firstReceiveAddress = addressFromPublicKey(receivePublicKey, network)
        )
    }

    fun deriveKey(
        mnemonic: String,
        passphrase: String = "",
        derivationPath: String,
        network: Network = Network.Mainnet
    ): BitcoinDerivedKey {
        val normalizedMnemonic = normalizeMnemonic(mnemonic)
        val node = derivePrivateKey(bip39Seed(normalizedMnemonic, passphrase), derivationPath)
        val publicKey = publicKeyFromPrivateKey(node.privateKey)

        return BitcoinDerivedKey(
            network = network,
            derivationPath = derivationPath,
            privateKey = node.privateKey,
            chainCode = node.chainCode,
            publicKey = publicKey,
            address = addressFromPublicKey(publicKey, network)
        )
    }

    fun derivePrivateKey(seed: ByteArray, derivationPath: String): ExtendedPrivateKey {
        require(seed.isNotEmpty()) { "Seed must not be empty" }

        var digest = hmacSha512(BIP32_SEED_KEY.toByteArray(Charsets.UTF_8), seed)
        var node = ExtendedPrivateKey(
            privateKey = validatePrivateKey(digest.copyOfRange(0, PRIVATE_KEY_LENGTH)),
            chainCode = digest.copyOfRange(PRIVATE_KEY_LENGTH, digest.size),
            depth = 0,
            parentFingerprint = ByteArray(4),
            childNumber = 0
        )

        parseDerivationPath(derivationPath).forEach { component ->
            node = deriveChild(node, component)
        }

        return node
    }

    fun publicKeyFromPrivateKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == PRIVATE_KEY_LENGTH) { "Bitcoin private key must be 32 bytes" }

        val privateKeyInteger = BigInteger(1, privateKey)
        require(privateKeyInteger > BigInteger.ZERO && privateKeyInteger < SECP256K1_N) {
            "Bitcoin private key is out of secp256k1 range"
        }

        return SECP256K1.g.multiply(privateKeyInteger).normalize().getEncoded(true)
    }

    fun addressFromPublicKey(publicKey: ByteArray, network: Network): String {
        require(publicKey.size == PUBLIC_KEY_LENGTH) { "Bitcoin compressed public key must be 33 bytes" }

        return bech32Encode(hrp(network), listOf(0) + convertBits(hash160(publicKey).toList(), 8, 5, true))
    }

    fun getReceivePath(network: Network = Network.Mainnet, index: Long = 0, change: Long = 0): String {
        require(index in 0..MAX_CHILD_INDEX) { "Invalid Bitcoin receive index" }
        require(change == 0L || change == 1L) { "Invalid Bitcoin change index" }

        val coinType = when (network) {
            Network.Mainnet -> 0
            Network.Testnet -> 1
        }

        return "m/84'/$coinType'/0'/$change/$index"
    }

    private fun deriveChild(parent: ExtendedPrivateKey, component: PathComponent): ExtendedPrivateKey {
        val serializedIndex = component.serializedIndex
        val data = if (component.hardened) {
            byteArrayOf(0) + parent.privateKey + uint32(serializedIndex)
        } else {
            publicKeyFromPrivateKey(parent.privateKey) + uint32(serializedIndex)
        }

        val digest = hmacSha512(parent.chainCode, data)
        val tweak = BigInteger(1, digest.copyOfRange(0, PRIVATE_KEY_LENGTH))
        require(tweak < SECP256K1_N) { "Bitcoin child private key tweak is out of range" }

        val parentKey = BigInteger(1, parent.privateKey)
        val childKey = tweak.add(parentKey).mod(SECP256K1_N)
        require(childKey > BigInteger.ZERO) { "Bitcoin child private key is zero" }

        return ExtendedPrivateKey(
            privateKey = childKey.toFixedLengthBytes(PRIVATE_KEY_LENGTH),
            chainCode = digest.copyOfRange(PRIVATE_KEY_LENGTH, digest.size),
            depth = parent.depth + 1,
            parentFingerprint = fingerprint(publicKeyFromPrivateKey(parent.privateKey)),
            childNumber = serializedIndex
        )
    }

    private fun serializeXpub(node: ExtendedPrivateKey): String {
        val publicKey = publicKeyFromPrivateKey(node.privateKey)
        val payload = ByteBuffer.allocate(XPUB_PAYLOAD_LENGTH)
            .putInt(XPUB_VERSION)
            .put(node.depth.toByte())
            .put(node.parentFingerprint)
            .putInt(node.childNumber.toInt())
            .put(node.chainCode)
            .put(publicKey)
            .array()

        return base58CheckEncode(payload)
    }

    private fun accountPath(network: Network): String = when (network) {
        Network.Mainnet -> UniversalWalletDerivationPaths.BITCOIN_MAINNET_ACCOUNT
        Network.Testnet -> UniversalWalletDerivationPaths.BITCOIN_TESTNET_ACCOUNT
    }

    private fun firstReceivePath(network: Network): String = when (network) {
        Network.Mainnet -> UniversalWalletDerivationPaths.BITCOIN_MAINNET_FIRST_RECEIVE
        Network.Testnet -> UniversalWalletDerivationPaths.BITCOIN_TESTNET_FIRST_RECEIVE
    }

    private fun parseDerivationPath(derivationPath: String): List<PathComponent> {
        require(derivationPath.isNotBlank()) { "Derivation path must not be empty" }
        require(derivationPath == "m" || derivationPath.startsWith("m/")) { "Derivation path must start with m" }

        if (derivationPath == "m") {
            return emptyList()
        }

        return derivationPath
            .removePrefix("m/")
            .split("/")
            .map { component ->
                val hardened = component.endsWith("'")
                val index = if (hardened) component.dropLast(1) else component

                require(index.isNotEmpty() && index.all(Char::isDigit)) { "Derivation path contains an invalid index" }

                val parsed = index.toLong()
                require(parsed <= MAX_CHILD_INDEX) { "Derivation index is out of range" }

                PathComponent(parsed, hardened)
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

    private fun hash160(value: ByteArray): ByteArray {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(value)
        val digest = RIPEMD160Digest()
        digest.update(sha256, 0, sha256.size)
        val output = ByteArray(20)
        digest.doFinal(output, 0)

        return output
    }

    private fun fingerprint(publicKey: ByteArray): ByteArray = hash160(publicKey).copyOfRange(0, 4)

    private fun validatePrivateKey(privateKey: ByteArray): ByteArray {
        val value = BigInteger(1, privateKey)
        require(value > BigInteger.ZERO && value < SECP256K1_N) { "Bitcoin private key is out of secp256k1 range" }

        return privateKey
    }

    private fun uint32(value: Long): ByteArray = ByteBuffer
        .allocate(UINT_32_BYTES)
        .putInt(value.toInt())
        .array()

    private fun BigInteger.toFixedLengthBytes(size: Int): ByteArray {
        val raw = toByteArray()
        val unsigned = if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(unsigned.size <= size) { "Integer does not fit into requested byte length" }

        return ByteArray(size - unsigned.size) + unsigned
    }

    private fun base58CheckEncode(payload: ByteArray): String {
        val checksum = MessageDigest.getInstance("SHA-256")
            .digest(MessageDigest.getInstance("SHA-256").digest(payload))
            .copyOfRange(0, 4)

        return base58Encode(payload + checksum)
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

    private fun bech32Encode(hrp: String, values: List<Int>): String {
        require(hrp.isNotBlank()) { "Bitcoin bech32 HRP must not be empty" }

        val checksum = bech32CreateChecksum(hrp, values)
        val encodedValues = values + checksum

        return hrp + "1" + encodedValues.joinToString("") { BECH32_ALPHABET[it].toString() }
    }

    private fun bech32CreateChecksum(hrp: String, values: List<Int>): List<Int> {
        val polymodValues = expandHrp(hrp) + values + List(6) { 0 }
        val polymod = bech32Polymod(polymodValues) xor 1

        return (0 until 6).map { index ->
            (polymod shr (5 * (5 - index))) and 31
        }
    }

    private fun bech32Polymod(values: List<Int>): Int {
        var checksum = 1

        values.forEach { value ->
            val top = checksum shr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value

            BECH32_GENERATORS.forEachIndexed { index, generator ->
                if (((top shr index) and 1) == 1) {
                    checksum = checksum xor generator
                }
            }
        }

        return checksum
    }

    private fun expandHrp(hrp: String): List<Int> =
        hrp.map { it.code shr 5 } + listOf(0) + hrp.map { it.code and 31 }

    private fun convertBits(values: List<Byte>, fromBits: Int, toBits: Int, pad: Boolean): List<Int> {
        var accumulator = 0
        var bits = 0
        val maxValue = (1 shl toBits) - 1
        val maxAccumulator = (1 shl (fromBits + toBits - 1)) - 1
        val result = mutableListOf<Int>()

        values.forEach { raw ->
            val value = raw.toInt() and 0xff
            require(value ushr fromBits == 0) { "Invalid bit group" }

            accumulator = ((accumulator shl fromBits) or value) and maxAccumulator
            bits += fromBits

            while (bits >= toBits) {
                bits -= toBits
                result.add((accumulator shr bits) and maxValue)
            }
        }

        if (pad) {
            if (bits > 0) {
                result.add((accumulator shl (toBits - bits)) and maxValue)
            }
        } else {
            require(bits < fromBits && ((accumulator shl (toBits - bits)) and maxValue) == 0) { "Invalid padding" }
        }

        return result
    }

    private fun hrp(network: Network): String = when (network) {
        Network.Mainnet -> "bc"
        Network.Testnet -> "tb"
    }

    enum class Network {
        Mainnet,
        Testnet
    }

    data class BitcoinAccount(
        val network: Network,
        val accountPath: String,
        val firstReceivePath: String,
        val accountXpub: String,
        val privateKey: ByteArray,
        val chainCode: ByteArray,
        val publicKey: ByteArray,
        val firstReceiveAddress: String
    )

    data class BitcoinDerivedKey(
        val network: Network,
        val derivationPath: String,
        val privateKey: ByteArray,
        val chainCode: ByteArray,
        val publicKey: ByteArray,
        val address: String
    )

    data class ExtendedPrivateKey(
        val privateKey: ByteArray,
        val chainCode: ByteArray,
        val depth: Int,
        val parentFingerprint: ByteArray,
        val childNumber: Long
    )

    private data class PathComponent(
        val index: Long,
        val hardened: Boolean
    ) {
        val serializedIndex: Long = index + if (hardened) HARDENED_OFFSET else 0
    }
}
