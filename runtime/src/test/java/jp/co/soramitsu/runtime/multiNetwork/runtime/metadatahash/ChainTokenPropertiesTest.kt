package jp.co.soramitsu.runtime.multiNetwork.runtime.metadatahash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChainTokenPropertiesTest {

    @Test
    fun `reads single and multi-token properties as Gson parses them`() {
        val single = ChainTokenProperties.parse(mapOf("tokenDecimals" to 10.0, "tokenSymbol" to "DOT", "ss58Format" to 0.0))
        assertEquals(10, single.decimals)
        assertEquals("DOT", single.symbol)
        val multi = ChainTokenProperties.parse(mapOf("tokenDecimals" to listOf(12.0, 10.0), "tokenSymbol" to listOf("ACA", "DOT")))
        assertEquals(12, multi.decimals)
        assertEquals("ACA", multi.symbol)
        assertEquals(18, ChainTokenProperties.parse(mapOf("tokenDecimals" to 18, "tokenSymbol" to "GLMR")).decimals)
    }

    @Test
    fun `refuses missing, fractional, negative or oversized decimals and missing symbols`() {
        listOf(
            mapOf("tokenSymbol" to "DOT"),
            mapOf("tokenDecimals" to 10.5, "tokenSymbol" to "DOT"),
            mapOf("tokenDecimals" to -1.0, "tokenSymbol" to "DOT"),
            mapOf("tokenDecimals" to 256.0, "tokenSymbol" to "DOT"),
            mapOf("tokenDecimals" to 10.0),
            mapOf("tokenDecimals" to 10.0, "tokenSymbol" to ""),
            mapOf("tokenDecimals" to emptyList<Double>(), "tokenSymbol" to "DOT"),
            mapOf("tokenDecimals" to "10", "tokenSymbol" to "DOT")
        ).forEach { properties ->
            assertThrows(properties.toString(), IllegalArgumentException::class.java) { ChainTokenProperties.parse(properties) }
        }
    }
}
