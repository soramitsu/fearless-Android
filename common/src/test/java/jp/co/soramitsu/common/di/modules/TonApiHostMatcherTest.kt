package jp.co.soramitsu.common.di.modules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TonApiHostMatcherTest {

    @Test
    fun `isTonApiHost should allow tonapi root and subdomains`() {
        assertTrue(isTonApiHost("tonapi.io"))
        assertTrue(isTonApiHost("api.tonapi.io"))
        assertTrue(isTonApiHost("V2.TONAPI.IO"))
    }

    @Test
    fun `isTonApiHost should reject sibling and partial domains`() {
        assertFalse(isTonApiHost("eviltonapi.io"))
        assertFalse(isTonApiHost("tonapi.io.evil.com"))
        assertFalse(isTonApiHost("tonapi.i"))
    }
}
