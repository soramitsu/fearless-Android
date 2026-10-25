package jp.co.soramitsu.wallet.impl.di

import io.mockk.mockk
import jp.co.soramitsu.xcm.domain.ApprovedXcmRouteRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedXcmRouteRegistrySelectionTest {

    private val failures = mutableListOf<Exception>()

    @Test
    fun `disabled flag returns unavailable without invoking asset loader`() {
        var loaderInvoked = false

        val result = selectApprovedXcmRouteRegistry(enabled = false, onLoadFailure = failures::add) {
            loaderInvoked = true
            error("disabled selection must not read APK assets")
        }

        assertFalse(loaderInvoked)
        assertSame(ApprovedXcmRouteRegistry.unavailable(), result)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `enabled flag returns successfully loaded immutable registry`() {
        val loaded = mockk<ApprovedXcmRouteRegistry>()

        val result = selectApprovedXcmRouteRegistry(enabled = true, onLoadFailure = failures::add) { loaded }

        assertSame(loaded, result)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `enabled loader failure is logged and returns unavailable instead of escaping`() {
        val failure = IllegalArgumentException("malformed bundled registry")

        val result = selectApprovedXcmRouteRegistry(enabled = true, onLoadFailure = failures::add) { throw failure }

        assertSame(ApprovedXcmRouteRegistry.unavailable(), result)
        assertEquals(listOf(failure), failures)
    }
}
