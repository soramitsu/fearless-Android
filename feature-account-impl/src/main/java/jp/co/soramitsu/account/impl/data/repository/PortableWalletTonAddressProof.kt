package jp.co.soramitsu.account.impl.data.repository

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import jp.co.soramitsu.common.utils.tonAccountId
import java.io.StringReader
import java.util.Base64

/** The same strict Wallet V4R2 public-address proof for signed roots and incoming watch slots. */
internal object PortableWalletTonAddressProof {
    private const val V4R2 = 2
    private const val RAW_ADDRESS = 1
    private const val IOS_JSON_ADDRESS = 2
    private const val PUBLIC_KEY_BYTES = 32
    private const val RAW_ADDRESS_BYTES = 33
    private const val ADDRESS_HASH_BYTES = 32
    private const val RAW_ADDRESS_PREFIX_CHARS = 2
    private const val HEX_CHARS_PER_BYTE = 2
    private const val HEX_NIBBLE_SHIFT = 4
    private const val HEX_RADIX = 16

    fun verifyV4R2(
        publicKey: ByteArray,
        address: ByteArray,
        encoding: Int,
        contractVersion: Int,
    ) {
        require(contractVersion == V4R2 && publicKey.size == PUBLIC_KEY_BYTES) {
            "TON public identity is not Wallet V4R2"
        }
        val raw = try {
            publicKey.tonAccountId(isTestnet = false)
        } catch (_: Exception) {
            throw IllegalArgumentException("TON V4R2 address cannot be derived")
        }
        require(raw.startsWith("0:") && raw.length == RAW_ADDRESS_PREFIX_CHARS + ADDRESS_HASH_BYTES * HEX_CHARS_PER_BYTE) {
            "TON V4R2 address is invalid"
        }
        val expectedHash = raw.substring(RAW_ADDRESS_PREFIX_CHARS).hexToBytes()
        try {
            when (encoding) {
                RAW_ADDRESS -> require(
                    address.size == RAW_ADDRESS_BYTES && address[0] == 0.toByte() &&
                        address.copyOfRange(1, RAW_ADDRESS_BYTES).contentEquals(expectedHash)
                ) {
                    "TON V4R2 address differs from its public key"
                }
                IOS_JSON_ADDRESS -> verifyIosJson(address, expectedHash)
                else -> throw IllegalArgumentException("TON address encoding is unsupported")
            }
        } finally {
            expectedHash.fill(0)
        }
    }

    private fun verifyIosJson(encoded: ByteArray, expectedHash: ByteArray) {
        val text = encoded.decodeToString(throwOnInvalidSequence = true)
        JsonReader(StringReader(text)).use { reader ->
            readIosJson(reader, expectedHash)
        }
    }

    private fun readIosJson(reader: JsonReader, expectedHash: ByteArray) {
        var workchain: Int? = null
        var hash: ByteArray? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "workchain" -> {
                    require(workchain == null && reader.peek() == JsonToken.NUMBER) {
                        "iOS TON workchain is duplicated or invalid"
                    }
                    workchain = reader.nextInt()
                }
                "hash" -> {
                    require(hash == null && reader.peek() == JsonToken.STRING) {
                        "iOS TON hash is duplicated or invalid"
                    }
                    val base64 = reader.nextString()
                    hash = Base64.getDecoder().decode(base64)
                    require(Base64.getEncoder().encodeToString(hash) == base64) {
                        "iOS TON hash is not canonical Base64"
                    }
                }
                else -> throw IllegalArgumentException("iOS TON address has an unknown field")
            }
        }
        reader.endObject()
        require(
            reader.peek() == JsonToken.END_DOCUMENT && workchain == 0 &&
                hash?.contentEquals(expectedHash) == true
        ) {
            "iOS TON V4R2 address differs from its public key"
        }
    }

    private fun String.hexToBytes(): ByteArray {
        require(length == ADDRESS_HASH_BYTES * HEX_CHARS_PER_BYTE) { "TON address hash is invalid" }
        return ByteArray(ADDRESS_HASH_BYTES) { index ->
            val high = this[index * HEX_CHARS_PER_BYTE].digitToIntOrNull(HEX_RADIX)
            val low = this[index * HEX_CHARS_PER_BYTE + 1].digitToIntOrNull(HEX_RADIX)
            require(high != null && low != null) { "TON address hash is invalid" }
            (high shl HEX_NIBBLE_SHIFT or low).toByte()
        }
    }
}
