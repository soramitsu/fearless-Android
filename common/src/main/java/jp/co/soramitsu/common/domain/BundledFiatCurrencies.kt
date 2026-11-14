package jp.co.soramitsu.common.domain

import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency

/**
 * The currency list bundled into the app with its remote configuration, so money is shown with its symbol from the
 * first frame and offline. [read] gives the file's content; a missing or damaged file means no symbols, never a crash.
 */
class BundledFiatCurrencies(private val read: () -> ByteArray?) {

    private val byId: Map<String, FiatCurrency> by lazy {
        runCatching {
            val content = read() ?: return@lazy emptyMap()
            JsonParser.parseString(content.toString(Charsets.UTF_8)).asJsonArray.mapNotNull { element ->
                val currency = element.asJsonObject
                val id = currency.get("id")?.asString
                val symbol = currency.get("symbol")?.asString
                if (id.isNullOrBlank() || symbol.isNullOrBlank()) {
                    null
                } else {
                    FiatCurrency(id, symbol, currency.get("name")?.asString.orEmpty(), currency.get("icon")?.asString.orEmpty())
                }
            }.associateBy { it.id }
        }.getOrDefault(emptyMap())
    }

    operator fun get(id: String): FiatCurrency? = byId[id]
}
