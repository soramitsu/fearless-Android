package jp.co.soramitsu.app.root.navigation

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.account.impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.account.impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.app.R
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.send.setup.SendSetupFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NavigatorRoutesTest {
    private val root = mockk<NavController>(relaxed = true)
    private val host = mockk<AppCompatActivity>(relaxed = true)
    private val navigator = Navigator(mockk(relaxed = true)).apply { attach(root, host) }

    @After
    fun releaseNavigation() {
        navigator.detach()
    }

    @Test
    fun stakingRoutesOpenTheirDistinctDestinations() {
        val routes: List<Pair<Int, () -> Unit>> = listOf(
            R.id.setupStakingFragment to navigator::openSetupStaking,
            R.id.startChangeValidatorsFragment to navigator::openStartChangeValidators,
            R.id.startChangeCollatorsFragment to navigator::openStartChangeCollators,
            R.id.payoutsListFragment to navigator::openPayouts,
            R.id.createPoolSetupFragment to navigator::openCreatePoolSetup,
            R.id.confirmCreatePoolFragment to navigator::openCreatePoolConfirm,
            R.id.customRebondFragment to navigator::openCustomRebond,
            R.id.currentValidatorsFragment to navigator::openCurrentValidators,
            R.id.selectRewardDestinationFragment to navigator::openChangeRewardDestination,
            R.id.startStakingPoolFragment to navigator::openStakingPoolWelcome,
            R.id.setupStakingPoolFragment to navigator::openSetupStakingPool,
            R.id.poolJoinRecoveryFragment to navigator::openPoolJoinRecovery,
            R.id.confirmJoinPoolFragment to navigator::openConfirmJoinPool,
            R.id.managePoolStakeFragment to navigator::openManagePoolStake,
            R.id.poolBondMoreFragment to navigator::openPoolBondMore,
            R.id.poolClaimFragment to navigator::openPoolClaim,
            R.id.poolRedeemFragment to navigator::openPoolRedeem,
            R.id.poolUnstakeFragment to navigator::openPoolUnstake,
            R.id.poolConfirmBondMoreFragment to navigator::openPoolConfirmBondMore,
            R.id.poolConfirmClaimFragment to navigator::openPoolConfirmClaim,
            R.id.poolConfirmRedeemFragment to navigator::openPoolConfirmRedeem,
            R.id.poolConfirmUnstakeFragment to navigator::openPoolConfirmUnstake
        )
        routes.forEach { (destination, action) ->
            clearMocks(root, answers = false)
            action()
            verify(exactly = 1) { root.navigate(destination, null, null) }
        }
    }

    @Test
    fun validatorRoutesKeepTheirSelectionAndConfirmationStepsSeparate() {
        val routes: List<Pair<Int, () -> Unit>> = listOf(
            R.id.setControllerAccountFragment to navigator::openControllerAccount,
            R.id.recommendedCollatorsFragment to navigator::openRecommendedCollators,
            R.id.selectCustomCollatorsFragment to navigator::openSelectCustomCollators,
            R.id.selectPoolFramgent to navigator::openSelectPool,
            R.id.searchCustomValidatorsFragment to navigator::openSearchCustomValidators,
            R.id.searchCustomValidatorsFragment to navigator::openSearchCustomCollators,
            R.id.reviewCustomValidatorsFragment to navigator::openReviewCustomValidators,
            R.id.confirmStakingFragment to navigator::openConfirmStaking,
            R.id.confirmNominationsFragment to navigator::openConfirmNominations,
            R.id.selectedValidatorsFragment to navigator::openSelectedValidators,
            R.id.startSelectValidatorsFragment to navigator::openStartSelectValidators,
            R.id.selectValidatorsFragment to navigator::openSelectValidators,
            R.id.validatorsSettingsFragment to navigator::openValidatorsSettings,
            R.id.confirmSelectValidatorsFragment to navigator::openConfirmSelectValidators,
            R.id.editPoolFragment to navigator::openEditPool,
            R.id.editPoolConfirmFragment to navigator::openEditPoolConfirm
        )
        routes.forEach { (destination, action) ->
            clearMocks(root, answers = false)
            action()
            verify(exactly = 1) { root.navigate(destination, null, null) }
        }
    }

    @Test
    fun walletAndSettingsRoutesOpenTheExpectedProductScreen() {
        val routes: List<Pair<Int, () -> Unit>> = listOf(
            R.id.aboutFragment to navigator::openAboutScreen,
            R.id.languagesFragment to navigator::openLanguages,
            R.id.appearanceFragment to navigator::openAppearance,
            R.id.selectWalletFragment to navigator::openSelectWallet,
            R.id.searchAssetsFragment to navigator::openSearchAssets,
            R.id.recoveryCenterFragment to navigator::openRecoveryCenter,
            R.id.assetDetailSortFragment to navigator::openAssetIntermediateDetailsSort,
            R.id.nftFiltersFragment to navigator::openNFTFilter,
            R.id.connectionsFragment to navigator::openConnectionsScreen,
            R.id.tonConnectionsFragment to navigator::openTonConnectionsScreen,
            R.id.selectMarketFragment to navigator::openSelectMarketDialog
        )
        routes.forEach { (destination, action) ->
            clearMocks(root, answers = false)
            action()
            verify(exactly = 1) { root.navigate(destination, null, null) }
        }
    }

    @Test
    fun sendRoutesPreserveTheAssetAddressAmountAndInputLock() {
        val asset = AssetPayload("chain", "asset")
        listOf(false, true).forEach { locked ->
            clearMocks(root, answers = false)
            if (locked) navigator.openLockedAmountSend(asset, "recipient", "currency", BigDecimal.TEN)
            else navigator.openSend(asset, "recipient", "currency", BigDecimal.TEN)
            val args = slot<Bundle>()
            verify { root.navigate(R.id.sendSetupFragment, capture(args), null) }
            assertSame(asset, args.captured.getParcelable(SendSetupFragment.KEY_PAYLOAD))
            assertEquals("recipient", args.captured.getString(SendSetupFragment.KEY_INITIAL_ADDRESS))
            assertEquals("currency", args.captured.getString(SendSetupFragment.KEY_TOKEN_ID))
            assertEquals(BigDecimal.TEN, args.captured.getSerializable(SendSetupFragment.KEY_INITIAL_AMOUNT))
            assertEquals(locked, args.captured.getBoolean(SendSetupFragment.KEY_LOCK_AMOUNT))
        }
    }

    @Test
    fun manageAssetsPreservesAnExplicitFilterAndDefaultsToAll() {
        navigator.openManageAssets()
        verify { root.navigate(R.id.manageAssetsFragment, match { it.getString("manage_asset_filter") == "All" }, null) }
        navigator.openManageAssets("Hidden")
        verify { root.navigate(R.id.manageAssetsFragment, match { it.getString("manage_asset_filter") == "Hidden" }, null) }
    }

    @Test
    fun deepLinksCarryTheRequestedChainAndAccountType() {
        navigator.openManageControllerAccount("chain-42")
        navigator.openImportAccountScreenFromWallet(2)
        verify { root.navigate(match<NavDeepLinkRequest> { it.uri.toString() == "fearless://staking/setControllerAccountFragment/chain-42" }) }
        verify { root.navigate(match<NavDeepLinkRequest> { it.uri.toString() == "fearless://onboarding/importAccountFragment/2" }) }
    }

    @Test
    fun sensitiveExportsRequireAnExplicitPinAndRetainTheirDestination() {
        val delayed = NavComponentDelayedNavigation(R.id.exportSeedFragment)
        navigator.withPinCodeCheckRequired(delayed, false, R.string.common_continue)
        val args = slot<Bundle>()
        verify { root.navigate(R.id.open_pincode_check, capture(args)) }
        val action = args.captured.getParcelable<PinCodeAction.Check>(PincodeFragment.KEY_PINCODE_ACTION)!!
        assertSame(delayed, action.delayedNavigation)
        assertFalse(action.allowBiometric)
        assertTrue(action.toolbarConfiguration.backVisible)
        assertEquals(R.string.common_continue, action.toolbarConfiguration.titleRes)
    }

    @Test
    fun pinCreationAndInitialUnlockReturnToTheMainGraph() {
        navigator.openCreatePincode()
        val created = slot<Bundle>()
        verify { root.navigate(R.id.pincodeFragment, capture(created)) }
        val create = created.captured.getParcelable<PinCodeAction.Create>(PincodeFragment.KEY_PINCODE_ACTION)!!
        assertEquals(R.id.action_open_main, (create.delayedNavigation as NavComponentDelayedNavigation).globalActionId)
        clearMocks(root, answers = false)
        navigator.openInitialCheckPincode()
        val checked = slot<Bundle>()
        verify { root.navigate(R.id.pincodeFragment, capture(checked)) }
        val check = checked.captured.getParcelable<PinCodeAction.Check>(PincodeFragment.KEY_PINCODE_ACTION)!!
        assertTrue(check.allowBiometric)
        assertEquals(R.id.action_open_main, (check.delayedNavigation as NavComponentDelayedNavigation).globalActionId)
    }

    @Test
    fun completingPinRemovesTheAppropriateBackStackBoundary() {
        listOf(R.id.action_open_main to R.id.root_nav_graph, R.id.exportSeedFragment to R.id.pincodeFragment).forEach { (destination, popTo) ->
            val extras = Bundle().apply { putString("request", "retained") }
            navigator.openAfterPinCode(NavComponentDelayedNavigation(destination, extras))
            val options = slot<NavOptions>()
            verify { root.navigate(destination, extras, capture(options)) }
            assertEquals(popTo, options.captured.popUpToId)
            assertEquals(destination != R.id.action_open_main, options.captured.isPopUpToInclusive())
        }
    }

    @Test
    fun backFinishesOnlyWhenTheActiveStackCannotPop() {
        every { root.popBackStack() } returns true
        navigator.back()
        verify(exactly = 0) { host.finish() }
        every { root.popBackStack() } returns false
        navigator.back()
        verify(exactly = 1) { host.finish() }
    }

    @Test
    fun resultsAreWrittenToThePreviousOrExplicitEntryBeforeBack() {
        val previous = SavedStateHandle()
        val explicit = SavedStateHandle()
        every { root.previousBackStackEntry } returns mockk<NavBackStackEntry> {
            every { savedStateHandle } returns previous
        }
        every { root.getBackStackEntry(R.id.walletFragment) } returns mockk<NavBackStackEntry> {
            every { savedStateHandle } returns explicit
        }
        every { root.popBackStack() } returns true
        navigator.backWithResult("selection" to "XOR", "amount" to 4)
        navigator.backWithResult(R.id.walletFragment, "selection" to "DOT")
        assertEquals("XOR", previous.get<String>("selection"))
        assertEquals(4, previous.get<Int>("amount"))
        assertEquals("DOT", explicit.get<String>("selection"))
        verify(exactly = 2) { root.popBackStack() }
    }

    @Test
    fun tabBackActionsUseTheActiveShellAndDetachedTabsAreIgnored() {
        val tabs = mockk<NavController>(relaxed = true)
        every { root.currentDestination } returns mockk<NavDestination> { every { id } returns R.id.mainFragment }
        navigator.attachMainTabs(tabs)
        navigator.returnToStakingBalance()
        navigator.returnToManagePoolStake()
        navigator.returnToCurrentValidators()
        navigator.returnToMain()
        navigator.popOutOfSend()
        navigator.closeSwap()
        verify { tabs.popBackStack(R.id.stakingBalanceFragment, false) }
        verify { tabs.popBackStack(R.id.managePoolStakeFragment, false) }
        verify { tabs.popBackStack(R.id.currentValidatorsFragment, false) }
        verify { tabs.popBackStack(R.id.defiHubFragment, false) }
        verify { tabs.popBackStack(R.id.sendSetupFragment, true) }
        verify { tabs.popBackStack(R.id.polkaswapHubFragment, false) }
        navigator.detachMainTabs(tabs)
        navigator.closeSwap()
        verify { root.navigate(R.id.close_swap) }
    }
}
