package jp.co.soramitsu.app.root.navigation

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavGraph
import androidx.navigation.NavOptions
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import co.jp.soramitsu.walletconnect.model.ChainChooseResult
import co.jp.soramitsu.walletconnect.model.ChainChooseState
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateSignerPayload
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.account.chainaccounts.ChainAccountsDialog
import jp.co.soramitsu.account.impl.presentation.account.create.CreateAccountDialog
import jp.co.soramitsu.account.impl.presentation.account.create.CreateAccountFragment
import jp.co.soramitsu.account.impl.presentation.account.details.AccountDetailsDialog
import jp.co.soramitsu.account.impl.presentation.account.rename.RenameAccountDialog
import jp.co.soramitsu.account.impl.presentation.backup_wallet.BackupWalletDialog
import jp.co.soramitsu.account.impl.presentation.create_backup_password.CreateBackupPasswordDialog
import jp.co.soramitsu.account.impl.presentation.experimental.SuccessfulFragment
import jp.co.soramitsu.account.impl.presentation.exporting.json.confirm.ExportJsonConfirmFragment
import jp.co.soramitsu.account.impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.account.impl.presentation.exporting.json.password.ExportJsonPasswordFragment
import jp.co.soramitsu.account.impl.presentation.exporting.mnemonic.ExportMnemonicFragment
import jp.co.soramitsu.account.impl.presentation.exporting.seed.ExportSeedFragment
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountFragment
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.ImportRemoteWalletDialog
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.BackupMnemonicDialog
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.BackupMnemonicFragment
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicFragment
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.account.impl.presentation.mnemonic_agreements.MnemonicAgreementsDialog
import jp.co.soramitsu.account.impl.presentation.node.add.AddNodeFragment
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsFragment
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.account.impl.presentation.node.list.NodesFragment
import jp.co.soramitsu.account.impl.presentation.nomis_scoring.ScoreDetailsFragment
import jp.co.soramitsu.account.impl.presentation.options_ecosystem_accounts.OptionsEcosystemAccountsFragment
import jp.co.soramitsu.account.impl.presentation.options_switch_node.OptionsSwitchNodeFragment
import jp.co.soramitsu.account.impl.presentation.optionsaddaccount.OptionsAddAccountFragment
import jp.co.soramitsu.account.impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.account.impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.account.impl.presentation.pincode.ToolbarConfiguration
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.presentation.AlertFragment
import jp.co.soramitsu.app.root.presentation.RootRouter
import jp.co.soramitsu.app.root.presentation.WebViewerFragment
import jp.co.soramitsu.app.root.presentation.emptyResultKey
import jp.co.soramitsu.app.root.presentation.main.navigateToMainTabDestination
import jp.co.soramitsu.app.root.presentation.stories.StoryFragment
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.utils.postToUiThread
import jp.co.soramitsu.common.view.onResumeObserver
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsRouter
import jp.co.soramitsu.nft.impl.presentation.NFTFlowFragment
import jp.co.soramitsu.nft.navigation.NFTRouter
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment
import jp.co.soramitsu.onboarding.impl.welcome.select_import_mode.SelectImportModeDialog
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkamarkt.api.PolkamarktRouter
import jp.co.soramitsu.polkamarkt.impl.presentation.PolkamarktFragment
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.impl.presentation.disclaimer.PolkaswapDisclaimerFragment
import jp.co.soramitsu.polkaswap.impl.presentation.swap_preview.SwapPreviewFragment
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensFragment
import jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings.TransactionSettingsFragment
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.splash.SplashRouter
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.ConfirmPayoutFragment
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.staking.impl.presentation.payouts.detail.PayoutDetailsFragment
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.staking.impl.presentation.pools.PoolInfoFragment
import jp.co.soramitsu.staking.impl.presentation.pools.PoolInfoOptionsFragment
import jp.co.soramitsu.staking.impl.presentation.staking.balance.StakingBalanceFragment
import jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm.ConfirmBondMoreFragment
import jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm.ConfirmBondMorePayload
import jp.co.soramitsu.staking.impl.presentation.staking.bond.select.SelectBondMoreFragment
import jp.co.soramitsu.staking.impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.staking.impl.presentation.staking.controller.confirm.ConfirmSetControllerFragment
import jp.co.soramitsu.staking.impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm.ConfirmRebondFragment
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.RedeemFragment
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.ConfirmRewardDestinationFragment
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.PoolFullUnstakeDepositorAlertFragment
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.ConfirmUnbondFragment
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.ConfirmUnbondPayload
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.select.SelectUnbondFragment
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.select.SelectUnbondPayload
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.select.compose.SelectCustomValidatorsFragment
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.settings.CustomValidatorsSettingsFragment
import jp.co.soramitsu.staking.impl.presentation.validators.details.CollatorDetailsFragment
import jp.co.soramitsu.staking.impl.presentation.validators.details.ValidatorDetailsFragment
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.success.presentation.SuccessFragment
import jp.co.soramitsu.success.api.presentation.SuccessRouter
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.impl.presentation.connectioninfo.TonConnectionInfoFragment
import jp.co.soramitsu.tonconnect.impl.presentation.dappscreen.DappScreenFragment
import jp.co.soramitsu.tonconnect.impl.presentation.tonconnectiondetails.TonConnectionDetailsFragment
import jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest.TonSignRequestFragment
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.domain.beacon.SignStatus
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.QrContentCBDC
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.addressbook.CreateContactFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.AssetDetailsFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.BalanceDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward.ClaimRewardsFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenTokensFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.legacy.LegacyCrowdloanFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet.OptionsWalletFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectionMode
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectorFragment
import jp.co.soramitsu.wallet.impl.presentation.beacon.main.BeaconFragment
import jp.co.soramitsu.wallet.impl.presentation.beacon.main.DAppMetadataModel
import jp.co.soramitsu.wallet.impl.presentation.beacon.sign.SignBeaconTransactionFragment
import jp.co.soramitsu.wallet.impl.presentation.beacon.sign.TransactionRawDataFragment
import jp.co.soramitsu.wallet.impl.presentation.contacts.ContactsFragment
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.CrossChainConfirmFragment
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup.CrossChainSetupFragment
import jp.co.soramitsu.wallet.impl.presentation.history.AddressHistoryFragment
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.receive.ReceiveFragment
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.ConfirmSendFragment
import jp.co.soramitsu.wallet.impl.presentation.send.setup.SendSetupFragment
import jp.co.soramitsu.wallet.impl.presentation.send.setupcbdc.CBDCSendSetupFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap.SwapDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer.TransferDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.filter.TransactionHistoryFilterFragment
import jp.co.soramitsu.walletconnect.impl.presentation.chainschooser.ChainChooseFragment
import jp.co.soramitsu.walletconnect.impl.presentation.connectioninfo.ConnectionInfoFragment
import jp.co.soramitsu.walletconnect.impl.presentation.requestpreview.RequestPreviewFragment
import jp.co.soramitsu.walletconnect.impl.presentation.sessionproposal.SessionProposalFragment
import jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest.WalletConnectSignMessageFragment
import jp.co.soramitsu.walletconnect.impl.presentation.transactionrawdata.RawDataFragment
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

@Parcelize
class NavComponentDelayedNavigation(val globalActionId: Int, val extras: Bundle? = null) : DelayedNavigation

class Navigator :
    SplashRouter,
    OnboardingRouter,
    AccountRouter,
    WalletRouter,
    RootRouter,
    StakingRouter,
    PolkaswapRouter,
    PolkamarktRouter,
    SuccessRouter,
    WalletConnectRouter,
    TonConnectRouter,
    NFTRouter,
    LiquidityPoolsRouter
{

    private var navController: NavController? = null
    private var mainTabNavController: NavController? = null
    private var activity: AppCompatActivity? = null

    fun attach(navController: NavController, activity: AppCompatActivity) {
        this.navController = navController
        this.activity = activity
    }

    fun detach() {
        navController = null
        mainTabNavController = null
        activity = null
    }

    fun attachMainTabs(navController: NavController) {
        mainTabNavController = navController
    }

    fun detachMainTabs(navController: NavController) {
        if (mainTabNavController === navController) {
            mainTabNavController = null
        }
    }

    /** Keeps authenticated product routes inside MainFragment so its tab bar remains mounted. */
    private fun navigateInMainTabOrRoot(
        @IdRes destinationId: Int,
        args: Bundle? = null,
        navOptions: NavOptions? = null
    ) {
        val shellController = mainTabNavController
        val targetController = checkNotNull(authenticatedDestinationTarget(
            shellController = mainTabNavController,
            shellContainsDestination = { it.graph.containsDestination(destinationId) },
            parentController = { navController }
        )) {
            "Destination $destinationId is not owned by the authenticated shell"
        }

        if (targetController === shellController) {
            navigateToMainTabDestination(targetController, destinationId, args, navOptions)
        } else {
            targetController.navigate(destinationId, args, navOptions)
        }
    }

    private fun activeAuthenticatedController(): NavController? {
        val rootController = navController
        val rootStillShowsMainShell = rootController?.currentDestination?.id == R.id.mainFragment
        return if (mainTabNavController != null && rootStillShowsMainShell) {
            mainTabNavController
        } else {
            rootController
        }
    }

    private fun controllerFor(@IdRes destinationId: Int): NavController? {
        return authenticatedDestinationTarget(
            shellController = mainTabNavController,
            shellContainsDestination = { it.graph.containsDestination(destinationId) },
            parentController = { navController }
        )
    }

    private fun popAuthenticatedBackStack(@IdRes destinationId: Int, inclusive: Boolean = false) {
        activeAuthenticatedController()?.popBackStack(destinationId, inclusive)
    }

    private fun NavGraph.containsDestination(@IdRes destinationId: Int): Boolean {
        if (id == destinationId) return true
        return any { destination ->
            destination.id == destinationId ||
                (destination is NavGraph && destination.containsDestination(destinationId))
        }
    }

    override fun openOnboarding() {
        navController?.navigate(R.id.action_to_onboarding, WelcomeFragment.getBundle(false))
    }

    override fun openCreatePincode() {
        val action = PinCodeAction.Create(NavComponentDelayedNavigation(R.id.action_open_main))
        val bundle =  PincodeFragment.getPinCodeBundle(action)
        navController?.navigate(R.id.pincodeFragment, bundle)
    }


    override fun openInitialCheckPincode() {
        val action = PinCodeAction.Check(NavComponentDelayedNavigation(R.id.action_open_main), ToolbarConfiguration())
        val bundle = PincodeFragment.getPinCodeBundle(action)
        navController?.navigateSafe(R.id.pincodeFragment, bundle)
    }

    private fun NavController.navigateSafe(@IdRes resId: Int, args: Bundle?) {
        runCatching { navigate(resId, args) }
    }

    override fun openCreateAccountFromOnboarding(accountType: AccountType) {
        val bundle = CreateAccountFragment.getBundle(accountType)
        navController?.navigate(R.id.action_welcomeFragment_to_createAccountFragment, bundle)
    }

    override fun openCreateWalletDialogFromGoogleBackup() {
        val bundle = CreateAccountDialog.getBundle()
        navController?.navigate(R.id.createAccountDialog, bundle)
    }

    override fun openCreateAccountFromWallet() {
        val bundle = WelcomeFragment.getBundle(
            displayBack = true,
            chainAccountData = null,
            route = "WelcomeScreen"
        )
        navController?.navigate(R.id.action_to_onboardingNavGraph, bundle)
    }

    override fun openImportAccountScreenFromWallet(blockChainType: Int) {
        val request = NavDeepLinkRequest.Builder
            .fromUri("fearless://onboarding/importAccountFragment/$blockChainType".toUri())
            .build()
        navController?.navigate(request)
    }

    override fun openManageControllerAccount(chainId: ChainId) {
        val request = NavDeepLinkRequest.Builder
            .fromUri("fearless://staking/setControllerAccountFragment/$chainId".toUri())
            .build()
        navController?.navigate(request)
    }

    override fun openImportRemoteWalletDialog() {
        val bundle = ImportRemoteWalletDialog.getBundle()
        navController?.navigate(R.id.importRemoteWalletDialog, bundle)
    }

    override fun openCreateBackupPasswordDialogWithResult(payload: SaveBackupPayload?): Flow<Int> {
        val bundle = CreateBackupPasswordDialog.getBundle(payload)
        return openWithResult(
            destinationId = R.id.createBackupPasswordDialog,
            resultKey = CreateBackupPasswordDialog.RESULT_BACKUP_KEY,
            bundle = bundle
        )
    }

    override fun openMnemonicAgreementsDialogForGoogleBackup(
        accountName: String,
        accountTypes: List<WalletEcosystem>
    ) {
        val bundle = MnemonicAgreementsDialog.getBundle(accountName, accountTypes)
        navController?.navigate(R.id.mnemonicAgreementsDialog, bundle)
    }

    override fun popOutOfSend() {
        activeAuthenticatedController()?.popBackStack(R.id.sendSetupFragment, true)
    }

    override fun openOnboardingNavGraph(chainId: ChainId, metaId: Long, isImport: Boolean) {
        val bundle = WelcomeFragment.getBundle(
            displayBack = true,
            chainAccountData = ChainAccountCreatePayload(chainId, metaId, isImport)
        )
        navController?.navigate(R.id.action_to_onboardingNavGraph, bundle)
    }


    override fun openCreateSubstrateOrEvmAccountScreen() {
        val bundle = WelcomeFragment.getBundle(
            displayBack = true,
            chainAccountData = null,
            route = "WelcomeScreen?accountType=SubstrateOrEvm"
        )
        navController?.navigate(R.id.action_to_onboardingNavGraph, bundle)
    }

    override fun openCreateTonAccountScreen() {
        val bundle = WelcomeFragment.getBundle(
            displayBack = true,
            chainAccountData = null,
            route = "WelcomeScreen?accountType=Ton"
        )
        navController?.navigate(R.id.action_to_onboardingNavGraph, bundle)
    }

    override fun backToWelcomeScreen() {
        navController?.popBackStack()
    }

    override fun openMain() {
        navController?.navigate(R.id.action_open_main)
    }

    override fun openAfterPinCode(delayedNavigation: DelayedNavigation) {
        require(delayedNavigation is NavComponentDelayedNavigation)

        val shouldOpenMain = delayedNavigation.globalActionId == R.id.action_open_main

        val navOptions = NavOptions.Builder()
            .apply {
                if (shouldOpenMain) {
                    setPopUpTo(R.id.root_nav_graph, false)
                } else {
                    setPopUpTo(R.id.pincodeFragment, true)
                }
            }
            .setEnterAnim(R.animator.fragment_open_enter)
            .setExitAnim(R.animator.fragment_open_exit)
            .setPopEnterAnim(R.animator.fragment_close_enter)
            .setPopExitAnim(R.animator.fragment_close_exit)
            .build()

        navController?.navigate(delayedNavigation.globalActionId, delayedNavigation.extras, navOptions)
    }

    override fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload) {
        val bundle = ConfirmMnemonicFragment.getBundle(confirmMnemonicPayload)

        navController?.navigate(R.id.confirmExportMnemonicFragment, bundle)
    }

    override fun openAboutScreen() {
        navigateInMainTabOrRoot(R.id.aboutFragment)
    }

    override fun openImportAddAccountScreen(
        walletId: Long,
        walletEcosystem: WalletEcosystem,
        importMode: ImportMode
    ) {
        val arguments = ImportAccountFragment.getBundle(walletId, walletEcosystem, importMode)
        navController?.navigate(R.id.importAccountFragment, arguments)
    }

    override fun openImportAccountScreen(walletEcosystem: WalletEcosystem, importMode: ImportMode) {
        val arguments = ImportAccountFragment.getBundle(null, walletEcosystem, importMode)
        navController?.navigate(R.id.importAccountFragment, arguments)
    }

    override fun openMnemonicScreenAddAccount(walletId: Long, accountName: String, type: WalletEcosystem) {
        val bundle = BackupMnemonicFragment.getBundle(accountName, walletId, listOf(type))
        navController?.navigate(R.id.backupMnemonicFragment, bundle)
    }

    override fun openMnemonicScreen(
        accountName: String,
        accountTypes: List<WalletEcosystem>
    ) {
        val bundle = BackupMnemonicFragment.getBundle(accountName, null, accountTypes)
        navController?.navigate(R.id.backupMnemonicFragment, bundle)
    }

    override fun openMnemonicDialogGoogleBackup(
        accountName: String,
        accountTypes: List<WalletEcosystem>
    ) {
        val bundle = BackupMnemonicDialog.getBundle(accountName, accountTypes)
        navController?.navigate(R.id.backupMnemonicDialog, bundle)
    }

    override fun backToCreateAccountScreen() {
        navController?.popBackStack()
    }

    override fun backToBackupMnemonicScreen() {
        navController?.popBackStack()
    }

    override fun backToProfileScreen() {
        navController?.popBackStack()
    }

    override fun openSetupStaking() {
        navigateInMainTabOrRoot(R.id.setupStakingFragment)
    }

    override fun openStartChangeValidators() {
        navigateInMainTabOrRoot(R.id.startChangeValidatorsFragment)
    }

    override fun openStartChangeCollators() {
        navigateInMainTabOrRoot(R.id.startChangeCollatorsFragment)
    }

    override fun openStory(story: StoryGroupModel) {
        navigateInMainTabOrRoot(R.id.stakingStoryFragment, StoryFragment.getBundle(story))
    }

    override fun openPayouts() {
        navigateInMainTabOrRoot(R.id.payoutsListFragment)
    }

    override fun openPayoutDetails(payout: PendingPayoutParcelable) {
        navigateInMainTabOrRoot(R.id.payoutDetailsFragment, PayoutDetailsFragment.getBundle(payout))
    }

    override fun openConfirmPayout(payload: ConfirmPayoutPayload) {
        navigateInMainTabOrRoot(R.id.confirmPayoutFragment, ConfirmPayoutFragment.getBundle(payload))
    }

    override fun openStakingBalance(collatorAddress: String?) {
        val bundle = collatorAddress?.let { StakingBalanceFragment.getBundle(it) }
        navigateInMainTabOrRoot(R.id.stakingBalanceFragment, bundle)
    }

    override fun openBondMore(payload: SelectBondMorePayload) {
        navigateInMainTabOrRoot(R.id.selectBondMoreFragment, SelectBondMoreFragment.getBundle(payload))
    }

    override fun openConfirmBondMore(payload: ConfirmBondMorePayload) {
        navigateInMainTabOrRoot(R.id.confirmBondMoreFragment, ConfirmBondMoreFragment.getBundle(payload))
    }

    override fun returnToStakingBalance() {
        popAuthenticatedBackStack(R.id.stakingBalanceFragment)
    }

    override fun returnToManagePoolStake() {
        popAuthenticatedBackStack(R.id.managePoolStakeFragment)
    }

    override fun openCreatePoolSetup() {
        navigateInMainTabOrRoot(R.id.createPoolSetupFragment)
    }

    override fun openCreatePoolConfirm() {
        navigateInMainTabOrRoot(R.id.confirmCreatePoolFragment)
    }

    override fun openWalletSelector(tag: String) {
        navigateInMainTabOrRoot(
            R.id.walletSelectorFragment,
            WalletSelectorFragment.buildArguments(tag)
        )
    }

    override fun openWalletSelectorForResult(
        selectedWalletId: Long?,
        walletSelectionMode: WalletSelectionMode
    ): Flow<Long> {
        val bundle = WalletSelectorFragment.buildArguments(
            tag = "",
            selectedWalletId = selectedWalletId,
            walletSelectionMode = walletSelectionMode
        )
        return openWithResult(
            destinationId = R.id.walletSelectorFragment,
            bundle = bundle,
            resultKey = WalletSelectorFragment.RESULT_ADDRESS
        )
    }

    override fun openSelectUnbond(payload: SelectUnbondPayload) {
        navigateInMainTabOrRoot(R.id.selectUnbondFragment, SelectUnbondFragment.getBundle(payload))
    }

    override fun openConfirmUnbond(payload: ConfirmUnbondPayload) {
        navigateInMainTabOrRoot(R.id.confirmUnbondFragment, ConfirmUnbondFragment.getBundle(payload))
    }

    override fun openRedeem(payload: RedeemPayload) {
        navigateInMainTabOrRoot(R.id.redeemFragment, RedeemFragment.getBundle(payload))
    }

    override fun openConfirmRebond(payload: ConfirmRebondPayload) {
        navigateInMainTabOrRoot(R.id.confirmRebondFragment, ConfirmRebondFragment.getBundle(payload))
    }

    override fun back() {
        val popped = requireNotNull(activeAuthenticatedController()).popBackStack()

        if (!popped) {
            activity!!.finish()
        }
    }

    override fun backWithResult(vararg results: Pair<String, Any?>) {
        val savedStateHandle = activeAuthenticatedController()?.previousBackStackEntry?.savedStateHandle

        if (savedStateHandle != null) {
            results.forEach { (key, value) ->
                savedStateHandle[key] = value
            }
        }
        back()
    }

    override fun backWithResult(resultDestinationId: Int, vararg results: Pair<String, Any?>) {
        val savedStateHandle =
            runCatching { activeAuthenticatedController()?.getBackStackEntry(resultDestinationId)?.savedStateHandle }.getOrNull()

        if (savedStateHandle != null) {
            results.forEach { (key, value) ->
                savedStateHandle[key] = value
            }
        }
        back()
    }

    override fun openSelectImportModeForResult(): Flow<ImportMode> {
        val bundle = SelectImportModeDialog.getBundle()
        return openWithResult(
            destinationId = R.id.selectImportModeDialog,
            bundle = bundle,
            resultKey = SelectImportModeDialog.RESULT_IMPORT_MODE
        )
    }

    override fun openTransactionSettingsDialog(initialSettings: TransactionSettingsModel) {
        val bundle = TransactionSettingsFragment.getBundle(initialSettings)
        navigateInMainTabOrRoot(R.id.transactionSettingsFragment, bundle)
    }

    override fun openSwapPreviewDialog(swapDetailsViewState: SwapDetailsViewState, parcelModel: SwapDetailsParcelModel) {
        val bundle = SwapPreviewFragment.getBundle(swapDetailsViewState, parcelModel)

        navigateInMainTabOrRoot(R.id.swapPreviewFragment, bundle)
    }

    override fun openSwapPreviewForResult(swapDetailsViewState: SwapDetailsViewState, parcelModel: SwapDetailsParcelModel): Flow<Int> {
        val bundle = SwapPreviewFragment.getBundle(swapDetailsViewState, parcelModel)
        return openWithResult(
            destinationId = R.id.swapPreviewFragment,
            bundle = bundle,
            resultKey = SwapPreviewFragment.KEY_SWAP_DETAILS_RESULT
        )
    }

    override fun openSelectMarketDialog() {
        navigateInMainTabOrRoot(R.id.selectMarketFragment)
    }

    override fun openCustomRebond() {
        navigateInMainTabOrRoot(R.id.customRebondFragment)
    }

    override fun openCurrentValidators() {
        navigateInMainTabOrRoot(R.id.currentValidatorsFragment)
    }

    override fun returnToCurrentValidators() {
        popAuthenticatedBackStack(R.id.currentValidatorsFragment)
    }

    override fun openChangeRewardDestination() {
        navigateInMainTabOrRoot(R.id.selectRewardDestinationFragment)
    }

    override fun openConfirmRewardDestination(payload: ConfirmRewardDestinationPayload) {
        navigateInMainTabOrRoot(
            R.id.confirmRewardDestinationFragment,
            ConfirmRewardDestinationFragment.getBundle(payload)
        )
    }

    override fun openStakingPoolWelcome() {
        navigateInMainTabOrRoot(R.id.startStakingPoolFragment)
    }

    override fun openSetupStakingPool() {
        navigateInMainTabOrRoot(R.id.setupStakingPoolFragment)
    }

    override fun openConfirmJoinPool() {
        navigateInMainTabOrRoot(R.id.confirmJoinPoolFragment)
    }

    override fun openPoolInfo(poolId: Int) {
        navigateInMainTabOrRoot(R.id.poolInfoFragment, PoolInfoFragment.getBundle(poolId))
    }

    override fun openManagePoolStake() {
        navigateInMainTabOrRoot(R.id.managePoolStakeFragment)
    }

    override fun openPoolBondMore() {
        navigateInMainTabOrRoot(R.id.poolBondMoreFragment)
    }

    override fun openPoolClaim() {
        navigateInMainTabOrRoot(R.id.poolClaimFragment)
    }

    override fun openPoolRedeem() {
        navigateInMainTabOrRoot(R.id.poolRedeemFragment)
    }

    override fun openPoolUnstake() {
        navigateInMainTabOrRoot(R.id.poolUnstakeFragment)
    }

    override fun openPoolConfirmBondMore() {
        navigateInMainTabOrRoot(R.id.poolConfirmBondMoreFragment)
    }

    override fun openPoolConfirmClaim() {
        navigateInMainTabOrRoot(R.id.poolConfirmClaimFragment)
    }

    override fun openPoolConfirmRedeem() {
        navigateInMainTabOrRoot(R.id.poolConfirmRedeemFragment)
    }

    override fun openPoolConfirmUnstake() {
        navigateInMainTabOrRoot(R.id.poolConfirmUnstakeFragment)
    }

    override val currentStackEntryLifecycle: Lifecycle
        get() = requireNotNull(activeAuthenticatedController()?.currentBackStackEntry).lifecycle

    override fun openControllerAccount() {
        navigateInMainTabOrRoot(R.id.setControllerAccountFragment)
    }

    override fun openConfirmSetController(payload: ConfirmSetControllerPayload) {
        navigateInMainTabOrRoot(
            R.id.confirmSetControllerAccount,
            ConfirmSetControllerFragment.getBundle(payload)
        )
    }

    override fun openRecommendedCollators() {
        navigateInMainTabOrRoot(R.id.recommendedCollatorsFragment)
    }

    override fun openSelectCustomCollators() {
        navigateInMainTabOrRoot(R.id.selectCustomCollatorsFragment)
    }

    override fun openSelectPool() {
        navigateInMainTabOrRoot(R.id.selectPoolFramgent)
    }

    override fun openRecommendedValidators() {
        val args = SelectCustomValidatorsFragment.getBundle(SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED)
        navigateInMainTabOrRoot(R.id.recommendedValidatorsFragment, args)
    }

    override fun openSelectCustomValidators() {
        val args = SelectCustomValidatorsFragment.getBundle(SelectValidatorFlowState.ValidatorSelectMode.CUSTOM)
        navigateInMainTabOrRoot(R.id.selectCustomValidatorsFragment, args)
    }

    override fun openCustomValidatorsSettingsFromValidator() {
        val bundle = CustomValidatorsSettingsFragment.getBundle(Asset.StakingType.RELAYCHAIN)
        navigateInMainTabOrRoot(R.id.settingsCustomValidatorsFragment, bundle)
    }

    override fun openCustomValidatorsSettingsFromCollator() {
        val bundle = CustomValidatorsSettingsFragment.getBundle(Asset.StakingType.PARACHAIN)
        navigateInMainTabOrRoot(R.id.settingsCustomValidatorsFragment, bundle)
    }

    override fun openSearchCustomValidators() {
        navigateInMainTabOrRoot(R.id.searchCustomValidatorsFragment)
    }

    override fun openSearchCustomCollators() {
        navigateInMainTabOrRoot(R.id.searchCustomValidatorsFragment)
    }

    override fun openReviewCustomValidators() {
        navigateInMainTabOrRoot(R.id.reviewCustomValidatorsFragment)
    }

    override fun openConfirmStaking() {
        navigateInMainTabOrRoot(R.id.confirmStakingFragment)
    }

    override fun openConfirmNominations() {
        navigateInMainTabOrRoot(R.id.confirmNominationsFragment)
    }

    override fun returnToMain() {
        popAuthenticatedBackStack(R.id.defiHubFragment)
    }

    override fun closeSwap() {
        if (mainTabNavController != null) {
            popAuthenticatedBackStack(R.id.polkaswapHubFragment)
        } else {
            navController?.navigate(R.id.close_swap)
        }
    }

    override fun openValidatorDetails(validatorIdHex: String) {
        navigateInMainTabOrRoot(
            R.id.validatorDetailsFragment,
            ValidatorDetailsFragment.getBundle(validatorIdHex)
        )
    }

    override fun openSelectedValidators() {
        navigateInMainTabOrRoot(R.id.selectedValidatorsFragment)
    }

    override fun openCollatorDetails(collatorDetails: CollatorDetailsParcelModel) {
        navigateInMainTabOrRoot(
            R.id.collatorDetailsFragment,
            CollatorDetailsFragment.getBundle(collatorDetails)
        )
    }

    override fun openSend(assetPayload: AssetPayload?, initialSendToAddress: String?, currencyId: String?, amount: BigDecimal?) {
        val bundle = SendSetupFragment.getBundle(assetPayload, initialSendToAddress, currencyId, amount, false)

        navigateInMainTabOrRoot(R.id.sendSetupFragment, bundle)
    }

    override fun openWalletConnectSessionProposal(pairingTopic: String?) {
        if (navController?.currentDestination?.id == R.id.sessionProposalFragment) return

        val bundle = SessionProposalFragment.getBundle(pairingTopic)

        navController?.navigate(R.id.sessionProposalFragment, bundle)
    }

    override fun openWalletConnectSessionRequest(sessionRequestTopic: String) {
        if (navController?.currentDestination?.id == R.id.sessionRequestFragment) return

        val bundle = WalletConnectSignMessageFragment.getBundle(sessionRequestTopic)

        navController?.navigate(R.id.sessionRequestFragment, bundle)
    }

    override fun openLockedAmountSend(assetPayload: AssetPayload?, initialSendToAddress: String?, currencyId: String?, amount: BigDecimal?) {
        val bundle = SendSetupFragment.getBundle(assetPayload, initialSendToAddress, currencyId, amount, true)

        navigateInMainTabOrRoot(R.id.sendSetupFragment, bundle)
    }

    override fun openCBDCSend(cbdcQrInfo: QrContentCBDC) {
        val bundle = CBDCSendSetupFragment.getBundle(cbdcQrInfo)

        navigateInMainTabOrRoot(R.id.cbdcSendSetupFragment, bundle)
    }

    override fun openCrossChainSend(assetPayload: AssetPayload?) {
        val bundle = CrossChainSetupFragment.getBundle(assetPayload)
        navigateInMainTabOrRoot(R.id.crossChainFragment, bundle)
    }

    private fun <T> openWithResult(
        @IdRes destinationId: Int,
        resultKey: String,
        bundle: Bundle? = null
    ): Flow<T> {
        val targetController = controllerFor(destinationId)
        val resultFlow = observeResultInternal<T>(resultKey, targetController)
        val backStackEntryFlow = targetController?.currentBackStackEntryFlow ?: emptyFlow()
        return combine(resultFlow, backStackEntryFlow) { result, backStackEntry ->
            Pair(result, backStackEntry)
        }
            .onStart { targetController?.navigate(destinationId, bundle) }
            .filter {
                val (_, backStackEntry) = it
                backStackEntry.destination.id != destinationId
            }
            .onEach { coroutineContext.job.cancel() }
            .map {
                val (result, _) = it
                result
            }
            .mapNotNull { it }
            .onEach { removeSavedStateHandle(resultKey, targetController) }
    }

    private suspend fun <T> openAndWaitResult(
        @IdRes destinationId: Int,
        resultKey: String,
        bundle: Bundle? = null
    ): T {
        val isCompleted = AtomicBoolean(false)

        return coroutineScope {
            suspendCancellableCoroutine { continuation ->
                openWithResult<T>(destinationId, resultKey, bundle).onEach { result ->
                    if (continuation.isActive && isCompleted.compareAndSet(false, true)) {
                        continuation.resume(result)
                    }
                }.launchIn(this)

                continuation.invokeOnCancellation {
                    back()
                }
            }
        }
    }

    override fun openSwapTokensScreen(chainId: String?, assetIdFrom: String?, assetIdTo: String?) {
        if (navController?.currentDestination?.id == R.id.swapTokensFragment)
            return

        val bundle = SwapTokensFragment.getBundle(chainId, assetIdFrom, assetIdTo)

        navigateInMainTabOrRoot(R.id.swapTokensFragment, bundle)
    }

    override fun openPolkaswapDisclaimerFromSwapTokensFragment() {
        val bundle = PolkaswapDisclaimerFragment.getBundle(
            R.id.swapTokensFragment
        )

        navigateInMainTabOrRoot(R.id.polkaswapDisclaimerFragment, bundle)
    }

    override fun openSelectChain(
        assetId: String,
        chainId: ChainId?,
        chooserMode: Boolean,
        isSelectAsset: Boolean,
        showAllChains: Boolean
    ) {
        val bundle = ChainSelectFragment.getBundle(
            assetId = assetId,
            chainId = chainId,
            chooserMode = chooserMode,
            isSelectAsset = isSelectAsset,
            showAllChains = showAllChains
        )
        navigateInMainTabOrRoot(R.id.chainSelectFragment, bundle)
    }

    override fun openConnectionsScreen() {
        navigateInMainTabOrRoot(R.id.connectionsFragment)
    }

    override fun openTonConnectionsScreen() {
        navigateInMainTabOrRoot(R.id.tonConnectionsFragment)
    }

    override fun openSelectMultipleChains(
        items: List<String>,
        selected: List<String>,
        isViewMode: Boolean
    ) {
        val bundle = ChainChooseFragment.getBundle(
            state = ChainChooseState(items, selected, isViewMode)
        )
        navigateInMainTabOrRoot(R.id.chainChooseFragment, bundle)
    }

    override fun openSelectMultipleChainsForResult(
        items: List<String>,
        selected: List<String>
    ): Flow<ChainChooseResult> {
        val bundle = ChainChooseFragment.getBundle(state = ChainChooseState(items, selected))
        return openWithResult(
            destinationId = R.id.chainChooseFragment,
            bundle = bundle,
            resultKey = ChainChooseFragment.RESULT
        )
    }

    override fun openConnectionDetails(topic: String) {
        val bundle = ConnectionInfoFragment.getBundle(topic)
        navigateInMainTabOrRoot(R.id.connectionInfoFragment, bundle)
    }

    override fun openRequestPreview(topic: String) {
        val bundle = RequestPreviewFragment.getBundle(topic)
        navigateInMainTabOrRoot(R.id.requestPreviewFragment, bundle)
    }

    override fun openRawData(payload: String) {
        val bundle = RawDataFragment.getBundle(payload)
        navigateInMainTabOrRoot(R.id.rawDataFragment, bundle)
    }

    override fun openSelectChain(
        selectedChainId: ChainId?,
        filterChainIds: List<ChainId>?,
        chooserMode: Boolean,
        currencyId: String?,
        showAllChains: Boolean,
        isSelectAsset: Boolean,
        isFilteringEnabled: Boolean
    ) {
        val bundle = ChainSelectFragment.getBundle(
            selectedChainId,
            filterChainIds,
            chooserMode,
            currencyId,
            showAllChains,
            isSelectAsset,
            isFilteringEnabled
        )
        navigateInMainTabOrRoot(R.id.chainSelectFragment, bundle)
    }

    override fun openSelectChainForXcm(
        selectedChainId: ChainId?,
        xcmChainType: XcmChainType,
        selectedOriginChainId: String?,
        xcmOriginAssetId: String?,
        xcmAssetSymbol: String?
    ) {
        val bundle = ChainSelectFragment.getBundleForXcmChains(
            selectedChainId = selectedChainId,
            xcmChainType = xcmChainType,
            xcmSelectedOriginChainId = selectedOriginChainId,
            xcmOriginAssetId = xcmOriginAssetId,
            xcmAssetSymbol = xcmAssetSymbol
        )
        navigateInMainTabOrRoot(R.id.chainSelectFragment, bundle)
    }

    override fun openSelectAsset(selectedAssetId: String) {
        val bundle = AssetSelectFragment.getBundle(selectedAssetId)
        navigateInMainTabOrRoot(R.id.assetSelectFragment, bundle)
    }

    override fun openSelectAsset(chainId: ChainId, selectedAssetId: String?, isFilterXcmAssets: Boolean) {
        val bundle = AssetSelectFragment.getBundle(chainId, selectedAssetId, isFilterXcmAssets)
        navigateInMainTabOrRoot(R.id.assetSelectFragment, bundle)
    }

    override fun openSelectAsset(chainId: ChainId, selectedAssetId: String?, excludeAssetId: String?) {
        val bundle = AssetSelectFragment.getBundle(chainId, selectedAssetId, excludeAssetId)
        navigateInMainTabOrRoot(R.id.assetSelectFragment, bundle)
    }

    override fun <T> observeResult(key: String): Flow<T> {
        @Suppress("UNCHECKED_CAST")
        return observeResultInternal<T>(key)
            .onStart { removeSavedStateHandle(key) }
            .onCompletion { removeSavedStateHandle(key) }
            .filter { it != null } as Flow<T>
    }

    private fun <T> observeResultInternal(
        key: String,
        controller: NavController? = activeAuthenticatedController()
    ): StateFlow<T?> {
        val savedStateHandle = controller?.currentBackStackEntry?.savedStateHandle
        return savedStateHandle?.getStateFlow<T?>(key, null) ?: MutableStateFlow(null)
    }

    private fun removeSavedStateHandle(
        key: String,
        controller: NavController? = activeAuthenticatedController()
    ) {
        val savedStateHandle = controller?.currentBackStackEntry?.savedStateHandle
        savedStateHandle?.set(key, null)
    }

    override fun getCurrentBackStackEntryFlow(): Flow<NavBackStackEntry> {
        return requireNotNull(activeAuthenticatedController()).currentBackStackEntryFlow
    }

    override fun openSelectChainAsset(chainId: ChainId) {
        val bundle = AssetSelectFragment.getBundleFilterByChain(chainId)
        navigateInMainTabOrRoot(R.id.assetSelectFragment, bundle)
    }

    override fun openFilter(filtersToShowOrAll: Set<TransactionFilter>) {
        val bundle = TransactionHistoryFilterFragment.getBundle(filtersToShowOrAll)
        navigateInMainTabOrRoot(R.id.transactionHistoryFilterFragment, bundle)
    }

    override fun openSendConfirm(transferDraft: TransferDraft, phishingType: PhishingType?, overrides: Map<String, Any?>, transferComment: String?, skipEdValidation: Boolean) {
        val bundle = ConfirmSendFragment.getBundle(transferDraft, phishingType, overrides, transferComment, skipEdValidation)

        navigateInMainTabOrRoot(R.id.confirmSendFragment, bundle)
    }

    override fun openCrossChainSendConfirm(transferDraft: CrossChainTransferDraft, phishingType: PhishingType?) {
        val bundle = CrossChainConfirmFragment.getBundle(transferDraft, phishingType)

        navigateInMainTabOrRoot(R.id.confirmCrossChainSendFragment, bundle)
    }

    override fun openOperationSuccess(operationHash: String?, chainId: ChainId?) {
        openOperationSuccess(operationHash, chainId, null)
    }

    override fun openPolkaswapDisclaimerFromProfile() {
        val bundle = PolkaswapDisclaimerFragment.getBundle(
            R.id.profileFragment
        )

        navigateInMainTabOrRoot(R.id.polkaswapDisclaimerFragment, bundle)
    }

    override fun listenPolkaswapDisclaimerResultFlowFromMainScreen(): Flow<Boolean> {
        val controller = activeAuthenticatedController()
        val resultDestinationId = if (mainTabNavController != null) {
            R.id.polkaswapHubFragment
        } else {
            R.id.mainFragment
        }
        val currentEntry = runCatching { controller?.getBackStackEntry(resultDestinationId) }.getOrNull()
        val onResumeObserver = currentEntry?.lifecycle?.onResumeObserver()

        return (onResumeObserver?.asFlow() ?: emptyFlow()).map {
            if (currentEntry?.savedStateHandle?.contains(PolkaswapDisclaimerFragment.KEY_DISCLAIMER_READ_RESULT) == true) {
                val result =
                    currentEntry.savedStateHandle.get<Boolean?>(PolkaswapDisclaimerFragment.KEY_DISCLAIMER_READ_RESULT)
                currentEntry.savedStateHandle.set<Boolean?>(
                    PolkaswapDisclaimerFragment.KEY_DISCLAIMER_READ_RESULT,
                    null
                )
                result
            } else {
                null
            }
        }.filterNotNull()
    }

    override fun openPolkaswapDisclaimerFromMainScreen() {
        val resultDestinationId = mainTabNavController?.currentDestination?.id ?: R.id.mainFragment
        val bundle = PolkaswapDisclaimerFragment.getBundle(resultDestinationId)

        navigateInMainTabOrRoot(R.id.polkaswapDisclaimerFragment, bundle)
    }

    override fun openOperationSuccess(operationHash: String?, chainId: ChainId?, customMessage: String?, customTitle: String?) {
        val bundle = SuccessFragment.getBundle(operationHash, chainId, customMessage, customTitle)

        navigateInMainTabOrRoot(R.id.successSheetFragment, bundle)
    }

    @SuppressLint("RestrictedApi")
    override fun openOperationSuccessAndPopUpToNearestRelatedScreen(operationHash: String?, chainId: ChainId?, customMessage: String?, customTitle: String?) {
        val bundle = SuccessFragment.getBundle(operationHash, chainId, customMessage, customTitle)
        val targetController = activeAuthenticatedController() ?: return

        val latestAvailableWalletConnectRelatedDestinationId =
            targetController.currentBackStack.replayCache.firstOrNull()?.lastOrNull {
                it.destination.id == R.id.connectionsFragment ||
                it.destination.id == R.id.mainFragment
            }?.destination?.id

        val navOptions = latestAvailableWalletConnectRelatedDestinationId?.let { destinationId ->
            NavOptions.Builder()
                .setPopUpTo(destinationId, false)
                .build()
        }

        targetController.navigate(R.id.successSheetFragment, bundle, navOptions)
    }

    override fun finishSendFlow() {
        activeAuthenticatedController()?.let { controller ->
            controller.popBackStack()
            controller.popBackStack()
        }
    }

    override fun openTransferDetail(transaction: OperationParcelizeModel.Transfer, assetPayload: AssetPayload, chainExplorerType: Chain.Explorer.Type?) {
        val bundle = TransferDetailFragment.getBundle(transaction, assetPayload, chainExplorerType)

        navigateInMainTabOrRoot(R.id.transferDetailFragment, bundle)
    }

    override fun openRewardDetail(payload: RewardDetailsPayload) {
        val bundle = RewardDetailFragment.getBundle(payload)

        navigateInMainTabOrRoot(R.id.rewardDetailFragment, bundle)
    }

    override fun openExtrinsicDetail(payload: ExtrinsicDetailsPayload) {
        val bundle = ExtrinsicDetailFragment.getBundle(payload)

        navigateInMainTabOrRoot(R.id.extrinsicDetailFragment, bundle)
    }

    override fun openSwapDetail(operation: OperationParcelizeModel.Swap) {
        val bundle = SwapDetailFragment.getBundle(operation)

        navigateInMainTabOrRoot(R.id.swapDetailFragment, bundle)
    }

    override fun openNodes(chainId: ChainId) {
        navigateInMainTabOrRoot(R.id.nodesFragment, NodesFragment.getBundle(chainId))
    }

    override fun openClaimRewards(chainId: ChainId) {
        val args = ClaimRewardsFragment.getBundle(chainId)
        navigateInMainTabOrRoot(R.id.claimRewardsFragment, args)
    }

    override fun openLanguages() {
        navigateInMainTabOrRoot(R.id.languagesFragment)
    }

    override fun openReceive(assetPayload: AssetPayload) {
        val bundle = ReceiveFragment.getBundle(assetPayload)

        navigateInMainTabOrRoot(R.id.receiveFragment, bundle)
    }

    override fun openSignBeaconTransaction(payload: SubstrateSignerPayload, dAppMetadata: DAppMetadataModel) {
        navController?.navigate(R.id.signBeaconTransactionFragment, SignBeaconTransactionFragment.getBundle(payload, dAppMetadata))
    }

    override val beaconSignStatus: Flow<SignStatus>
        get() = navController!!.currentBackStackEntry!!.savedStateHandle
            .getLiveData<SignStatus>(SignBeaconTransactionFragment.SIGN_RESULT_KEY)
            .asFlow()

    override fun setBeaconSignStatus(status: SignStatus) {
        navController!!.previousBackStackEntry!!.savedStateHandle.set(SignBeaconTransactionFragment.SIGN_RESULT_KEY, status)
    }

    override fun returnToWallet() {
        // to achieve smooth animation
        postToUiThread {
            navController?.navigate(R.id.action_return_to_wallet)
        }
    }

    override fun openAccountDetails(metaAccountId: Long) {
        val extras = AccountDetailsDialog.getBundle(metaAccountId)

        navigateInMainTabOrRoot(R.id.accountDetailsDialog, extras)
    }

    override fun openEcosystemAccountsFragment(walletId: Long, type: WalletEcosystem) {
        val bundle = ChainAccountsDialog.getBundle(walletId, type)
        navigateInMainTabOrRoot(R.id.chainAccountsDialog, bundle)
    }

    override fun openBackupWalletScreen(metaAccountId: Long) {
        val extras = BackupWalletDialog.getBundle(metaAccountId)

        navigateInMainTabOrRoot(R.id.backupWalletDialog, extras)
    }

    override fun openRenameWallet(metaAccountId: Long, name: String?) {
        val extras = RenameAccountDialog.getBundle(metaAccountId, name)

        navigateInMainTabOrRoot(R.id.renameAccountDialog, extras)
    }

    override fun openNodeDetails(payload: NodeDetailsPayload) {
        navigateInMainTabOrRoot(R.id.nodeDetailsFragment, NodeDetailsFragment.getBundle(payload))
    }

    override fun trackReturnToAssetDetailsFromChainSelector(): Flow<Unit>? {
        return activeAuthenticatedController()?.currentBackStackEntryFlow?.filter {
            it.destination.id == R.id.assetDetailFragment
        }?.distinctUntilChanged()?.map { /* DO NOTHING */ }
    }

    override fun openAssetDetails(assetPayload: AssetPayload) {
        val bundle = BalanceDetailFragment.getBundle(assetPayload)

        navigateInMainTabOrRoot(R.id.balanceDetailFragment, bundle)
    }

    override fun openAssetDetailsAndPopUpToBalancesList(assetPayload: AssetPayload) {
        val bundle = BalanceDetailFragment.getBundle(assetPayload)

        val navOptions = NavOptions.Builder()
            .setPopUpTo(
                if (mainTabNavController != null) R.id.walletFragment else R.id.mainFragment,
                false
            )
            .build()

        navigateInMainTabOrRoot(R.id.balanceDetailFragment, bundle, navOptions)
    }

    override fun openAssetIntermediateDetails(assetPayload: AssetPayload) {
        val bundle = AssetDetailsFragment.getBundle(assetPayload)

        navigateInMainTabOrRoot(R.id.assetDetailFragment, bundle)
    }

    override fun openAssetIntermediateDetailsSort() {
        navigateInMainTabOrRoot(R.id.assetDetailSortFragment)
    }

    override fun openAddressHistory(chainId: ChainId) {
        val bundle = AddressHistoryFragment.getBundle(chainId)

        navigateInMainTabOrRoot(R.id.addressHistoryFragment, bundle)
    }

    override fun openAddressHistoryWithResult(chainId: ChainId): Flow<String> {
        val bundle = AddressHistoryFragment.getBundle(chainId)
        return openWithResult(
            destinationId = R.id.addressHistoryFragment,
            bundle = bundle,
            resultKey = AddressHistoryFragment.RESULT_ADDRESS
        )
    }

    override fun openCreateContact(chainId: ChainId?, address: String?) {
        val bundle = CreateContactFragment.getBundle(chainId, address)

        navigateInMainTabOrRoot(R.id.createContactFragment, bundle)
    }

    override fun openAddNode(chainId: ChainId) {
        navigateInMainTabOrRoot(R.id.addNodeFragment, AddNodeFragment.getBundle(chainId))
    }

    override fun getExportMnemonicDestination(metaId: Long, chainId: ChainId, isExportWallet: Boolean): DelayedNavigation {
        val extras = ExportMnemonicFragment.getBundle(metaId, chainId, isExportWallet)

        return NavComponentDelayedNavigation(R.id.exportMnemonicFragment, extras)
    }

    override fun openExportMnemonic(metaId: Long, chainId: ChainId): DelayedNavigation {
        return getExportMnemonicDestination(metaId, chainId, isExportWallet = false)
    }

    override fun getExportSeedDestination(metaId: Long, chainId: ChainId, isExportWallet: Boolean): DelayedNavigation {
        val extras = ExportSeedFragment.getBundle(metaId, chainId, isExportWallet)

        return NavComponentDelayedNavigation(R.id.exportSeedFragment, extras)
    }

    override fun openExportSeed(metaId: Long, chainId: ChainId): DelayedNavigation {
        return getExportSeedDestination(metaId, chainId, isExportWallet = false)
    }

    override fun openExportJsonPasswordDestination(metaId: Long, chainId: ChainId, isExportWallet: Boolean): DelayedNavigation {
        val extras = ExportJsonPasswordFragment.getBundle(metaId, chainId, isExportWallet)

        return NavComponentDelayedNavigation(R.id.exportJsonPasswordFragment, extras)
    }

    override fun openConfirmMnemonicOnExport(mnemonic: List<String>, metaId: Long) {
        val payload = ConfirmMnemonicPayload(
            mnemonic = mnemonic,
            metaId = metaId,
            createExtras = null,
            accountTypes = listOf(WalletEcosystem.Substrate)
        )
        val extras = ConfirmMnemonicFragment.getBundle(payload)

        navController?.navigate(R.id.action_exportMnemonicFragment_to_confirmExportMnemonicFragment, extras)
    }

    override fun openExportJsonPassword(metaId: Long, chainId: ChainId): DelayedNavigation {
        return openExportJsonPasswordDestination(metaId, chainId, isExportWallet = false)
    }

    override fun openExportJsonConfirm(payload: ExportJsonConfirmPayload) {
        val extras = ExportJsonConfirmFragment.getBundle(payload)

        navController?.navigate(R.id.action_exportJsonPasswordFragment_to_exportJsonConfirmFragment, extras)
    }

    override fun finishExportFlow() {
        navController?.navigate(R.id.action_return_to_wallet)
    }

    override fun openChangePinCode() {
        val action = PinCodeAction.Change
        val bundle = PincodeFragment.getPinCodeBundle(action)
        navigateInMainTabOrRoot(R.id.pincodeFragment, bundle)
    }

    override fun openBeacon(qrContent: String?) {
        qrContent?.let {
            navController?.navigate(R.id.actionOpenBeaconFragment, BeaconFragment.getBundle(it))
        } ?: navController?.navigate(R.id.actionOpenBeaconFragment)
    }

    override fun withPinCodeCheckRequired(
        delayedNavigation: DelayedNavigation,
        createMode: Boolean,
        pinCodeTitleRes: Int?
    ) {
        val action = if (createMode) {
            PinCodeAction.Create(delayedNavigation)
        } else {
            PinCodeAction.Check(delayedNavigation, ToolbarConfiguration(pinCodeTitleRes, true))
        }

        val extras = PincodeFragment.getPinCodeBundle(action)

        navController?.navigate(R.id.open_pincode_check, extras)
    }

    override fun openPincodeCheck() {
        val action = PinCodeAction.Check(null, ToolbarConfiguration())
        val bundle = PincodeFragment.getPinCodeBundle(action)
        navController?.navigate(R.id.open_pincode_check, bundle)
    }

    override fun openNavGraph() {
        val action = PinCodeAction.Check(null, ToolbarConfiguration())
        val bundle = PincodeFragment.getPinCodeBundle(action)
        navController?.navigate(R.id.root_nav_graph, bundle)
    }

    override fun openSelectWallet() {
        navigateInMainTabOrRoot(R.id.selectWalletFragment)
    }

    override fun openOptionsAddAccount(metaId: Long, type: WalletEcosystem) {
        val bundle = OptionsAddAccountFragment.getBundle(metaId, type)
        navigateInMainTabOrRoot(R.id.optionsAddAccountFragment, bundle)
    }

    override fun openEcosystemAccountsOptions(walletId: Long, type: WalletEcosystem) {
        val bundle = OptionsEcosystemAccountsFragment.getBundle(walletId, type)
        navigateInMainTabOrRoot(R.id.optionsEcosystemAccountsFragment, bundle)
    }

    override fun openOptionsSwitchNode(
        metaId: Long,
        chainId: ChainId,
        chainName: String
    ) {
        val bundle = OptionsSwitchNodeFragment.getBundle(metaId, chainId, chainName)
        navigateInMainTabOrRoot(R.id.optionsSwitchNodeFragment, bundle)
    }

    override fun openAlert(payload: AlertViewState) {
        openAlert(payload, emptyResultKey)
    }

    override fun openAlert(payload: AlertViewState, resultKey: String) {
        val currentDestination = requireNotNull(activeAuthenticatedController()?.currentDestination?.id)
        openAlert(payload, resultKey, currentDestination)
    }

    override fun openAlert(payload: AlertViewState, resultKey: String, resultDestinationId: Int) {
        val bundle = AlertFragment.getBundle(payload, resultKey, resultDestinationId)
        navigateInMainTabOrRoot(R.id.alertFragment, bundle)
    }

    override fun openSearchAssets() {
        navigateInMainTabOrRoot(R.id.searchAssetsFragment)
    }

    override fun openOptionsWallet(walletId: Long, allowDetails: Boolean) {
        val bundle = OptionsWalletFragment.getBundle(walletId, allowDetails)
        navigateInMainTabOrRoot(R.id.optionsWalletFragment, bundle)
    }

    override fun openFrozenTokens(payload: FrozenAssetPayload) {
        val bundle = FrozenTokensFragment.getBundle(payload)
        navigateInMainTabOrRoot(R.id.frozenTokensFragment, bundle)
    }

    override fun openLegacyCrowdloan(assetPayload: AssetPayload) {
        navigateInMainTabOrRoot(
            R.id.legacyCrowdloanFragment,
            LegacyCrowdloanFragment.getBundle(assetPayload)
        )
    }

    fun educationalStoriesCompleted() {
        navController?.previousBackStackEntry?.savedStateHandle?.set(StoryFragment.KEY_STORY, true)
        navController?.navigateUp()
    }

    override fun openExperimentalFeatures() {
        navigateInMainTabOrRoot(R.id.experimentalFragment)
    }

    override fun openSuccessFragment(avatar: Drawable) {
        SuccessfulFragment.avatar = avatar
        navController?.navigate(R.id.successFragment)
    }

    override fun openTransactionRawData(rawData: String) {
        val bundle = TransactionRawDataFragment.createBundle(rawData)
        navController?.navigate(R.id.transactionRawDataFragment, bundle)
    }

    override fun setWalletSelectorPayload(payload: WalletSelectorPayload) {
        activeAuthenticatedController()?.previousBackStackEntry?.savedStateHandle
            ?.set(WalletSelectorPayload::class.java.name, payload)
    }

    override fun openStartSelectValidators() {
        navigateInMainTabOrRoot(R.id.startSelectValidatorsFragment)
    }

    override fun openSelectValidators() {
        navigateInMainTabOrRoot(R.id.selectValidatorsFragment)
    }

    override fun openValidatorsSettings() {
        navigateInMainTabOrRoot(R.id.validatorsSettingsFragment)
    }

    override fun openConfirmSelectValidators() {
        navigateInMainTabOrRoot(R.id.confirmSelectValidatorsFragment)
    }

    override fun openPoolInfoOptions(poolInfo: PoolInfo) {
        navigateInMainTabOrRoot(
            R.id.poolOptionsInfoFragment,
            PoolInfoOptionsFragment.getBundle(poolInfo)
        )
    }

    override fun openEditPool() {
        navigateInMainTabOrRoot(R.id.editPoolFragment)
    }

    override fun openEditPoolConfirm() {
        navigateInMainTabOrRoot(R.id.editPoolConfirmFragment)
    }

    override val walletSelectorPayloadFlow: Flow<WalletSelectorPayload?>
        get() = activeAuthenticatedController()?.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<WalletSelectorPayload?>(WalletSelectorPayload::class.java.name)
            ?.asFlow() ?: emptyFlow()

    override fun setAlertResult(key: String, result: Result<*>, resultDestinationId: Int?) {
        val controller = activeAuthenticatedController()
        val resultBackStackEntry = resultDestinationId?.let { controller?.getBackStackEntry(it) }
            ?: controller?.previousBackStackEntry
        resultBackStackEntry?.savedStateHandle?.set(
            key,
            result
        )
    }

    override fun alertResultFlow(key: String): Flow<Result<Unit>> {
        val currentEntry = activeAuthenticatedController()?.currentBackStackEntry
        val onResumeObserver = currentEntry?.lifecycle?.onResumeObserver()

        return (onResumeObserver?.asFlow() ?: emptyFlow()).map {
            if (currentEntry?.savedStateHandle?.contains(key) == true) {
                val result = currentEntry.savedStateHandle.get<Result<Unit>?>(key)
                currentEntry.savedStateHandle.set<Result<Unit>?>(key, null)
                result
            } else {
                null
            }
        }.filterNotNull()
    }

    override fun listenAlertResultFlowFromStartSelectValidatorsScreen(key: String): Flow<Result<Unit>> {
        val currentEntry = activeAuthenticatedController()?.getBackStackEntry(R.id.startSelectValidatorsFragment)
        val onResumeObserver = currentEntry?.lifecycle?.onResumeObserver()

        return (onResumeObserver?.asFlow() ?: emptyFlow()).map {
            if (currentEntry?.savedStateHandle?.contains(key) == true) {
                val result = currentEntry.savedStateHandle.get<Result<Unit>?>(key)
                currentEntry.savedStateHandle.set<Result<Unit>?>(key, null)
                result
            } else {
                null
            }
        }.filterNotNull()
    }

    override fun listenAlertResultFlowFromStartChangeValidatorsScreen(key: String): Flow<Result<Unit>> {
        val currentEntry = activeAuthenticatedController()?.getBackStackEntry(R.id.startChangeValidatorsFragment)
        val onResumeObserver = currentEntry?.lifecycle?.onResumeObserver()

        return (onResumeObserver?.asFlow() ?: emptyFlow()).map {
            if (currentEntry?.savedStateHandle?.contains(key) == true) {
                val result = currentEntry.savedStateHandle.get<Result<Unit>?>(key)
                currentEntry.savedStateHandle.set<Result<Unit>?>(key, null)
                result
            } else {
                null
            }
        }.filterNotNull()
    }

    override fun listenAlertResultFlowFromNetworkIssuesScreen(key: String): Flow<Result<Unit>> {
        val currentEntry = activeAuthenticatedController()?.currentBackStackEntry
        val onResumeObserver = currentEntry?.lifecycle?.onResumeObserver()

        return (onResumeObserver?.asFlow() ?: emptyFlow()).map {
            if (currentEntry?.savedStateHandle?.contains(key) == true) {
                val result = currentEntry.savedStateHandle.get<Result<Unit>?>(key)
                currentEntry.savedStateHandle.set<Result<Unit>?>(key, null)
                result
            } else {
                null
            }
        }.filterNotNull()
    }

    override fun openAlertFromStartSelectValidatorsScreen(payload: AlertViewState, key: String) {
        openAlert(payload, key, R.id.startSelectValidatorsFragment)
    }

    override fun openAlertFromStartChangeValidatorsScreen(
        payload: AlertViewState,
        keyAlertResult: String
    ) {
        openAlert(payload, keyAlertResult, R.id.startChangeValidatorsFragment)
    }

    override fun openWebViewer(title: String, url: String) {
        navigateInMainTabOrRoot(R.id.webViewerFragment, WebViewerFragment.getBundle(title, url))
    }

    override fun openDappScreen(dapp: DappModel) {
        navController?.navigate(R.id.dappScreenFragment, DappScreenFragment.getBundle(dapp))
    }

    override fun setChainSelectorPayload(chainId: ChainId?) {
        activeAuthenticatedController()?.previousBackStackEntry?.savedStateHandle
            ?.set(ChainSelectFragment.KEY_SELECTED_CHAIN_ID, chainId)
    }

    override val chainSelectorPayloadFlow: Flow<ChainId?>
        get() = activeAuthenticatedController()?.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<ChainId?>(ChainSelectFragment.KEY_SELECTED_CHAIN_ID)
            ?.asFlow() ?: emptyFlow()

    override fun openPoolFullUnstakeDepositorAlertFragment(amount: String) {
        val bundle = PoolFullUnstakeDepositorAlertFragment.getBundle(amount)
        navigateInMainTabOrRoot(R.id.poolFullUnstakeDepositorAlertFragment, bundle)
    }

    override fun openContactsWithResult(chainId: ChainId): Flow<String> {
        val bundle = ContactsFragment.getBundle(chainId)
        return openWithResult(
            destinationId = R.id.contactsFragment,
            bundle = bundle,
            resultKey = ContactsFragment.RESULT_CONTACT
        )
    }

    override fun openNftCollection(selectedAssetId: ChainId, contractAddress: String, collectionName: String) {
        val bundle = NFTFlowFragment.getCollectionDetailsBundle(selectedAssetId, contractAddress, collectionName)
        navigateInMainTabOrRoot(R.id.nftFlowFragment, bundle)
    }

    override fun openNFTFilter() {
        navigateInMainTabOrRoot(R.id.nftFiltersFragment)
    }

    override fun openManageAssets() {
        navigateInMainTabOrRoot(R.id.manageAssetsFragment)
    }

    override fun openServiceScreen() {
        navigateInMainTabOrRoot(R.id.serviceFragment)
    }

    override fun openScoreDetailsScreen(metaId: Long) {
        navigateInMainTabOrRoot(R.id.scoreDetailsFragment, ScoreDetailsFragment.getBundle(metaId))
    }

    override fun openPools() {
        navigateInMainTabOrRoot(R.id.poolsFlowFragment)
    }

    override fun openDemeterFarming() {
        navigateInMainTabOrRoot(R.id.demeterFarmingFragment)
    }

    override fun openPolkamarkt(marketId: String?) {
        navigateInMainTabOrRoot(R.id.polkamarktFragment, PolkamarktFragment.bundle(marketId))
    }

    override fun openRiskDisclaimer() {
        openPolkaswapDisclaimerFromMainScreen()
    }

    override fun openTonConnectionInfo(dappItem: DappModel) {
        val bundle = TonConnectionInfoFragment.getBundle(dappItem)

        navigateInMainTabOrRoot(R.id.tonConnectionInfo, bundle)
    }

    override suspend fun openTonSignRequestWithResult(
        dapp: DappModel,
        method: String,
        signRequest: TonConnectSignRequest
    ): Result<Pair<String, String>> {
        val bundle = TonSignRequestFragment.getBundle(dapp, method, signRequest)

        val result = openAndWaitResult<Result<Pair<String, String>>>(
            destinationId = R.id.tonSignRequestFragment,
            bundle = bundle,
            resultKey = TonSignRequestFragment.TON_SIGN_RESULT_KEY
        )
        return result
    }

    override suspend fun openTonConnectionAndWaitForResult(app: AppEntity, proofPayload: String?): JSONObject {
        val bundle = TonConnectionDetailsFragment.getBundle(app, proofPayload)

        val result = openAndWaitResult<String>(
            destinationId = R.id.tonConnectionDetails,
            bundle = bundle,
            resultKey = TonConnectionDetailsFragment.TON_CONNECT_RESULT_KEY
        )
        return JSONObject(result)
    }
}
