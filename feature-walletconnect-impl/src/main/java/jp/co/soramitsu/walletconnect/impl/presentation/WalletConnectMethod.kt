package jp.co.soramitsu.walletconnect.impl.presentation

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
