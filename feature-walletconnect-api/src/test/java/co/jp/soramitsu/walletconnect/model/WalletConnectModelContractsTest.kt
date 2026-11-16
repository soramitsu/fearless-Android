package co.jp.soramitsu.walletconnect.model

import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WalletConnectModelContractsTest {
    @Test
    fun unknownVerificationNeverClaimsAnAttestedOrigin() {
        val context = WalletConnectVerifyContext.unknown("https://claimed.example")
        assertEquals(WalletConnectValidation.UNKNOWN, context.validation)
        assertEquals("https://claimed.example", context.claimedUrl)
        assertNull(context.verifiedOrigin)
        assertFalse(context.isScam)
    }

    @Test
    fun proposalDisplayPropertiesComeFromProposerMetadata() {
        val metadata = WalletConnectMetadata("Dapp", "Description", "https://example.com", listOf("icon"), "app://return")
        val proposal = WalletConnectProposal(1, "topic", metadata, "key", emptyMap(), emptyMap(), "irn", 1234)
        assertEquals("Dapp", proposal.name)
        assertEquals("https://example.com", proposal.url)
        assertEquals(listOf("icon"), proposal.icons)
        assertEquals("app://return", proposal.proposer.redirect)
        assertEquals(1234L, proposal.expiryTimestamp)
    }

    @Test
    fun chooserStateParcelRetainsAvailableSelectedAndViewMode() {
        val state = ChainChooseState(listOf("eip155:1", "eip155:137"), listOf("eip155:137"), true)
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(state, 0)
            parcel.setDataPosition(0)
            assertEquals(state, parcel.readParcelable<ChainChooseState>(ChainChooseState::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun chooserResultParcelRetainsSelectedChainSet() {
        val result = ChainChooseResult(setOf("eip155:137", "eip155:1"))
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(result, 0)
            parcel.setDataPosition(0)
            assertEquals(result, parcel.readParcelable<ChainChooseResult>(ChainChooseResult::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }
}
