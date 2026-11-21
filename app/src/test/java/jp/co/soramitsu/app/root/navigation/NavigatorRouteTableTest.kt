package jp.co.soramitsu.app.root.navigation

import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavAction
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.SavedEndpointAccess
import jp.co.soramitsu.account.impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.account.impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.HiltShimHostActivity
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.presentation.emergency.WalletEmergencyExportActivity
import jp.co.soramitsu.app.root.presentation.keyloss.WalletKeyLossRecoveryActivity
import jp.co.soramitsu.app.root.presentation.stories.StoryFragment
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragmentPayload
import jp.co.soramitsu.polkaswap.impl.presentation.disclaimer.PolkaswapDisclaimerFragment
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.api.presentation.AssetSelectPurpose
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Every product route opens its own destination; flows read results from the entries the routes write to. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class NavigatorRouteTableTest {
    private val root: NavController = mock(NavController::class.java)
    private val host = HiltShim.host()
    private val navigator = Navigator(mockk<SavedEndpointAccess>(relaxed = true)).apply { attach(root, host) }
    private val asset = AssetPayload("polkadot", "dot")
    private val alert = AlertViewState("Title", "Message", "OK", iconRes = 0)
    private val jobs = mutableListOf<Job>()

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
        navigator.detach(host)
        WalletSessionLock.unlock()
        HiltShim.closeHosts()
    }

    @Test
    fun onboardingAndAccountCreationRoutesUseTheRootGraph() {
        assertRoutes(
            R.id.action_to_onboarding to { navigator.openOnboarding() },
            R.id.action_welcomeFragment_to_createAccountFragment to { navigator.openCreateAccountFromOnboarding(AccountType.entries.first()) },
            R.id.createAccountDialog to { navigator.openCreateWalletDialogFromGoogleBackup() },
            R.id.action_to_onboardingNavGraph to { navigator.openCreateAccountFromWallet() },
            R.id.action_to_onboardingNavGraph to { navigator.openOnboardingNavGraph("polkadot", 3, true) },
            R.id.action_to_onboardingNavGraph to { navigator.openCreateSubstrateOrEvmAccountScreen() },
            R.id.action_to_onboardingNavGraph to { navigator.openCreateTonAccountScreen() },
            R.id.importRemoteWalletDialog to { navigator.openImportRemoteWalletDialog() },
            R.id.mnemonicAgreementsDialog to { navigator.openMnemonicAgreementsDialogForGoogleBackup("Main", listOf(WalletEcosystem.Substrate)) },
            R.id.action_open_main to { navigator.openMain() },
            R.id.confirmExportMnemonicFragment to { navigator.openConfirmMnemonicOnCreate(mockk(relaxed = true)) },
            R.id.importAccountFragment to { navigator.openImportAddAccountScreen(3, WalletEcosystem.Ethereum, ImportMode.RawSeed) },
            R.id.importAccountFragment to { navigator.openImportAccountScreen(WalletEcosystem.Ton, ImportMode.MnemonicPhrase) },
            R.id.backupMnemonicFragment to { navigator.openMnemonicScreenAddAccount(3, "Main", WalletEcosystem.Substrate) },
            R.id.backupMnemonicFragment to { navigator.openMnemonicScreen("Main", listOf(WalletEcosystem.Substrate, WalletEcosystem.Ton)) },
            R.id.backupMnemonicDialog to { navigator.openMnemonicDialogGoogleBackup("Main", listOf(WalletEcosystem.Ethereum)) }
        )
        val onboarding = route { navigator.openCreateTonAccountScreen() }.single().second
            ?.getParcelable<WelcomeFragmentPayload>(WelcomeFragment.KEY_PAYLOAD)
        assertEquals("WelcomeScreen?accountType=Ton", onboarding?.route)
        assertTrue(onboarding!!.displayBack)
        val chainAccount = route { navigator.openOnboardingNavGraph("polkadot", 3, true) }.single().second
            ?.getParcelable<WelcomeFragmentPayload>(WelcomeFragment.KEY_PAYLOAD)?.createChainAccount
        assertEquals("polkadot", chainAccount?.chainId)
    }

    @Test
    fun backStepsInTheAccountFlowsPopTheRootStack() {
        `when`(root.popBackStack()).thenReturn(true)
        clearInvocations(root)
        listOf(
            navigator::backToWelcomeScreen,
            navigator::backToCreateAccountScreen,
            navigator::backToBackupMnemonicScreen,
            navigator::backToProfileScreen,
            navigator::finishSendFlow
        ).forEach { it() }
        assertEquals(6, mockingDetails(root).invocations.count { it.method.name == "popBackStack" && it.arguments.isEmpty() })
    }

    @Test
    fun stakingRoutesCarryTheirPayloads() {
        assertRoutes(
            R.id.stakingStoryFragment to { navigator.openStory(StoryGroupModel(emptyList())) },
            R.id.payoutDetailsFragment to { navigator.openPayoutDetails(mockk(relaxed = true)) },
            R.id.confirmPayoutFragment to { navigator.openConfirmPayout(mockk(relaxed = true)) },
            R.id.stakingBalanceFragment to { navigator.openStakingBalance("collator") },
            R.id.stakingBalanceFragment to { navigator.openStakingBalance(null) },
            R.id.selectBondMoreFragment to { navigator.openBondMore(mockk(relaxed = true)) },
            R.id.confirmBondMoreFragment to { navigator.openConfirmBondMore(mockk(relaxed = true)) },
            R.id.walletSelectorFragment to { navigator.openWalletSelector("payout") },
            R.id.selectUnbondFragment to { navigator.openSelectUnbond(mockk(relaxed = true)) },
            R.id.confirmUnbondFragment to { navigator.openConfirmUnbond(mockk(relaxed = true)) },
            R.id.redeemFragment to { navigator.openRedeem(mockk(relaxed = true)) },
            R.id.confirmRebondFragment to { navigator.openConfirmRebond(mockk(relaxed = true)) },
            R.id.confirmRewardDestinationFragment to { navigator.openConfirmRewardDestination(mockk(relaxed = true)) },
            R.id.poolInfoFragment to { navigator.openPoolInfo(12) },
            R.id.confirmSetControllerAccount to { navigator.openConfirmSetController(mockk(relaxed = true)) },
            R.id.recommendedValidatorsFragment to { navigator.openRecommendedValidators() },
            R.id.selectCustomValidatorsFragment to { navigator.openSelectCustomValidators() },
            R.id.settingsCustomValidatorsFragment to { navigator.openCustomValidatorsSettingsFromValidator() },
            R.id.settingsCustomValidatorsFragment to { navigator.openCustomValidatorsSettingsFromCollator() },
            R.id.validatorDetailsFragment to { navigator.openValidatorDetails("0xabc") },
            R.id.collatorDetailsFragment to { navigator.openCollatorDetails(mockk(relaxed = true)) },
            R.id.poolOptionsInfoFragment to { navigator.openPoolInfoOptions(mockk(relaxed = true)) },
            R.id.poolFullUnstakeDepositorAlertFragment to { navigator.openPoolFullUnstakeDepositorAlertFragment("10 DOT") },
            R.id.claimRewardsFragment to { navigator.openClaimRewards("polkadot") },
            R.id.legacyCrowdloanFragment to { navigator.openLegacyCrowdloan(asset) },
            R.id.poolConfirmUnstakeFragment to { navigator.openPoolConfirmUnstake() },
            R.id.editPoolConfirmFragment to { navigator.openEditPoolConfirm() }
        )
        val pool = route { navigator.openPoolInfo(12) }.single().second
        assertTrue(pool!!.keySet().any { pool.get(it) == 12 })
    }

    @Test
    fun walletRoutesOpenTheirScreensWithTheSelectedAsset() {
        assertRoutes(
            R.id.transactionSettingsFragment to { navigator.openTransactionSettingsDialog(mockk(relaxed = true)) },
            R.id.swapPreviewFragment to { navigator.openSwapPreviewDialog(mockk(relaxed = true), mockk(relaxed = true)) },
            R.id.swapTokensFragment to { navigator.openSwapTokensScreen("sora", "xor", "val") },
            R.id.polkaswapDisclaimerFragment to { navigator.openPolkaswapDisclaimerFromSwapTokensFragment() },
            R.id.polkaswapDisclaimerFragment to { navigator.openPolkaswapDisclaimerFromProfile() },
            R.id.polkaswapDisclaimerFragment to { navigator.openPolkaswapDisclaimerFromMainScreen() },
            R.id.polkaswapDisclaimerFragment to { navigator.openRiskDisclaimer() },
            R.id.chainSelectFragment to {
                navigator.openSelectChain(assetId = "dot", chainId = "polkadot", chooserMode = true, isSelectAsset = false, showAllChains = true)
            },
            R.id.chainSelectFragment to {
                navigator.openSelectChain(
                    selectedChainId = "polkadot", filterChainIds = listOf("kusama"), chooserMode = false, currencyId = "dot",
                    showAllChains = false, isSelectAsset = true, isFilteringEnabled = true
                )
            },
            R.id.chainSelectFragment to { navigator.openSelectChainForXcm("polkadot", XcmChainType.entries.first(), "kusama", "ksm", "KSM") },
            R.id.connectionInfoFragment to { navigator.openConnectionDetails("topic") },
            R.id.assetSelectFragment to { navigator.openSelectAssetForReceive() },
            R.id.assetSelectFragment to { navigator.openSelectAsset("dot", "polkadot") },
            R.id.assetSelectFragment to { navigator.openSelectAsset("polkadot", "dot", true) },
            R.id.assetSelectFragment to { navigator.openSelectAsset("polkadot", "dot", "usdt", AssetSelectPurpose.SEND) },
            R.id.assetSelectFragment to { navigator.openSelectChainAsset("polkadot") },
            R.id.transactionHistoryFilterFragment to { navigator.openFilter(TransactionFilter.entries.toSet()) },
            R.id.confirmSendFragment to { navigator.openSendConfirm(mockk(relaxed = true), null, emptyMap(), "memo", false) },
            R.id.confirmCrossChainSendFragment to { navigator.openCrossChainSendConfirm(mockk(relaxed = true), null) },
            R.id.successSheetFragment to { navigator.openOperationSuccess("0xhash", "polkadot") },
            R.id.successSheetFragment to { navigator.openOperationSuccess("0xhash", "polkadot", "Sent", "Done") },
            R.id.transferDetailFragment to { navigator.openTransferDetail(mockk(relaxed = true), asset, null) },
            R.id.rewardDetailFragment to { navigator.openRewardDetail(mockk(relaxed = true)) },
            R.id.extrinsicDetailFragment to { navigator.openExtrinsicDetail(mockk(relaxed = true)) },
            R.id.swapDetailFragment to { navigator.openSwapDetail(mockk(relaxed = true)) },
            R.id.nodesFragment to { navigator.openNodes("polkadot") },
            R.id.receiveFragment to { navigator.openReceive(asset) },
            R.id.balanceDetailFragment to { navigator.openAssetDetails(asset) },
            R.id.assetDetailFragment to { @Suppress("DEPRECATION") navigator.openAssetIntermediateDetails(asset) },
            R.id.addressHistoryFragment to { navigator.openAddressHistory("polkadot") },
            R.id.createContactFragment to { navigator.openCreateContact("polkadot", "5F...") },
            R.id.addNodeFragment to { navigator.openAddNode("polkadot") },
            R.id.frozenTokensFragment to { navigator.openFrozenTokens(mockk(relaxed = true)) },
            R.id.crossChainFragment to { navigator.openCrossChainSend(asset) },
            R.id.nftFlowFragment to { navigator.openNftCollection("polkadot", "0xcontract", "Collection") },
            R.id.poolsFlowFragment to { navigator.openPools() },
            R.id.demeterFarmingFragment to { navigator.openDemeterFarming() },
            R.id.polkamarktFragment to { navigator.openPolkamarkt("market-1") },
            R.id.serviceFragment to { navigator.openServiceScreen() },
            R.id.webViewerFragment to { navigator.openWebViewer("Terms", "https://fearlesswallet.io") },
            R.id.dappScreenFragment to { navigator.openDappScreen(mockk(relaxed = true)) },
            R.id.tonConnectionInfo to { navigator.openTonConnectionInfo(mockk(relaxed = true)) }
        )
    }

    @Test
    fun settingsRoutesOpenTheWalletAndAccountOptions() {
        assertRoutes(
            R.id.accountDetailsDialog to { navigator.openAccountDetails(3) },
            R.id.chainAccountsDialog to { navigator.openEcosystemAccountsFragment(3, WalletEcosystem.Substrate) },
            R.id.backupWalletDialog to { navigator.openBackupWalletScreen(3) },
            R.id.renameAccountDialog to { navigator.openRenameWallet(3, "Main") },
            R.id.nodeDetailsFragment to { navigator.openNodeDetails(mockk(relaxed = true)) },
            R.id.optionsAddAccountFragment to { navigator.openOptionsAddAccount(3, WalletEcosystem.Ton) },
            R.id.optionsEcosystemAccountsFragment to { navigator.openEcosystemAccountsOptions(3, WalletEcosystem.Ethereum) },
            R.id.optionsSwitchNodeFragment to { navigator.openOptionsSwitchNode(3, "polkadot", "Polkadot") },
            R.id.optionsWalletFragment to { navigator.openOptionsWallet(3, true) },
            R.id.pincodeFragment to { navigator.openChangePinCode() },
            R.id.alertFragment to { navigator.openAlert(alert, "key", R.id.walletFragment) },
            R.id.alertFragment to { navigator.openAlertFromStartSelectValidatorsScreen(alert, "key") },
            R.id.alertFragment to { navigator.openAlertFromStartChangeValidatorsScreen(alert, "key") }
        )
        val change = route { navigator.openChangePinCode() }.single().second
        assertSame(PinCodeAction.Change, change?.getParcelable<PinCodeAction>(PincodeFragment.KEY_PINCODE_ACTION))
    }

    @Test
    fun alertsWithoutAnExplicitTargetReturnToTheCurrentScreen() {
        val current = destination(R.id.walletFragment)
        `when`(root.currentDestination).thenReturn(current)
        assertRoutes(
            R.id.alertFragment to { navigator.openAlert(alert) },
            R.id.alertFragment to { navigator.openAlert(alert, "result") }
        )
    }

    @Test
    fun anOpenSwapScreenIsNotOpenedTwice() {
        val swap = destination(R.id.swapTokensFragment)
        `when`(root.currentDestination).thenReturn(swap)
        assertTrue(route { navigator.openSwapTokensScreen(null, null, null) }.isEmpty())
    }

    @Test
    fun assetDetailsFromTheBalancesListPopBackToTheMainScreen() {
        val (destination, _, options) = navigations { navigator.openAssetDetailsAndPopUpToBalancesList(asset) }.single()
        assertEquals(R.id.balanceDetailFragment, destination)
        assertEquals(R.id.mainFragment, options?.popUpToId)
        assertFalse(options!!.isPopUpToInclusive())
    }

    @Test
    fun exportsAndRecoveryGoThroughThePinCheck() {
        assertEquals(R.id.exportMnemonicFragment, (navigator.openExportMnemonic(3, "polkadot") as NavComponentDelayedNavigation).globalActionId)
        assertEquals(R.id.exportSeedFragment, (navigator.openExportSeed(3, "polkadot") as NavComponentDelayedNavigation).globalActionId)
        assertEquals(R.id.exportJsonPasswordFragment, (navigator.openExportJsonPassword(3, "polkadot") as NavComponentDelayedNavigation).globalActionId)
        assertTrue((navigator.getExportSeedDestination(3, "polkadot", true) as NavComponentDelayedNavigation).extras!!.size() > 0)

        assertRoutes(
            R.id.action_exportMnemonicFragment_to_confirmExportMnemonicFragment to { navigator.openConfirmMnemonicOnExport(listOf("a", "b"), 3) },
            R.id.action_exportJsonPasswordFragment_to_exportJsonConfirmFragment to { navigator.openExportJsonConfirm(mockk(relaxed = true)) },
            R.id.action_return_to_wallet to { navigator.finishExportFlow() },
            R.id.open_pincode_check to { navigator.openArchivedWalletRecovery() },
            R.id.open_pincode_check to { navigator.openRebind(RecoveryTarget(3, "substrate"), ImportMode.MnemonicPhrase) },
            R.id.pinRecoveryImportFragment to { navigator.openPinRecoveryImport(RecoveryTarget(3, "ton"), ImportMode.MnemonicPhrase, "challenge") }
        )
        val rebind = route { navigator.openRebind(RecoveryTarget(3, "ethereum"), ImportMode.RawSeed) }.single().second
        val check = rebind!!.getParcelable<PinCodeAction.Check>(PincodeFragment.KEY_PINCODE_ACTION)!!
        assertEquals(R.id.importAccountFragment, (check.delayedNavigation as NavComponentDelayedNavigation).globalActionId)
        assertEquals(R.string.wallet_recovery_restore, check.toolbarConfiguration.titleRes)
    }

    @Test
    fun creatingAPinWrapsTheDestinationInACreateAction() {
        val delayed = NavComponentDelayedNavigation(R.id.exportSeedFragment)
        val (destination, args) = route { navigator.withPinCodeCheckRequired(delayed, true, null) }.single()
        assertEquals(R.id.open_pincode_check, destination)
        assertSame(delayed, args!!.getParcelable<PinCodeAction.Create>(PincodeFragment.KEY_PINCODE_ACTION)!!.delayedNavigation)
    }

    @Test
    fun lockingRoutesLockTheSessionBeforeAskingForThePin() {
        WalletSessionLock.unlock()
        assertEquals(listOf(R.id.open_pincode_check), route { navigator.openPincodeCheck() }.map { it.first })
        assertTrue(WalletSessionLock.isLocked())
        WalletSessionLock.unlock()
        assertEquals(listOf(R.id.root_nav_graph), route { navigator.openNavGraph() }.map { it.first })
        assertTrue(WalletSessionLock.isLocked())
    }

    @Test
    fun returningToTheWalletWaitsForTheNextMainLoopTurn() {
        val pending = route { navigator.returnToWallet() }
        assertTrue(pending.isEmpty())
        assertEquals(listOf(R.id.action_return_to_wallet), route { shadowOf(Looper.getMainLooper()).idle() }.map { it.first })
    }

    @Test
    fun recoveryActivitiesStartFromTheHost() {
        navigator.openPreviousSecureStorageRecovery()
        assertEquals(WalletKeyLossRecoveryActivity::class.java.name, shadowOf(host).nextStartedActivity.component?.className)
        navigator.openWalletEmergencyExport()
        assertEquals(WalletEmergencyExportActivity::class.java.name, shadowOf(host).nextStartedActivity.component?.className)
    }

    @Test
    fun resultRoutesOpenTheirSheetWhenCollected() {
        assertRoutes(
            R.id.walletSelectorFragment to { started(navigator.openWalletSelectorForResult(3, WalletSelectionMode.entries.first())) },
            R.id.selectImportModeDialog to { started(navigator.openSelectImportModeForResult()) },
            R.id.selectImportModeDialog to { started(navigator.openSelectAccountImportModeForResult()) },
            R.id.swapPreviewFragment to { started(navigator.openSwapPreviewForResult(mockk(relaxed = true), mockk(relaxed = true))) },
            R.id.addressHistoryFragment to { started(navigator.openAddressHistoryWithResult("polkadot")) },
            R.id.contactsFragment to { started(navigator.openContactsWithResult("polkadot")) }
        )
    }

    @Test
    fun resultsAreWrittenToAndReadFromTheRightEntries() {
        val current = entry(R.id.walletFragment)
        val previous = entry(R.id.balanceDetailFragment)
        val target = entry(R.id.startSelectValidatorsFragment)
        `when`(root.currentBackStackEntry).thenReturn(current)
        `when`(root.previousBackStackEntry).thenReturn(previous)
        `when`(root.getBackStackEntry(R.id.startSelectValidatorsFragment)).thenReturn(target)

        navigator.setAlertResult("alert", Result.success(Unit), null)
        navigator.setAlertResult("alert", Result.success(Unit), R.id.startSelectValidatorsFragment)
        assertEquals(Result.success(Unit), previous.savedStateHandle.get<Result<Unit>>("alert"))
        assertEquals(Result.success(Unit), target.savedStateHandle.get<Result<Unit>>("alert"))

        navigator.setChainSelectorPayload("kusama")
        assertEquals("kusama", previous.savedStateHandle.get<String>(ChainSelectFragment.KEY_SELECTED_CHAIN_ID))
        val payload = mockk<jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload>(relaxed = true)
        navigator.setWalletSelectorPayload(payload)
        assertSame(payload, previous.savedStateHandle.get(jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload::class.java.name))

        current.savedStateHandle[ChainSelectFragment.KEY_SELECTED_CHAIN_ID] = "polkadot"
        assertEquals(listOf("polkadot"), collectOnMain(navigator.chainSelectorPayloadFlow))
        current.savedStateHandle[jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload::class.java.name] = payload
        assertEquals(listOf(payload), collectOnMain(navigator.walletSelectorPayloadFlow))
        assertSame(current.lifecycle, navigator.currentStackEntryLifecycle)
    }

    @Test
    fun alertResultsAreDeliveredOnceWhenTheirScreenResumes() {
        val current = entry(R.id.walletFragment)
        val startSelect = entry(R.id.startSelectValidatorsFragment)
        val startChange = entry(R.id.startChangeValidatorsFragment)
        val main = entry(R.id.mainFragment)
        `when`(root.currentBackStackEntry).thenReturn(current)
        `when`(root.getBackStackEntry(R.id.startSelectValidatorsFragment)).thenReturn(startSelect)
        `when`(root.getBackStackEntry(R.id.startChangeValidatorsFragment)).thenReturn(startChange)
        `when`(root.getBackStackEntry(R.id.mainFragment)).thenReturn(main)

        current.savedStateHandle["current"] = Result.success(Unit)
        assertEquals(listOf(Result.success(Unit)), collectOnMain(navigator.alertResultFlow("current")))
        assertNull(current.savedStateHandle.get<Result<Unit>>("current"))

        current.savedStateHandle["network"] = Result.failure<Unit>(IllegalStateException("offline"))
        assertEquals(1, collectOnMain(navigator.listenAlertResultFlowFromNetworkIssuesScreen("network")).size)

        startSelect.savedStateHandle["select"] = Result.success(Unit)
        assertEquals(1, collectOnMain(navigator.listenAlertResultFlowFromStartSelectValidatorsScreen("select")).size)
        startChange.savedStateHandle["change"] = Result.success(Unit)
        assertEquals(1, collectOnMain(navigator.listenAlertResultFlowFromStartChangeValidatorsScreen("change")).size)
        assertTrue("Nothing stored, nothing delivered", collectOnMain(navigator.alertResultFlow("absent")).isEmpty())

        main.savedStateHandle[PolkaswapDisclaimerFragment.KEY_DISCLAIMER_READ_RESULT] = true
        assertEquals(listOf(true), collectOnMain(navigator.listenPolkaswapDisclaimerResultFlowFromMainScreen()))
        assertNull(main.savedStateHandle.get<Boolean>(PolkaswapDisclaimerFragment.KEY_DISCLAIMER_READ_RESULT))
    }

    @Test
    fun observedResultsSkipEmptyValuesAndStoriesReportCompletion() {
        val current = entry(R.id.walletFragment)
        val previous = entry(R.id.stakingFragment)
        `when`(root.currentBackStackEntry).thenReturn(current)
        `when`(root.previousBackStackEntry).thenReturn(previous)
        val history = flowOf(entry(R.id.walletFragment), entry(R.id.assetDetailFragment))
        `when`(root.currentBackStackEntryFlow).thenReturn(history)

        val observed = collectOnMain(navigator.observeResult<String>("picked"))
        current.savedStateHandle["picked"] = "polkadot"
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("polkadot"), observed)

        assertEquals(1, runBlocking { navigator.trackReturnToAssetDetailsFromChainSelector()!!.toList() }.size)
        assertEquals(2, runBlocking { navigator.getCurrentBackStackEntryFlow().toList() }.size)

        navigator.educationalStoriesCompleted()
        assertEquals(true, previous.savedStateHandle.get<Boolean>(StoryFragment.KEY_STORY))
        assertEquals(1, mockingDetails(root).invocations.count { it.method.name == "navigateUp" })
    }

    @Test
    fun dappRoutesBecomeAvailableOnceTheCurrentScreenCanReachThem() {
        val listener = mockingDetails(root).invocations.single { it.method.name == "addOnDestinationChangedListener" }
            .arguments[0] as NavController.OnDestinationChangedListener
        val reachable = mock(NavDestination::class.java)
        `when`(reachable.getAction(org.mockito.ArgumentMatchers.anyInt())).thenReturn(NavAction(1))
        val controller = mock(NavController::class.java)
        `when`(controller.currentDestination).thenReturn(reachable)
        listener.onDestinationChanged(controller, reachable, null)
        runBlocking { withTimeout(5_000) { navigator.awaitDappRoutes() } }
    }

    @Test
    fun delayedNavigationSurvivesParcelling() {
        val original = NavComponentDelayedNavigation(R.id.exportSeedFragment, Bundle().apply { putString("chain", "polkadot") })
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(original, 0)
            parcel.setDataPosition(0)
            val restored = parcel.readParcelable<NavComponentDelayedNavigation>(NavComponentDelayedNavigation::class.java.classLoader)!!
            assertEquals(R.id.exportSeedFragment, restored.globalActionId)
            assertEquals("polkadot", restored.extras?.getString("chain"))
        } finally {
            parcel.recycle()
        }
    }

    private fun assertRoutes(vararg routes: Pair<Int, () -> Unit>) {
        routes.forEachIndexed { index, (destination, action) ->
            assertEquals("route #$index", listOf(destination), route(action).map { it.first })
        }
    }

    private fun route(action: () -> Unit): List<Pair<Int, Bundle?>> = navigations(action).map { (id, args, _) -> id to args }

    private fun navigations(action: () -> Unit): List<Triple<Int, Bundle?, NavOptions?>> {
        clearInvocations(root)
        action()
        return mockingDetails(root).invocations
            .filter { it.method.name == "navigate" && it.arguments.firstOrNull() is Int }
            .map { Triple(it.arguments[0] as Int, it.arguments.getOrNull(1) as Bundle?, it.arguments.getOrNull(2) as? NavOptions) }
    }

    private fun <T> started(flow: Flow<T>) = runBlocking {
        launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { } }.cancelAndJoin()
    }

    private fun <T> collectOnMain(flow: Flow<T>): MutableList<T> {
        val values = mutableListOf<T>()
        jobs += CoroutineScope(Dispatchers.Main.immediate).launch { flow.collect { values += it } }
        shadowOf(Looper.getMainLooper()).idle()
        return values
    }

    private fun destination(id: Int): NavDestination = mock(NavDestination::class.java).also { `when`(it.id).thenReturn(id) }

    private fun entry(id: Int): NavBackStackEntry {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }
        val handle = SavedStateHandle()
        val destination = destination(id)
        return mock(NavBackStackEntry::class.java).also {
            `when`(it.savedStateHandle).thenReturn(handle)
            `when`(it.lifecycle).thenReturn(owner.lifecycle)
            `when`(it.destination).thenReturn(destination)
        }
    }
}
