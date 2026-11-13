package jp.co.soramitsu.walletconnect.impl.presentation

import androidx.annotation.StringRes
import co.jp.soramitsu.feature_walletconnect_impl.R

enum class WalletConnectMethod(val method: String) {
    PolkadotSignTransaction("polkadot_signTransaction"),
    PolkadotSignMessage("polkadot_signMessage"),
    EthereumSignTransaction("eth_signTransaction"),
    EthereumSendTransaction("eth_sendTransaction"),
    EthereumPersonalSign("personal_sign"),
    EthereumSignTypedData("eth_signTypedData"),
    EthereumSign("eth_sign"),
    EthereumSignTypedDataV4("eth_signTypedData_v4");

    /** Whether the method belongs to the `eip155` namespace; the others belong to `polkadot`. */
    val isEvmMethod: Boolean
        get() = this != PolkadotSignTransaction && this != PolkadotSignMessage
}

/** The success sheet's title and text for a request that was only signed: its result went to the dApp. */
internal data class SignedRequestSuccessText(@StringRes val title: Int, @StringRes val text: Int)

/**
 * Null for eth_sendTransaction, the one request this wallet broadcasts itself: the sheet's default
 * "Transaction sent" is true only for it. Every other method hands the dApp a signature.
 */
internal fun signedRequestSuccessText(method: String): SignedRequestSuccessText? = when (method) {
    WalletConnectMethod.EthereumSendTransaction.method -> null
    WalletConnectMethod.EthereumSignTransaction.method,
    WalletConnectMethod.PolkadotSignTransaction.method -> SignedRequestSuccessText(
        title = R.string.walletconnect_transaction_signed_title,
        text = R.string.walletconnect_transaction_signed_text
    )
    else -> SignedRequestSuccessText(
        title = R.string.walletconnect_message_signed_title,
        text = R.string.walletconnect_message_signed_text
    )
}
