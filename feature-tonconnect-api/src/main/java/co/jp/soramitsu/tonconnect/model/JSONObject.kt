package co.jp.soramitsu.tonconnect.model

import org.json.JSONObject

fun JSONObject.optStringCompat(vararg keys: String): String? {
    for (key in keys) {
        val value = optString(key)
        if (!value.isNullOrBlank()) {
            return value
        }
    }
    return null
}

fun JSONObject.getLongCompat(key: String): Long {
    return when (val value = opt(key)) {
        is Long -> value
        is Number -> value.toLong()
        is String -> value.toLong()
        else -> throw IllegalArgumentException("Value for key $key is not a Long")
    }
}