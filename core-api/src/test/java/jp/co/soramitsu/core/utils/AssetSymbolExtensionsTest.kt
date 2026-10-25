package jp.co.soramitsu.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AssetSymbolExtensionsTest {

    @Test
    fun `bridged xc tokens match their native symbol`() {
        assertEquals("DOT", "xcdot".normalizedAssetSymbol())
        assertEquals("DOT", "xcDOT".normalizedAssetSymbol())
        assertEquals("USDT", " xcusdt\n".normalizedAssetSymbol())
    }

    @Test
    fun `native symbols are only trimmed and upper-cased`() {
        assertEquals("DOT", "dot".normalizedAssetSymbol())
        assertEquals("KSM", "KSM".normalizedAssetSymbol())
    }

    @Test
    fun `a token whose own symbol starts with XC keeps it`() {
        assertEquals("XCN", "XCN".normalizedAssetSymbol())
        assertEquals("XCDOT", "XCDOT".normalizedAssetSymbol())
    }

    @Test
    fun `the prefix alone is a symbol of its own`() {
        assertEquals("XC", "xc".normalizedAssetSymbol())
        assertEquals("", "  ".normalizedAssetSymbol())
    }
}
