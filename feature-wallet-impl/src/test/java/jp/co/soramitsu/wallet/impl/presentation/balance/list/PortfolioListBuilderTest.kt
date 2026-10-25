package jp.co.soramitsu.wallet.impl.presentation.balance.list

import java.math.BigDecimal
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroup
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroupMember
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups
import org.junit.Assert.*
import org.junit.Test

class PortfolioListBuilderTest {
    private val groups = ResolvedTokenGroups(listOf(ResolvedTokenGroup("dot", "DOT", "Polkadot", 10,
        listOf(ResolvedTokenGroupMember("relay", "0"), ResolvedTokenGroupMember("hub", "dot"), ResolvedTokenGroupMember("other", "dot")))))

    @Test fun `approved members sum their own quantities and prices excluding hidden members`() {
        val rows = build(listOf(asset("relay", "0", "2", "10"), asset("hub", "dot", "3", "12")),
            hidden = listOf(asset("other", "dot", "500", "999", hidden = true)))
        val group = rows.filterIsInstance<PortfolioListItem.TokenGroup>().single()
        assertEquals(BigDecimal("5"), group.total)
        assertEquals(BigDecimal("22"), group.fiat)
        assertEquals(1, group.hiddenCount)
        assertFalse(group.priceIncomplete)
    }

    @Test fun `same ticker unreviewed detected and testnet assets never join a group`() {
        val reviewed = asset("relay", "0", "2", "10")
        val impostor = asset("fake", "0", "2", "10")
        val detected = asset("hub", "dot", "2", "10").let { it.copy(state = it.state.copy(metadataTrust = "unverified")) }
        val testnet = asset("other", "dot", "2", "10").let { it.copy(state = it.state.copy(isTestnet = true)) }
        val rows = build(listOf(reviewed, impostor, detected, testnet))
        assertTrue(rows.none { it is PortfolioListItem.TokenGroup })
        assertEquals(4, rows.filterIsInstance<PortfolioListItem.AssetRow>().size)
    }

    @Test fun `funded unpriced member has quantity and incomplete price without inherited price`() {
        val rows = build(listOf(asset("relay", "0", "1", "10"), asset("hub", "dot", "100", null)))
        val group = rows.filterIsInstance<PortfolioListItem.TokenGroup>().single()
        assertEquals(BigDecimal("101"), group.total)
        assertEquals(BigDecimal.TEN, group.fiat)
        assertTrue(group.priceIncomplete)
    }

    @Test fun `shown zero stays inline and automatic zero waits behind group expansion`() {
        val rows = build(listOf(asset("relay", "0", "2", "10"), asset("hub", "dot", "0", null),
            asset("other", "dot", "0", null, preference = "shown")), ui = PortfolioUiState(expandedGroups = setOf("dot")))
        assertEquals(listOf("relay", "other"), rows.filterIsInstance<PortfolioListItem.AssetRow>().map { it.asset.state.chainId })
        assertEquals("dot", rows.filterIsInstance<PortfolioListItem.ZeroSummary>().single().groupId)
    }

    @Test fun `empty wallet shows empty card and collapsed popular summary`() {
        val rows = build(listOf(asset("relay", "0", "0", null)), positive = false)
        assertEquals(PortfolioListItem.EmptyWallet, rows.first())
        assertEquals(2, rows.size)
        assertFalse((rows.last() as PortfolioListItem.ZeroSummary).expanded)
    }

    @Test fun `network lens ranks by trusted fiat and zero sections trail collapsed`() {
        val rows = build(listOf(asset("hub", "dot", "2", "5"), asset("relay", "0", "1", "10"), asset("other", "dot", "0", null)),
            ui = PortfolioUiState(lens = PortfolioLens.ByNetwork, collapsedNetworks = setOf("relay")))
        assertEquals(listOf("relay", "hub"), rows.filterIsInstance<PortfolioListItem.NetworkHeader>().map { it.first.state.chainId })
        assertEquals(listOf("hub"), rows.filterIsInstance<PortfolioListItem.AssetRow>().map { it.asset.state.chainId })
        assertTrue(rows.last() is PortfolioListItem.ZeroSummary)
    }

    @Test fun `selected network is flat and cannot inherit a multi-network amount`() {
        val rows = PortfolioListBuilder.build(listOf(asset("hub", "dot", "2", "5")), emptyList(), groups, PortfolioUiState(), "hub", true)
        assertEquals(1, rows.size)
        assertEquals(BigDecimal("2"), (rows.single() as PortfolioListItem.AssetRow).asset.total)
    }

    @Test fun `duplicate row and leaked hidden input cannot double count`() {
        val a = asset("relay", "0", "1", "5")
        val row = build(listOf(a, a, asset("hub", "dot", "100", "500", hidden = true)))
            .filterIsInstance<PortfolioListItem.AssetRow>().single()
        assertEquals(BigDecimal.ONE, row.asset.total)
        assertEquals(BigDecimal("5"), row.asset.trustedFiat)
    }

    @Test fun `hiding the second approved member renders the surviving member as a flat row`() {
        val relay = asset("relay", "0", "2", "10")
        val hub = asset("hub", "dot", "3", "12", hidden = true)
        val row = build(listOf(relay), hidden = listOf(hub)).single() as PortfolioListItem.AssetRow
        assertEquals(relay, row.asset)
        assertFalse(row.nested)
    }

    @Test fun `unverified metadata cannot influence trusted fiat network sorting`() {
        val detected = asset("fake", "0", "2", "100000").let { it.copy(state = it.state.copy(metadataTrust = "unverified")) }
        val rows = build(listOf(detected, asset("relay", "0", "1", "10")), ui = PortfolioUiState(lens = PortfolioLens.ByNetwork))
        assertEquals(listOf("relay", "fake"), rows.filterIsInstance<PortfolioListItem.NetworkHeader>().map { it.first.state.chainId })
    }

    @Test fun `unknown holding cannot become empty wallet or automatic zero`() {
        val unknown = asset("relay", "0", "0", null).copy(total = null)
        val rows = build(listOf(unknown), positive = false)
        assertFalse(unknown.isAutomaticZero)
        assertTrue(rows.none { it is PortfolioListItem.EmptyWallet || it is PortfolioListItem.ZeroSummary })
        assertNull(rows.filterIsInstance<PortfolioListItem.AssetRow>().single().asset.total)
    }

    @Test fun `group with an unknown holding cannot publish a partial quantity as total`() {
        val unknown = asset("hub", "dot", "0", null).copy(total = null)
        val group = build(listOf(asset("relay", "0", "2", "10"), unknown))
            .filterIsInstance<PortfolioListItem.TokenGroup>().single()
        assertNull(group.total)
        assertEquals(BigDecimal.TEN, group.fiat)
        assertTrue(group.priceIncomplete)
    }

    private fun build(visible: List<PortfolioAsset>, hidden: List<PortfolioAsset> = emptyList(), ui: PortfolioUiState = PortfolioUiState(), positive: Boolean = true) =
        PortfolioListBuilder.build(visible, hidden, groups, ui, null, positive)

    private fun asset(chain: String, id: String, total: String, fiat: String?, hidden: Boolean = false, preference: String = "auto") = PortfolioAsset(
        AssetListItemViewState(index = 0, assetIconUrl = "", assetName = "Polkadot", assetChainName = chain, assetSymbol = "DOT",
            assetTokenFiat = null, assetTokenRate = null, assetTransferableBalance = total, assetTransferableBalanceFiat = fiat,
            assetChainUrls = emptyMap(), chainId = chain, chainAssetId = id, isSupported = true, isHidden = hidden,
            assetPreference = preference, isTestnet = false, ecosystemId = "substrate", priceTrust = if (fiat != null) "canonical" else "untrusted"),
        BigDecimal(total), fiat?.let(::BigDecimal), "$", null)
}
