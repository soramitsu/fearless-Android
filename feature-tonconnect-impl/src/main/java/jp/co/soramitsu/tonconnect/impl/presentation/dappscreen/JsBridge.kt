package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import androidx.collection.ArrayMap
import org.json.JSONArray

abstract class JsBridge(
    val windowKey: String,
    val timeout: Long? = null
) {

    abstract val availableFunctions: Array<String>

    val keys = ArrayMap<String, Any>()

    /**
     * Don't use reflection because it's not safe and can be used for hacking.
     * Use direct call of functions.
     */
    abstract suspend fun invokeFunction(name: String, args: JSONArray): Any?

    /**
     * The script injected into one main-frame page. It must send [token] with
     * every call and accept only responses that carry it, without exposing it.
     */
    abstract fun jsInjection(token: String): String

    companion object {
        const val TOKEN_KEY = "token"
    }
}
