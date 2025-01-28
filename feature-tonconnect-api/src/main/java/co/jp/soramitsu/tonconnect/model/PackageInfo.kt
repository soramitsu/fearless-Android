package co.jp.soramitsu.tonconnect.model

enum class BridgeMethod(val title: String) {
    SEND_TRANSACTION("sendTransaction"),

    // SIGN_DATA("signData"),
    DISCONNECT("disconnect"),
    UNKNOWN("unknown");

    companion object {
        @Suppress("FunctionMinLength")
        fun of(title: String): BridgeMethod {
            return entries.firstOrNull { it.title == title } ?: UNKNOWN
        }
    }
}
