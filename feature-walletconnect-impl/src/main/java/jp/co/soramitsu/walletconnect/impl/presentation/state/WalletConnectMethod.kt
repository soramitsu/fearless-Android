package jp.co.soramitsu.walletconnect.impl.presentation.state

enum class WalletConnectMethod(val method: String) {
    PolkadotSignTransaction("polkadot_signTransaction"),
    PolkadotSignMessage("polkadot_signMessage"),
    EthereumSignTransaction("eth_signTransaction"),
    EthereumSendTransaction("eth_sendTransaction"),
    EthereumPersonalSign("personal_sign"),
    EthereumSignTypedData("eth_signTypedData"),
    EthereumSign("eth_sign"),
    EthereumSignTypedDataV4("eth_signTypedData_v4")
}
