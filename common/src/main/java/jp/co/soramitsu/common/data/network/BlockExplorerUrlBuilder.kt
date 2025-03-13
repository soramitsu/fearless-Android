package jp.co.soramitsu.common.data.network

import java.net.URL

class BlockExplorerUrlBuilder(private val baseUrl: String, private val types: List<String>) {
    enum class Type(customName: String? = null) {
        EXTRINSIC, ACCOUNT, EVENT, TX, TRANSFER, ADDRESS, TON_ACCOUNT("tonAccount"), TON_TRANSACTION("tonTransaction");

        val nameLowercase = customName ?: name.lowercase()
    }

    private fun Type.supportedOrNull() = when (nameLowercase) {
        in types -> true
        else -> null
    }

    init {
        if (!baseUrl.contains("{type}") || !baseUrl.contains("{value}")) {
            throw IllegalArgumentException("Wrong baseUrl format")
        }
    }

    fun build(type: Type, value: String): String? = type.supportedOrNull()?.let {
        when (type) {
            Type.TON_ACCOUNT -> {
                val url = URL(baseUrl)
                "${url.protocol}://${url.host}/${value}"
            }
            Type.TON_TRANSACTION -> {
                baseUrl.replace("{type}", "transaction").replace("{value}", value)
            }
            else -> {
                baseUrl.replace("{type}", type.nameLowercase).replace("{value}", value)
            }
        }
    }
}


