package jp.co.soramitsu.irohaconnect.api

/** Recognises an Iroha Connect wallet invitation before the full, strict parse in the session service. */
object IrohaConnectLinks {
    private val PREFIXES = listOf("iroha://connect?", "irohaconnect://connect?")

    /** True for `iroha://connect?…` and `irohaconnect://connect?…`, the links `@iroha/connect-widget` shows. */
    fun isConnectLink(text: String?): Boolean = text != null && PREFIXES.any(text::startsWith)
}
