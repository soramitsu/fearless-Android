package jp.co.soramitsu.tonconnect.impl.domain

import jp.co.soramitsu.common.utils.toBoc
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.TonConnectRawMessage
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import org.ton.block.AddrStd

/**
 * The checks a dApp `sendTransaction` request must pass before it is shown to
 * the user and again before it is signed.
 */
object TonConnectSendTransactionPolicy {

    /** TON mainnet's global id, the only network TON Connect connections are made for. */
    const val MAINNET_NETWORK_ID = -239L

    /** No signed external message may stay valid for longer than this. */
    const val MAX_VALIDITY_SECONDS = 300L

    /**
     * Returns the `valid_until` to sign for [wallet]: the dApp's expiry, capped at
     * [MAX_VALIDITY_SECONDS] after [nowSeconds]. Throws [BridgeError.Exception]
     * with [BridgeError.BAD_REQUEST] when the request must not be signed.
     */
    fun validUntilToSign(
        request: TonConnectSignRequest,
        wallet: AddrStd,
        nowSeconds: Long,
        maxMessages: Int
    ): Long {
        requireValid(isMainnet(request.networkValue))
        requireValid(isFromWallet(request.fromValue, wallet))
        requireValid(request.messages.size in 1..maxMessages)
        requireValid(request.messages.all(::isWellFormed))

        val requestedExpiry = request.validUntil
        requireValid(requestedExpiry == null || requestedExpiry > nowSeconds)

        val latestExpiry = nowSeconds + MAX_VALIDITY_SECONDS
        return requestedExpiry?.coerceAtMost(latestExpiry) ?: latestExpiry
    }

    /** An absent network means the connection's network, which is always mainnet. */
    fun isMainnet(networkValue: String?): Boolean {
        return networkValue.isNullOrBlank() || networkValue.trim().toLongOrNull() == MAINNET_NETWORK_ID
    }

    /**
     * An absent `from` means the connected wallet. Raw (`0:…`) and user-friendly
     * forms of one account match; bounceable and test-only flags are ignored.
     */
    fun isFromWallet(fromValue: String?, wallet: AddrStd): Boolean {
        if (fromValue.isNullOrBlank()) return true
        val from = runCatching { AddrStd.parse(fromValue.trim()) }.getOrNull() ?: return false
        return from.workchainId == wallet.workchainId &&
            from.address.toByteArray().contentEquals(wallet.address.toByteArray())
    }

    /**
     * A message is signed exactly as requested, so every part of it must parse:
     * a payload or state init that cannot be read must not silently become empty.
     */
    fun isWellFormed(message: TonConnectRawMessage): Boolean = runCatching {
        message.address
        message.amount >= 0 &&
            isSingleCellOrAbsent(message.payloadValue) &&
            isSingleCellOrAbsent(message.stateInitValue) &&
            (message.stateInitValue.isNullOrBlank() || message.stateInit != null)
    }.getOrDefault(false)

    private fun isSingleCellOrAbsent(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        return runCatching { value.toBoc().roots.size == 1 }.getOrDefault(false)
    }

    private fun requireValid(condition: Boolean) {
        if (!condition) throw BridgeError.Exception(BridgeError.BAD_REQUEST)
    }
}
