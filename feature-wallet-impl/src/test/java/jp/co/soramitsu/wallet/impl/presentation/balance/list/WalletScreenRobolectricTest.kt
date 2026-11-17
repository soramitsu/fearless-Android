package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.domain.model.NetworkIssueType
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroup
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroupMember
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.assetRow
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h4000dp")
class WalletScreenRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val callback = RecordingWalletCallback()

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
    }

    @After
    fun tearDown() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun flatListShowsBackupBannerAssetsAndFooterActions() {
        val state = baseState(
            assetsState = WalletAssetsState.Assets(
                AssetsLoadingState.Loaded(
                    listOf(
                        assetRow(symbol = "dot", networkFiatSubtotal = "$75.00"),
                        assetRow(index = 1, symbol = "ksm", chainName = "Kusama", chainId = "kusama", chainAssetId = "ksm"),
                        assetRow(index = 2, symbol = "det", chainId = "kusama", chainAssetId = "det", metadataTrust = "detected")
                    ),
                    exclusions = PortfolioExclusions(hiddenAssetCount = 2, hiddenFiat = "$3", unpricedFundedCount = 1, poolStakesExcluded = true)
                ),
                isHideVisible = true
            ),
            isBackedUp = false
        )
        render(state)

        compose.onNodeWithText(string(R.string.ux_backup_description)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.ux_backup_status)).performClick()
        compose.onNodeWithText("DOT").assertIsDisplayed()
        compose.onNodeWithText("KSM").assertIsDisplayed()
        assertEquals(4, compose.onAllNodes(hasText("$75.00")).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.portfolio_detected_assets, 1)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_pool_stakes_not_included)).assertIsDisplayed()
        compose.onNodeWithTag("portfolio_unpriced_not_included").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_hidden_manage, 2, "$3")).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.wallet_manage_assets)).performClick()
        compose.waitForIdle()

        assertTrue(callback.calls.contains("backup"))
        assertTrue(callback.calls.contains("hiddenReview"))
        assertTrue(callback.calls.contains("manageAssets"))
    }

    @Test
    fun collapsingANetworkHidesItsAssets() {
        render(
            baseState(
                assetsState = WalletAssetsState.Assets(
                    AssetsLoadingState.Loaded(listOf(assetRow(symbol = "dot", chainName = "Relay"))),
                    isHideVisible = false
                )
            )
        )

        compose.onNodeWithText("DOT").assertIsDisplayed()
        compose.onNodeWithText("Relay").performClick()
        compose.waitForIdle()

        assertEquals(0, compose.count("DOT"))
    }

    @Test
    fun emptyLoadedListExplainsThatEveryAssetIsHidden() {
        render(
            baseState(
                assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loaded(emptyList(), allAssetsHidden = true), isHideVisible = true)
            )
        )

        compose.onNodeWithText(string(R.string.wallet_all_assets_hidden)).assertIsDisplayed()
    }

    @Test
    fun loadingStateRendersTheShimmerList() {
        render(baseState(assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loading(), isHideVisible = true)))

        assertEquals(0, compose.count(string(R.string.wallet_manage_assets)))
        assertTrue(compose.count(string(R.string.portfolio_balance_loading)) > 0)
    }

    @Test
    fun networkIssueOffersARetry() {
        render(
            baseState(
                assetsState = WalletAssetsState.NetworkIssue("polkadot", NetworkIssueType.Node, retryButtonLoading = false),
                isRecoveryRequired = true
            )
        )

        compose.onNodeWithText(string(R.string.network_issue_main)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_try_again)).performClick()
        compose.waitForIdle()

        assertTrue(callback.calls.contains("retry"))
    }

    @Test
    fun portfolioRowsReportGroupAndZeroToggles() {
        render(baseState(assetsState = WalletAssetsState.Assets(portfolio(), isHideVisible = true)))

        compose.onNodeWithText(string(R.string.portfolio_value_notice)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_got_it)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithTag("portfolio_hidden_funds_notice").assertIsDisplayed()
        compose.onNodeWithTag("portfolio_hidden_funds_review").performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithTag("portfolio_hidden_funds_keep_hidden").performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.portfolio_show_zero_networks, 3)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.portfolio_group_networks, 2)).performClick()
        compose.waitForIdle()

        assertTrue(callback.calls.toString(), callback.calls.containsAll(listOf("valueNotice", "hiddenReview", "keepHidden", "zero:null", "group:usdt")))
        // One list: the By token / By network switch is gone, the search and the network chips replace it.
        assertEquals(0, compose.count(string(R.string.portfolio_by_network)))
        compose.onNodeWithTag(SEARCH_TAG).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_price_incomplete)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_group_hidden, 1)).assertIsDisplayed()
    }

    @Test
    fun groupSendWithOneFundedMemberOpensItDirectly() {
        render(baseState(assetsState = WalletAssetsState.Assets(portfolio(), isHideVisible = true)))

        groupAction(R.string.common_action_send).performClick()
        compose.waitForIdle()

        assertTrue(callback.calls.toString(), callback.calls.contains("member:7:moonbeam:usdt-m:SEND"))
    }

    @Test
    fun groupReceiveLetsTheUserChooseTheNetwork() {
        render(baseState(assetsState = WalletAssetsState.Assets(portfolio(), isHideVisible = true)))

        groupAction(R.string.common_action_receive).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.portfolio_choose_network)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_balance_unavailable)).assertIsDisplayed()
        compose.onNodeWithText("Account 1").assertIsDisplayed()
        compose.onNode(hasText("Polkadot Asset Hub") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertTrue(callback.calls.toString(), callback.calls.contains("member:7:assethub:usdt-a:RECEIVE"))
    }

    @Test
    fun groupHideAsksForConfirmation() {
        render(baseState(assetsState = WalletAssetsState.Assets(portfolio(), isHideVisible = true)))

        groupAction(R.string.common_action_hide).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.portfolio_hide_group_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_hide_group_message)).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.common_action_hide)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertTrue(callback.calls.toString(), callback.calls.contains("hideGroup:7:usdt"))
    }

    @Test
    fun groupDialogCanBeCancelled() {
        render(baseState(assetsState = WalletAssetsState.Assets(portfolio(), isHideVisible = true)))

        groupAction(R.string.common_action_receive).performClick()
        compose.onNodeWithText(string(R.string.common_cancel)).performClick()
        compose.waitForIdle()

        assertEquals(0, compose.count(string(R.string.portfolio_choose_network)))
    }

    @Test
    fun legacyPaletteShowsThePlainHeaderAndGroupCard() {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(any()) } returns false

        render(baseState(assetsState = WalletAssetsState.Assets(portfolio(), isHideVisible = true), showSelector = true))

        compose.onNodeWithText("USDT").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.portfolio_group_networks, 2)).assertIsDisplayed()
        groupAction(R.string.common_action_receive).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.portfolio_choose_network)).assertIsDisplayed()
    }

    @Test
    fun legacyPaletteNetworkIssueAndBanners() {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(any()) } returns false

        render(
            baseState(
                assetsState = WalletAssetsState.NetworkIssue("polkadot", NetworkIssueType.Node, retryButtonLoading = false),
                isBackedUp = false
            )
        )

        compose.onNodeWithText(string(R.string.common_try_again)).performClick()
        compose.waitForIdle()
        assertTrue(callback.calls.contains("retry"))
    }

    @Test
    fun scrollEventsAreConsumedOnce() {
        val top = Event(Unit)
        val bottom = Event(Unit)
        render(
            baseState(
                assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loaded(listOf(assetRow())), isHideVisible = true)
            ).copy(scrollToTopEvent = top, scrollToBottomEvent = bottom)
        )
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()

        assertTrue(top.hasBeenHandled)
        assertTrue(bottom.hasBeenHandled)
    }

    @Test
    fun walletSyncStateFollowsAssetsAndCounts() {
        val loading = baseState(assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loading(), true))
        val issue = baseState(assetsState = WalletAssetsState.NetworkIssue("x", NetworkIssueType.Node, false))
        val loaded = baseState(assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loaded(emptyList()), true))

        assertEquals("Running", walletSyncState(loading).phase.name)
        assertEquals("Failed", walletSyncState(issue).phase.name)
        assertEquals("Failed", walletSyncState(loaded.copy(hasNetworkIssues = true)).phase.name)
        assertEquals("Running", walletSyncState(loaded.copy(syncCounts = WalletSyncCounts(1, 0, 3))).phase.name)
        assertEquals("Done", walletSyncState(loaded.copy(syncCounts = WalletSyncCounts(2, 1, 3))).phase.name)
        assertEquals("short", heroAddressLabel("short"))
        assertEquals("abcdef…uvwxyz", heroAddressLabel("abcdefghijklmnopqrstuvwxyz"))
    }

    private fun groupAction(label: Int) =
        compose.onNode(hasText(string(label)) and hasAnyAncestor(hasTestTag("group:usdt")))

    private fun render(state: WalletState) {
        compose.setContent {
            FearlessAppTheme {
                WalletScreenWithRefresh(data = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }

    private fun portfolio(): AssetsLoadingState.Loaded {
        val moonbeam = PortfolioAsset(
            assetRow(symbol = "usdt", chainName = "Moonbeam", chainId = "moonbeam", chainAssetId = "usdt-m"),
            total = BigDecimal("10"),
            trustedFiat = BigDecimal("10"),
            fiatSymbol = "$"
        )
        val assetHub = PortfolioAsset(
            assetRow(index = 1, symbol = "usdt", chainName = "Polkadot Asset Hub", chainId = "assethub", chainAssetId = "usdt-a", networkAccountLabel = "Account 1"),
            total = null,
            trustedFiat = null,
            fiatSymbol = "$"
        )
        val loose = PortfolioAsset(assetRow(index = 2, symbol = "glmr", chainName = "Moonbeam", chainId = "moonbeam", chainAssetId = "glmr"), BigDecimal.ONE, null, "$")
        val groups = ResolvedTokenGroups(
            listOf(
                ResolvedTokenGroup(
                    id = "usdt", symbol = "USDT", name = "Tether", precision = 6,
                    members = listOf(ResolvedTokenGroupMember("moonbeam", "usdt-m"), ResolvedTokenGroupMember("assethub", "usdt-a"))
                )
            )
        )
        return AssetsLoadingState.Loaded(
            assets = listOf(moonbeam.state, assetHub.state, loose.state),
            exclusions = PortfolioExclusions(hiddenFundedAssetKeys = setOf("k")),
            hiddenFundedNoticeCount = 2,
            walletId = 7L,
            portfolioAssets = listOf(moonbeam, assetHub, loose),
            availableAssets = listOf(moonbeam, assetHub, loose),
            portfolioItems = listOf(
                PortfolioListItem.TokenGroup(
                    id = "usdt", symbol = "USDT", name = "Tether", members = listOf(moonbeam, assetHub),
                    total = BigDecimal("10"), fiat = BigDecimal("10"), priceIncomplete = true, hiddenCount = 1, expanded = false
                ),
                PortfolioListItem.AssetRow(loose),
                PortfolioListItem.ZeroSummary(networkCount = 3, expanded = false)
            ),
            portfolioUi = PortfolioUiState(),
            tokenGroups = groups
        )
    }

    private fun baseState(
        assetsState: WalletAssetsState,
        isBackedUp: Boolean = true,
        isRecoveryRequired: Boolean = false,
        showSelector: Boolean = false
    ) = WalletState.default.copy(
        assetsState = assetsState,
        balance = AssetBalanceViewState("$85.00", "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY", true, ChangeBalanceViewState("+1%", "+$1")),
        isBackedUp = isBackedUp,
        isRecoveryRequired = isRecoveryRequired,
        showCurrenciesOrNftSelector = showSelector
    )

    private fun ComposeContentTestRule.count(text: String): Int =
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size
}

@OptIn(ExperimentalMaterialApi::class)
internal class RecordingWalletCallback : WalletScreenInterface {
    val calls = mutableListOf<String>()

    override fun onAddressClick() { calls += "address" }
    override fun onBalanceClicked() { calls += "balance" }
    override fun onBackupClicked() { calls += "backup" }
    override fun onBackupCloseClick() { calls += "backupClose" }
    override fun onJoinSubOrEvmClicked() { calls += "joinSub" }
    override fun onJoinSubOrEvmCloseClick() { calls += "joinSubClose" }
    override fun onJoinTonClicked() { calls += "joinTon" }
    override fun onJoinTonCloseClick() { calls += "joinTonClose" }
    override fun assetTypeChanged(type: AssetType) { calls += "type:$type" }
    override fun onRefresh() { calls += "refresh" }
    override fun onManageAssetClick() { calls += "manageAssets" }
    override fun onHiddenFundsReviewClick() { calls += "hiddenReview" }
    override fun onHiddenFundsKeepHiddenClick() { calls += "keepHidden" }
    override fun onRetry() { calls += "retry" }
    override fun onWalletRecoveryClick() { calls += "recovery" }
    override fun onPortfolioLayoutNoticeDismiss() { calls += "oneListNotice" }
    override fun onPortfolioSearch(query: String) { calls += "search:$query" }
    override fun onPortfolioNetworkSelect(chainId: String?) { calls += "networkSelect:$chainId" }
    override fun onPortfolioMoreNetworks() { calls += "moreNetworks" }
    override fun onPortfolioGroupToggle(id: String) { calls += "group:$id" }
    override fun onPortfolioZeroToggle(id: String?) { calls += "zero:$id" }
    override fun onPortfolioValueNoticeDismiss() { calls += "valueNotice" }
    override fun onPortfolioMemberAction(walletId: Long, chainId: String, assetId: String, action: ActionItemType) {
        calls += "member:$walletId:$chainId:$assetId:$action"
    }
    override fun onPortfolioHideGroup(walletId: Long, groupId: String) { calls += "hideGroup:$walletId:$groupId" }
    override fun onHeroAction(action: ActionItemType) { calls += "hero:$action" }
    override fun assetClicked(state: AssetListItemViewState) { calls += "asset:${state.chainAssetId}" }
    override fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) {
        calls += "action:$actionType:$chainAssetId"
    }
}
