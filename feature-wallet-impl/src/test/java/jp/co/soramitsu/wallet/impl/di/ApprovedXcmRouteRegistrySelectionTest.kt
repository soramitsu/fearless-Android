package jp.co.soramitsu.wallet.impl.di

import jp.co.soramitsu.xcm.domain.ApprovedXcmRouteRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedXcmRouteRegistrySelectionTest {

    @Test
    fun `disabled flag returns unavailable without invoking asset loader`() {
        var loaderInvoked = false

        val result = selectApprovedXcmRouteRegistry(enabled = false) {
            loaderInvoked = true
            error("disabled selection must not read APK assets")
        }

        assertFalse(loaderInvoked)
        assertNotNull(result)
    }

    @Test
    fun `enabled flag returns successfully loaded immutable registry`() {
        val expected = ApprovedXcmRouteRegistry.unavailable()
        var loaderInvoked = false

        val result = selectApprovedXcmRouteRegistry(enabled = true) {
            loaderInvoked = true
            expected
        }

        assertTrue(loaderInvoked)
        assertSame(expected, result)
    }

    @Test
    fun `enabled loader failure returns unavailable instead of escaping`() {
        var loaderInvoked = false

        val result = selectApprovedXcmRouteRegistry(enabled = true) {
            loaderInvoked = true
            throw IllegalArgumentException("malformed bundled registry")
        }

        assertTrue(loaderInvoked)
        assertNotNull(result)
    }
}
