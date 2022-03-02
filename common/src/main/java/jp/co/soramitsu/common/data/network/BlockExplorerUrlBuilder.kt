package jp.co.soramitsu.common.data.network

class BlockExplorerUrlBuilder(private val baseUrl: String, private val types: List<String>) {
    enum class Type {
        EXTRINSIC, ACCOUNT, EVENT;

        val nameLowercase = name.lowercase()
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

    fun build(type: Type, value: String) = type.supportedOrNull()?.let {
        baseUrl.replace("{type}", type.nameLowercase).replace("{value}", value)
    }
}
