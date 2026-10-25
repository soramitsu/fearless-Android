package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonRequestWalletBindingTest {
    @Test
    fun explicitRequestWalletWinsOverGloballySelectedConnectionWallet() {
        assertEquals(7L, tonRequestWalletId(7, "https://app.example", listOf("https://app.example" to 9)))
    }

    @Test
    fun explicitBindingDoesNotDependOnFallbackAvailability() {
        assertEquals(7L, tonRequestWalletId(7, null, emptyList()))
    }

    @Test
    fun absentBindingFailsClosedInsteadOfSelectingGlobalWallet() {
        assertNull(tonRequestWalletId(null, "https://app.example", emptyList()))
    }

    @Test
    fun matchingConnectedHostSuppliesRequestBinding() {
        assertEquals(7L, tonRequestWalletId(null, "https://app.example/request", listOf("https://app.example/connect" to 7)))
    }

    @Test
    fun unrelatedConnectedHostCannotSupplyBinding() {
        assertNull(tonRequestWalletId(null, "https://app.example", listOf("https://other.example" to 7)))
    }

    @Test
    fun malformedOrMissingRequestUrlFailsClosed() {
        assertNull(tonRequestWalletId(null, null, listOf("https://app.example" to 7)))
        assertNull(tonRequestWalletId(null, "bad url", listOf("https://app.example" to 7)))
    }

    @Test
    fun malformedConnectionCannotHideFollowingMatchingConnection() {
        assertEquals(7L, tonRequestWalletId(null, "https://app.example", listOf("bad url" to 8, "https://app.example" to 7)))
    }

    @Test
    fun firstMatchingUnboundConnectionDoesNotFallThroughToAnotherWallet() {
        assertNull(tonRequestWalletId(null, "https://app.example", listOf("https://app.example" to null, "https://app.example" to 9)))
    }

    @Test
    fun bindingReplacementAndRemovalAreObservableValues() {
        assertEquals(7L, tonRequestWalletId(null, "https://app.example", listOf("https://app.example" to 7)))
        assertEquals(9L, tonRequestWalletId(null, "https://app.example", listOf("https://app.example" to 9)))
        assertNull(tonRequestWalletId(null, "https://app.example", emptyList()))
    }
}
