package jp.co.soramitsu.walletconnect.impl.presentation

import kotlinx.serialization.json.JsonObject
import org.web3j.crypto.RawTransaction
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * An `eth_sendTransaction` or `eth_signTransaction` transaction, read once for both the preview and
 * the signature: the preview describes exactly the recipient, value and data that are signed.
 */
internal class WalletConnectEvmTransaction private constructor(
    /** The chain id the transaction names, or null when it names none. */
    val chainId: BigInteger?,
    val from: String?,
    /** The `0x` hex recipient address: a contract for any call with data. */
    val to: String,
    private val value: BigInteger?,
    private val dataHex: String,
    private val nonce: BigInteger?,
    val gasPrice: BigInteger?,
    val gasLimit: BigInteger?
) {
    /** The value as signed: RLP encodes an absent value as zero. */
    val signedValue: BigInteger
        get() = value?.takeIf { it.signum() > 0 } ?: BigInteger.ZERO

    /** The calldata bytes as signed. */
    val data: ByteArray
        get() = Numeric.hexStringToByteArray(dataHex)

    fun rawTransaction(): RawTransaction =
        RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, dataHex)

    companion object {
        private val ADDRESS = Regex("(0x)?[0-9a-fA-F]{40}")
        private val HEX_DATA = Regex("(0x)?([0-9a-fA-F]{2})*")

        /** Reads a dApp transaction; one that cannot be read exactly is refused, never guessed. */
        fun parse(message: String): WalletConnectEvmTransaction {
            val transaction = runCatching { message.toJsonObject() }.getOrNull()
                ?: rejectRequest("The transaction is malformed")
            val to = transaction["to"].stringValue() ?: rejectRequest("The transaction has no recipient")
            requireRequest(ADDRESS.matches(to)) { "The transaction recipient is malformed" }
            val data = if ("data" in transaction) {
                transaction["data"].stringValue() ?: rejectRequest("The transaction data is malformed")
            } else {
                ""
            }
            requireRequest(HEX_DATA.matches(data)) { "The transaction data is malformed" }

            return WalletConnectEvmTransaction(
                chainId = evmChainIdOf(transaction["chainId"]),
                from = transaction["from"].stringValue(),
                to = to,
                value = transaction.quantity("value"),
                dataHex = data,
                nonce = transaction.quantity("nonce"),
                gasPrice = transaction.quantity("gasPrice"),
                gasLimit = transaction.quantity("gasLimit")
            )
        }

        private fun JsonObject.quantity(key: String): BigInteger? {
            if (key !in this) return null
            val quantity = this[key].stringValue()?.let { runCatching { Numeric.decodeQuantity(it) }.getOrNull() }
                ?: rejectRequest("The transaction $key is malformed")
            requireRequest(quantity.signum() >= 0) { "The transaction $key is negative" }
            return quantity
        }
    }
}
