package jp.co.soramitsu.core.utils

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
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

private const val SUBSTRATE_ACCOUNT_ID_SIZE = 32
