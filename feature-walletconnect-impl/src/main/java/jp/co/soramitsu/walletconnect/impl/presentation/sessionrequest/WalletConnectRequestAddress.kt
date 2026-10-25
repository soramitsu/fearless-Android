package jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress

/** Decode the requested network's account identity; invalid inputs never compare equal. */
internal fun walletConnectRequestAccountId(isEthereumBased: Boolean, address: String?): ByteArray? {
    address ?: return null
    return runCatching {
        if (isEthereumBased) {
            val hex = if (address.startsWith("0x", ignoreCase = true)) address.substring(2) else address
            require(hex.matches(Regex("[0-9a-fA-F]{40}")))
            hex.fromHex()
        } else {
            val accountId = address.toAccountId()
            // The legacy decoder accepts extra trailing bytes; canonical round trip rejects them.
            require(accountId.toAddress(address.addressByte()) == address)
            accountId
        }
    }.getOrNull()
}

internal fun matchesWalletConnectRequestAddress(
    isEthereumBased: Boolean,
    walletAddress: String?,
    requestAddress: String?
): Boolean {
    val wallet = walletConnectRequestAccountId(isEthereumBased, walletAddress) ?: return false
    val requested = walletConnectRequestAccountId(isEthereumBased, requestAddress) ?: return false
    return wallet.contentEquals(requested)
}
