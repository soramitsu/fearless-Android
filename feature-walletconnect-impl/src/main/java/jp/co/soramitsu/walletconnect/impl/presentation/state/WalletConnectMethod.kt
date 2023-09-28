package jp.co.soramitsu.walletconnect.impl.presentation.state

enum class WalletConnectMethod(val method: String) {
    PolkadotSignTransaction("polkadot_signTransaction"),
    PolkadotSignMessage("polkadot_signMessage"),
    EthereumSignTransaction("eth_signTransaction"),
    EthereumSendTransaction("eth_sendTransaction"),
    EthereumPersonalSign("personal_sign"),
    EthereumSignTypeData("eth_signTypedData"),
    EthereumSignTypeDataV4("eth_signTypedData_v4")
}