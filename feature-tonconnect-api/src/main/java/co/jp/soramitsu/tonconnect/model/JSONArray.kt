package co.jp.soramitsu.tonconnect.model

import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.toStringList(): List<String> {
    val list = mutableListOf<String>()
    for (i in 0 until length()) {
        when (val item = get(i)) {
            is String -> list.add(item)
            is JSONArray, is JSONObject -> list.add(item.toString())
            else -> list.add(item.toString())
        }
    }
    return list
}
