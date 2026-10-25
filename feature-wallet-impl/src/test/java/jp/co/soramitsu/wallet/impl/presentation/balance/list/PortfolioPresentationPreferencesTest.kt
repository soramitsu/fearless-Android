package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioPresentationPreferencesTest {

    private val store = InMemoryPreferences()
    private val preferences = PortfolioPresentationPreferences(store)

    @Test
    fun `testnets are off until the wallet turns them on, per wallet`() = runTest {
        assertFalse(preferences.observeShowTestnets(WALLET).first())

        preferences.setShowTestnets(WALLET, true)

        assertTrue(preferences.observeShowTestnets(WALLET).first())
        assertFalse(preferences.observeShowTestnets(OTHER_WALLET).first())
    }

    @Test
    fun `reviewed hidden funded assets accumulate per wallet`() = runTest {
        assertEquals(emptySet<String>(), preferences.observeReviewedHiddenFundedAssets(WALLET).first())

        preferences.markHiddenFundedAssetsReviewed(WALLET, setOf("chain:a"))
        preferences.markHiddenFundedAssetsReviewed(WALLET, setOf("chain:b"))
        preferences.markHiddenFundedAssetsReviewed(WALLET, emptySet())

        assertEquals(setOf("chain:a", "chain:b"), preferences.observeReviewedHiddenFundedAssets(WALLET).first())
        assertEquals(emptySet<String>(), preferences.observeReviewedHiddenFundedAssets(OTHER_WALLET).first())
    }

    @Test
    fun `reviewed hidden funded assets are bounded`() = runTest {
        val many = (0 until PortfolioPresentationPreferences.MAX_REMEMBERED_ASSETS + 40).mapTo(mutableSetOf()) { "chain:$it" }

        preferences.markHiddenFundedAssetsReviewed(WALLET, many)

        assertEquals(
            PortfolioPresentationPreferences.MAX_REMEMBERED_ASSETS,
            preferences.observeReviewedHiddenFundedAssets(WALLET).first().size
        )
    }

    @Test
    fun `legacy hidden assets are recorded once per wallet and never rewritten`() = runTest {
        var reads = 0
        preferences.rememberLegacyHiddenAssets(WALLET) { reads++; setOf("chain:legacy") }
        preferences.rememberLegacyHiddenAssets(WALLET) { reads++; setOf("chain:legacy", "chain:hidden-in-this-version") }

        assertEquals(1, reads)
        assertEquals(setOf("chain:legacy"), preferences.observeLegacyHiddenAssets(WALLET).first())
        assertEquals(emptySet<String>(), preferences.observeLegacyHiddenAssets(OTHER_WALLET).first())
    }

    @Test
    fun `observing the legacy snapshot before it exists does not stop it from being recorded`() = runTest {
        // The production store creates an empty entry for a set that is observed before it is written.
        store.putStringSet(PortfolioPresentationPreferences.legacyHiddenKey(WALLET), emptySet())

        preferences.rememberLegacyHiddenAssets(WALLET) { setOf("chain:legacy") }

        assertEquals(setOf("chain:legacy"), preferences.observeLegacyHiddenAssets(WALLET).first())
    }

    @Test
    fun `a wallet without hidden assets records an empty snapshot, so later hides are never called legacy`() = runTest {
        preferences.rememberLegacyHiddenAssets(WALLET) { emptySet() }
        preferences.rememberLegacyHiddenAssets(WALLET) { setOf("chain:hidden-in-this-version") }

        assertEquals(emptySet<String>(), preferences.observeLegacyHiddenAssets(WALLET).first())
    }

    @Test
    fun `asset keys are the database identity of a row`() {
        assertEquals("1:0xabc", PortfolioPresentationPreferences.assetKey("1", "0xabc"))
    }

    @Test fun `lens defaults to token and persists per wallet with invalid fallback`() = runTest {
        assertEquals(PortfolioLens.ByToken, preferences.observeLens(WALLET).first())
        preferences.setLens(WALLET, PortfolioLens.ByNetwork)
        assertEquals(PortfolioLens.ByNetwork, PortfolioPresentationPreferences(store).observeLens(WALLET).first())
        assertEquals(PortfolioLens.ByToken, preferences.observeLens(OTHER_WALLET).first())
        assertEquals(PortfolioLens.ByNetwork, preferences.observeUi(WALLET).first().lens)
        assertEquals(PortfolioLens.ByToken, preferences.observeUi(OTHER_WALLET).first().lens)
        store.putString(PortfolioPresentationPreferences.viewModeKey(WALLET), "bad")
        assertEquals(PortfolioLens.ByToken, preferences.observeLens(WALLET).first())
    }

    @Test fun `collapse and expansion are wallet scoped and survive reconstruction`() = runTest {
        preferences.toggleGroup(WALLET, "dot")
        preferences.toggleNetwork(WALLET, "polkadot")
        preferences.toggleZeroGroup(WALLET, "dot")
        preferences.setZeroSummaryExpanded(WALLET, true)
        val restored = PortfolioPresentationPreferences(store).observeUi(WALLET).first()
        assertEquals(setOf("dot"), restored.expandedGroups)
        assertEquals(setOf("polkadot"), restored.collapsedNetworks)
        assertEquals(setOf("dot"), restored.expandedZeroGroups)
        assertTrue(restored.zeroSummaryExpanded)
        assertEquals(PortfolioUiState(), preferences.observeUi(OTHER_WALLET).first())
    }

    @Test fun `presentation sets reject malformed oversized and typed values`() = runTest {
        for (raw in listOf("bad", "[1]", "[true]", "[\"\"]", "[" + (0..300).joinToString { "\"$it\"" } + "]", "x".repeat(100000))) {
            store.putString(PortfolioPresentationPreferences.expandedGroupsKey(WALLET), raw)
            assertTrue(preferences.observeUi(WALLET).first().expandedGroups.isEmpty())
        }
        repeat(300) { preferences.toggleGroup(WALLET, "group$it") }
        assertEquals(PortfolioPresentationPreferences.MAX_PRESENTATION_ITEMS, preferences.observeUi(WALLET).first().expandedGroups.size)
    }

    @Test fun `hidden funded notice dismissal is exact set bound and wallet scoped`() = runTest {
        val old = setOf("a:1", "b:2")
        preferences.dismissHiddenFundedNotice(WALLET, old)
        val saved = preferences.observeHiddenFundedDismissal(WALLET).first()
        assertEquals(0, preferences.hiddenFundedNoticeCount(old.reversed().toSet(), emptySet(), saved))
        assertEquals(3, preferences.hiddenFundedNoticeCount(old + "c:3", emptySet(), saved))
        assertEquals(1, preferences.hiddenFundedNoticeCount(old - "b:2", emptySet(), saved))
        assertEquals(0, preferences.hiddenFundedNoticeCount(old, old, null))
        assertEquals(null, preferences.observeHiddenFundedDismissal(OTHER_WALLET).first())
    }

    private companion object {
        const val WALLET = 7L
        const val OTHER_WALLET = 8L
    }
}
