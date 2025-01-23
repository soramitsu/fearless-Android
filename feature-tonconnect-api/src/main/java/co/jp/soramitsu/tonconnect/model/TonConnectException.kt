package co.jp.soramitsu.tonconnect.model

sealed class TonConnectException(message: String) : Exception(message) {

    data class UnsupportedVersion(
        val version: Int
    ) : TonConnectException("Unsupported TonConnect version: $version")

    data class WrongClientId(
        val clientId: String?
    ) : TonConnectException("Wrong clientId: '$clientId'")

    data class RequestParsingError(
        val data: String?
    ) : TonConnectException("Invalid ConnectRequest data: '$data'")

    data class ReturnParsingError(
        val data: String?
    ) : TonConnectException("Invalid return data: '$data'")
}
