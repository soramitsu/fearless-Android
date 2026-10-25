package jp.co.soramitsu.core.utils

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.hash.Hasher.keccak256
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId

private val ETHEREUM_ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")
private val TON_RAW_ADDRESS_REGEX = Regex("^-?\\d+:[0-9a-fA-F]{64}$")
private val TON_USER_FRIENDLY_ADDRESS_REGEX = Regex("^[A-Za-z0-9_-]{48}$")

fun IChain.isValidAddress(address: String): Boolean {
    val normalized = address.trim()
    if (normalized.isEmpty()) return false

    return when (ecosystem) {
        Ecosystem.Substrate -> runCatching { normalized.toAccountId().size == SUBSTRATE_ACCOUNT_ID_SIZE }.getOrDefault(false)
        Ecosystem.EthereumBased,
        Ecosystem.Ethereum -> ETHEREUM_ADDRESS_REGEX.matches(normalized)
        Ecosystem.Ton -> TON_RAW_ADDRESS_REGEX.matches(normalized) || TON_USER_FRIENDLY_ADDRESS_REGEX.matches(normalized)
    }
}

/**
 * EIP-55: the case of the letters of a mixed-case EVM address is a checksum, so a mistyped mixed-case address is
 * detected. All-lowercase and all-uppercase addresses carry no checksum and are accepted as they are.
 */
fun String.isEvmChecksumValid(): Boolean {
    if (!ETHEREUM_ADDRESS_REGEX.matches(this)) return false
    val hex = substring(2)
    if (hex == hex.lowercase() || hex == hex.uppercase()) return true
    val hash = hex.lowercase().toByteArray(Charsets.US_ASCII).keccak256()
    return hex.withIndex().all { (index, char) ->
        val byte = hash[index / 2].toInt() and 0xff
        val nibble = if (index % 2 == 0) byte shr 4 else byte and 0xf
        !char.isLetter() || char.isUpperCase() == (nibble >= 8)
    }
}

private const val SUBSTRATE_ACCOUNT_ID_SIZE = 32
