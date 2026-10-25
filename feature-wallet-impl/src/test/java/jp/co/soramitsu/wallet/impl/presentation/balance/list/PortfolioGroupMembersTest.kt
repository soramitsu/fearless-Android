package jp.co.soramitsu.wallet.impl.presentation.balance.list

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.*
import org.junit.Assert.*
import org.junit.Test

class PortfolioGroupMembersTest {
    @Test fun `approved account can receive before balance row exists without introducing a valued holding`() {
        val asset = mockk<Asset>(relaxed = true) { every { id } returns "usdt" }
        val chain = mockk<Chain>(relaxed = true) {
            every { id } returns "hub"
            every { ecosystem } returns Ecosystem.Substrate
            every { assetsById } returns mapOf("usdt" to asset)
            every { minSupportedVersion } returns null
        }
        val groups = ResolvedTokenGroups(listOf(ResolvedTokenGroup("usdt", "USDT", "Tether", 6,
            listOf(ResolvedTokenGroupMember("hub", "usdt"), ResolvedTokenGroupMember("other", "contract")))))
        val rows = PortfolioGroupMembers.includeUnfunded(emptyList(), listOf(chain), groups, { true }, { "my-address" })
        assertEquals(1, rows.size)
        assertEquals("hub", rows.single().state.chainId)
        assertEquals("usdt", rows.single().state.chainAssetId)
        assertNull(rows.single().total)
        assertEquals("—", rows.single().state.assetTotalBalance)
        assertFalse(rows.single().isAutomaticZero)
        assertNull(rows.single().trustedFiat)
        assertEquals("my-address", rows.single().state.networkAccountLabel)
        assertTrue(PortfolioGroupMembers.includeUnfunded(emptyList(), listOf(chain), groups, { false }, { null }).isEmpty())
        assertEquals(rows, PortfolioGroupMembers.includeUnfunded(rows, listOf(chain), groups, { true }, { null }))
    }
}
