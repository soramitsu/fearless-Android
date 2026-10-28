package jp.co.soramitsu.walletconnect.impl.presentation.summary

import com.reown.walletkit.client.Wallet
import jp.co.soramitsu.common.utils.readableUtf8Text
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.walletconnect.impl.presentation.PolkadotRefusal
import jp.co.soramitsu.walletconnect.impl.presentation.PolkadotSignPayload
import jp.co.soramitsu.walletconnect.impl.presentation.PolkadotTransaction
import jp.co.soramitsu.walletconnect.impl.presentation.PolkadotTransactionRefusedException
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectEvmTransaction
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectMethod
import jp.co.soramitsu.walletconnect.impl.presentation.message
import jp.co.soramitsu.walletconnect.impl.presentation.polkadotMessageBytes
import java.math.BigInteger
import java.util.concurrent.CancellationException

/** What a WalletConnect request asks the user to sign, decoded from exactly what is signed. */
internal sealed interface WalletConnectRequestSummary {
    /** False when part of the request could not be decoded and its raw data must be checked. */
    val isComplete: Boolean

    /** A message signature (`personal_sign`, `eth_sign`, `polkadot_signMessage`). */
    data class Message(val text: String) : WalletConnectRequestSummary {
        override val isComplete = true
    }

    data class EvmTransaction(
        /** The recipient: a contract for any call with data. */
        val to: String,
        /** The native value sent, in wei. */
        val value: BigInteger,
        /** The token call the data encodes, or null when the data is not one the wallet knows. */
        val call: EvmTokenCall?,
        /** The function selector (all the data when it is shorter), or null when the transaction has no data. */
        val selector: String?,
        val gasPrice: BigInteger? = null,
        val gasLimit: BigInteger? = null
    ) : WalletConnectRequestSummary {
        override val isComplete: Boolean
            get() = (selector == null || call != null) && gasPrice != null && gasLimit != null
    }

    data class TypedData(val typedData: TypedDataSummary) : WalletConnectRequestSummary {
        override val isComplete: Boolean
            get() = typedData.isComplete
    }

    data class PolkadotTransaction(
        /** Null when the chain's runtime could not decode the call. */
        val call: PolkadotCallSummary?,
        /** The tip in planks of the native token. */
        val tip: BigInteger,
        val isImmortal: Boolean,
        /** Why the wallet refuses to sign it, exactly as the signer would; null when it can be signed. */
        val refusal: PolkadotRefusal? = null
    ) : WalletConnectRequestSummary {
        override val isComplete: Boolean
            get() = call?.isComplete == true
    }

    /** Nothing could be decoded: only the raw request can be shown. */
    data object Undecoded : WalletConnectRequestSummary {
        override val isComplete = false
    }
}

/**
 * Decodes a request with the same readers the signer uses, so the summary never describes other
 * bytes than the ones signed. [runtimeOf] gives the runtime a Polkadot call is decoded with.
 */
internal class WalletConnectRequestDecoder(
    private val runtimeOf: suspend (Chain) -> RuntimeSnapshot?
) {

    suspend fun summary(chain: Chain, request: Wallet.Model.SessionRequest): WalletConnectRequestSummary {
        val method = WalletConnectMethod.values().firstOrNull { it.method == request.request.method }
        val message = request.request.message
        if (method == null || message == null) return WalletConnectRequestSummary.Undecoded
        return try {
            when (method) {
                WalletConnectMethod.EthereumPersonalSign,
                WalletConnectMethod.EthereumSign -> WalletConnectRequestSummary.Message(message)
                WalletConnectMethod.PolkadotSignMessage -> WalletConnectRequestSummary.Message(polkadotMessageText(message))
                WalletConnectMethod.EthereumSendTransaction,
                WalletConnectMethod.EthereumSignTransaction -> evmTransaction(message)
                WalletConnectMethod.EthereumSignTypedData,
                WalletConnectMethod.EthereumSignTypedDataV4 -> TypedDataSummaries.summary(message)
                    ?.let(WalletConnectRequestSummary::TypedData)
                    ?: WalletConnectRequestSummary.Undecoded
                WalletConnectMethod.PolkadotSignTransaction -> polkadotTransaction(chain, message)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (ignored: Exception) {
            // The signer refuses what cannot be read; the preview shows it as raw data.
            WalletConnectRequestSummary.Undecoded
        }
    }

    private fun evmTransaction(message: String): WalletConnectRequestSummary {
        val transaction = WalletConnectEvmTransaction.parse(message)
        val data = transaction.data
        return WalletConnectRequestSummary.EvmTransaction(
            to = checksumAddress(transaction.to),
            value = transaction.signedValue,
            call = EvmCalldata.decode(data),
            selector = data.takeIf { it.isNotEmpty() }?.let { EvmCalldata.selector(it) ?: it.toHexString(withPrefix = true) },
            gasPrice = transaction.gasPrice,
            gasLimit = transaction.gasLimit
        )
    }

    private suspend fun polkadotTransaction(chain: Chain, message: String): WalletConnectRequestSummary {
        val payload = PolkadotSignPayload.parse(message)
        val runtime = runtimeOf(chain)
        val call = runtime?.let { PolkadotCallDecoder(it) { accountId -> chain.addressOf(accountId) }.decode(payload.method) }
        return WalletConnectRequestSummary.PolkadotTransaction(
            call = call,
            tip = payload.tip,
            isImmortal = payload.isImmortal,
            refusal = refusalOf(chain, payload, runtime)
        )
    }

    // The signer's own checks, so the preview shows a refusal before the user taps Sign.
    // Without the runtime only its network and era can be checked; signing then waits for the runtime.
    private fun refusalOf(
        chain: Chain,
        payload: PolkadotSignPayload,
        runtime: RuntimeSnapshot?
    ): PolkadotRefusal? = try {
        if (runtime == null) {
            PolkadotTransaction.requireSignableWithoutRuntime(chain, payload)
        } else {
            PolkadotTransaction.read(chain, payload, runtime)
        }
        null
    } catch (refused: PolkadotTransactionRefusedException) {
        refused.refusal
    }

    // polkadot.js dApps send text as hex: what the user reads is the text it encodes.
    private fun polkadotMessageText(message: String): String {
        if (!message.startsWith("0x")) return message
        return readableUtf8Text(polkadotMessageBytes(message)) ?: message
    }
}
