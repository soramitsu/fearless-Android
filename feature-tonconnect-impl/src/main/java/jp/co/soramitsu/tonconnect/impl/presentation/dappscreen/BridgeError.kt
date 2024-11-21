package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen


enum class BridgeError(val code: Int, val message: String) {
    UNKNOWN(0, "Unknown error"),
    BAD_REQUEST(1, "Bad request"),
    APP_MANIFEST_NOT_FOUND(2, "App manifest not found"),
    APP_MANIFEST_CONTENT_ERROR(3, "App manifest content error"),
    UNKNOWN_APP(100, "Unknown app"),
    USER_DECLINED_TRANSACTION(300, "User declined the transaction"),
    METHOD_NOT_SUPPORTED(400, "Method not supported");

    class Exception(val error: BridgeError) : Throwable(error.message)
}
