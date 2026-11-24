package jp.co.soramitsu.common

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update prompts open the wallet's Play Store page, falling back to a browser. A device with neither (no Play
 * Store, no browser) made the fallback throw ActivityNotFoundException, which closed the app.
 */
class PlayMarketUrisTest {

    @Test
    fun `the Play Store app is opened when it is installed`() {
        val opened = mutableListOf<String>()

        assertTrue(openPlayMarket { opened += it })
        assertEquals(listOf(PLAY_MARKET_APP_URI), opened)
    }

    @Test
    fun `without the Play Store app the store page opens in a browser`() {
        val opened = mutableListOf<String>()

        assertTrue(openPlayMarket { uri -> if (uri == PLAY_MARKET_APP_URI) throw ActivityNotFoundException() else opened += uri })
        assertEquals(listOf(PLAY_MARKET_BROWSER_URI), opened)
    }

    @Test
    fun `with neither the Play Store nor a browser nothing opens and nothing throws`() {
        val attempted = mutableListOf<String>()

        assertFalse(openPlayMarket { uri -> attempted += uri; throw ActivityNotFoundException() })
        assertEquals(listOf(PLAY_MARKET_APP_URI, PLAY_MARKET_BROWSER_URI), attempted)
    }
}
