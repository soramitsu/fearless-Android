package jp.co.soramitsu.common.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BundledFiatCurrenciesTest {
    private val list = """[
        {"id":"usd","symbol":"$","name":"US Dollar","icon":"https://example.test/usd.svg"},
        {"id":"jpy","symbol":"¥","name":"Yen"},
        {"id":"","symbol":"?"},
        {"id":"noSymbol","symbol":""}
    ]""".trimIndent().toByteArray()

    @Test fun readsEachCurrencysSymbol() {
        val currencies = BundledFiatCurrencies { list }
        assertEquals("$", currencies["usd"]?.symbol)
        assertEquals("¥", currencies["jpy"]?.symbol)
        assertEquals("", currencies["jpy"]?.icon)
    }

    @Test fun entriesWithoutAnIdOrSymbolAndUnknownCurrenciesAreNotKnown() {
        val currencies = BundledFiatCurrencies { list }
        assertNull(currencies[""])
        assertNull(currencies["noSymbol"])
        assertNull(currencies["cad"])
    }

    @Test fun aMissingOrDamagedFileMeansNoSymbolsNotACrash() {
        assertNull(BundledFiatCurrencies { null }["usd"])
        assertNull(BundledFiatCurrencies { "not json".toByteArray() }["usd"])
        assertNull(BundledFiatCurrencies { error("unreadable") }["usd"])
    }

    @Test fun theFileIsReadOnce() {
        var reads = 0
        val currencies = BundledFiatCurrencies { reads++; list }
        currencies["usd"]
        currencies["jpy"]
        assertEquals(1, reads)
    }
}
