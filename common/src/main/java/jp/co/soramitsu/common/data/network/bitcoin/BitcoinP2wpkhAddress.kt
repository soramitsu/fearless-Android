package jp.co.soramitsu.common.data.network.bitcoin

/** BIP173 validation for the v0, 20-byte output type supported by this wallet's signer. */
object BitcoinP2wpkhAddress {
    /** Checks case, network, checksum, witness version and canonical padding before normalizing. */
    fun normalize(address: String, network: BitcoinIndexerRoutes.Network): String {
        val value = address.trim()
        require(value.length == 42 && (value == value.lowercase() || value == value.uppercase()))
        val normalized = value.lowercase()
        val hrp = if (network == BitcoinIndexerRoutes.Network.Mainnet) "bc" else "tb"
        require(normalized.startsWith("${hrp}1q"))
        val words = normalized.substring(3).map { ALPHABET.indexOf(it).also { index -> require(index >= 0) } }
        val expanded = hrp.map { it.code shr 5 } + 0 + hrp.map { it.code and 31 }
        var checksum = 1
        (expanded + words).forEach { valueWord ->
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor valueWord
            GENERATORS.forEachIndexed { index, generator ->
                if (((top ushr index) and 1) != 0) checksum = checksum xor generator
            }
        }
        require(checksum == 1)
        // Exactly 32 five-bit payload words encode 20 bytes, without padding.
        require(words.size == 39 && words.first() == 0)
        return normalized
    }

    private const val ALPHABET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATORS = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
}
