package jp.co.soramitsu.app.root.navigation

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
import androidx.navigation.NavOptions
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateSignerPayload
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.account.api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.account.details.AccountDetailsFragment
import jp.co.soramitsu.account.impl.presentation.account.export.WalletExportFragment
import jp.co.soramitsu.account.impl.presentation.account.exportaccounts.AccountsForExportFragment
import jp.co.soramitsu.account.impl.presentation.account.list.AccountChosenNavDirection
import jp.co.soramitsu.account.impl.presentation.account.list.AccountListFragment
import jp.co.soramitsu.account.impl.presentation.experimental.SuccessfulFragment
import jp.co.soramitsu.account.impl.presentation.exporting.json.confirm.ExportJsonConfirmFragment
import jp.co.soramitsu.account.impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.account.impl.presentation.exporting.json.password.ExportJsonPasswordFragment
import jp.co.soramitsu.account.impl.presentation.exporting.mnemonic.ExportMnemonicFragment
import jp.co.soramitsu.account.impl.presentation.exporting.seed.ExportSeedFragment
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountFragment
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.BackupMnemonicFragment
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicFragment
import jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.account.impl.presentation.node.add.AddNodeFragment
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsFragment
import jp.co.soramitsu.account.impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.account.impl.presentation.node.list.NodesFragment
import jp.co.soramitsu.account.impl.presentation.optionsaddaccount.OptionsAddAccountFragment
import jp.co.soramitsu.account.impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.account.impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.account.impl.presentation.pincode.ToolbarConfiguration
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.presentation.AlertFragment
import jp.co.soramitsu.app.root.presentation.RootRouter
import jp.co.soramitsu.app.root.presentation.WebViewerFragment
import jp.co.soramitsu.app.root.presentation.emptyResultKey
import jp.co.soramitsu.app.root.presentation.stories.StoryFragment
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.utils.postToUiThread
import jp.co.soramitsu.common.view.onResumeObserver
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.crowdloan.impl.presentation.CrowdloanRouter
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.confirm.ConfirmContributeFragment
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeFragment
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.CrowdloanContributeFragment
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel.ContributePayload
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.impl.presentation.swap_preview.SwapPreviewFragment
import jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens.SwapTokensFragment
import jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings.TransactionSettingsFragment
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import jp.co.soramitsu.splash.SplashRouter
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
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
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.settings.CustomValidatorsSettingsFragment
import jp.co.soramitsu.staking.impl.presentation.validators.details.CollatorDetailsFragment
import jp.co.soramitsu.staking.impl.presentation.validators.details.ValidatorDetailsFragment
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.success.presentation.SuccessFragment
import jp.co.soramitsu.success.presentation.SuccessRouter
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.domain.beacon.SignStatus
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.addressbook.CreateContactFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.assetselector.AssetSelectFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.BalanceDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenTokensFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet.OptionsWalletFragment
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectorFragment
import jp.co.soramitsu.wallet.impl.presentation.beacon.main.BeaconFragment
import jp.co.soramitsu.wallet.impl.presentation.beacon.main.DAppMetadataModel
import jp.co.soramitsu.wallet.impl.presentation.beacon.sign.SignBeaconTransactionFragment
import jp.co.soramitsu.wallet.impl.presentation.beacon.sign.TransactionRawDataFragment
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.CrossChainConfirmFragment
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup.CrossChainSetupFragment
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.wallet_type.SelectWalletTypeFragment
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.wallet_type.WalletType
import jp.co.soramitsu.wallet.impl.presentation.history.AddressHistoryFragment
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.receive.ReceiveFragment
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.ConfirmSendFragment
import jp.co.soramitsu.wallet.impl.presentation.send.setup.SendSetupFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap.SwapDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer.TransferDetailFragment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import jp.co.soramitsu.common.utils.combine as combineLiveData
import kotlinx.parcelize.Parcelize
import kotlin.coroutines.coroutineContext

@Parcelize
class NavComponentDelayedNavigation(val globalActionId: Int, val extras: Bundle? = null) : DelayedNavigation

class Navigator :
    SplashRouter,
    OnboardingRouter,
    AccountRouter,
    WalletRouter,
    RootRouter,
    StakingRouter,
    CrowdloanRouter,
    PolkaswapRouter,
    SuccessRouter,
    SoraCardRouter {

    private var navController: NavController? = null
    private var activity: AppCompatActivity? = null

    fun attach(navController: NavController, activity: AppCompatActivity) {
        this.navController = navController
        this.activity = activity
    }

    fun detach() {
        navController = null
        activity = null
    }

    override fun openAddFirstAccount() {
        navController?.navigate(R.id.action_to_onboarding, WelcomeFragment.getBundle(false))
    }

    override fun openInitialCheckPincode() {
        val action = PinCodeAction.Check(NavComponentDelayedNavigation(R.id.action_open_main), ToolbarConfiguration())
        val bundle = PincodeFragment.getPinCodeBundle(action)
        navController?.navigateSafe(R.id.action_splash_to_pin, bundle)
    }

    private fun NavController.navigateSafe(@IdRes resId: Int, args: Bundle?) {
        runCatching { navigate(resId, args) }
    }

    override fun openCreateAccountFromOnboarding() {
        navController?.navigate(R.id.action_welcomeFragment_to_createAccountFragment)
    }

    override fun openCreateAccountFromWallet() {
        val request = NavDeepLinkRequest.Builder
            .fromUri("fearless://onboarding/createAccountFragment".toUri())
            .build()
        navController?.navigate(request)
    }

    override fun openImportAccountScreenFromWallet(blockChainType: Int) {
        val request = NavDeepLinkRequest.Builder
            .fromUri("fearless://onboarding/importAccountFragment/$blockChainType".toUri())
            .build()
        navController?.navigate(request)
    }

    override fun openCreateAccountSkipWelcome(payload: ChainAccountCreatePayload) {
        val bundle = BackupMnemonicFragment.getBundle("", payload)
        navController?.navigate(R.id.action_welcomeFragment_to_backupMnemonicFragment, bundle)
    }

    override fun openImportAccountSkipWelcome(payload: ChainAccountCreatePayload) {
        val bundle = ImportAccountFragment.getBundle(payload)
        navController?.navigate(
            R.id.importAction,
            bundle,
            NavOptions.Builder().setPopUpTo(R.id.welcomeFragment, true).build()
        )
    }

    override fun popOutOfSend() {
        navController?.popBackStack(R.id.sendSetupFragment, true)
    }

    override fun openOnboardingNavGraph(chainId: ChainId, metaId: Long, isImport: Boolean) {
        val bundle = WelcomeFragment.getBundle(
            displayBack = true,
            chainAccountData = ChainAccountCreatePayload(chainId, metaId, isImport)
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

        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.pincodeFragment, true)
            .setEnterAnim(R.animator.fragment_open_enter)
            .setExitAnim(R.animator.fragment_open_exit)
            .setPopEnterAnim(R.animator.fragment_close_enter)
            .setPopExitAnim(R.animator.fragment_close_exit)
            .build()

        navController?.navigate(delayedNavigation.globalActionId, delayedNavigation.extras, navOptions)
    }

    override fun openCreatePincode() {
        val bundle = buildCreatePinBundle()

        when (navController?.currentDestination?.id) {
            R.id.splashFragment -> navController?.navigate(R.id.action_splash_to_pin, bundle)
            R.id.importAccountFragment -> navController?.navigate(R.id.action_importAccountFragment_to_pincodeFragment, bundle)
            R.id.confirmMnemonicFragment -> navController?.navigate(R.id.action_confirmMnemonicFragment_to_pincodeFragment, bundle)
        }
    }

    override fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload) {
        val bundle = ConfirmMnemonicFragment.getBundle(confirmMnemonicPayload)

        navController?.navigate(
            R.id.action_backupMnemonicFragment_to_confirmMnemonicFragment,
            bundle
        )
    }

    override fun openAboutScreen() {
        navController?.navigate(R.id.action_profileFragment_to_aboutFragment)
    }

    override fun openImportAccountScreen(blockChainType: Int) {
        val arguments = ImportAccountFragment.getBundle(blockChainType)
        navController?.navigate(R.id.importAction, arguments)
    }

    override fun openMnemonicScreen(accountName: String, payload: ChainAccountCreatePayload?) {
        val bundle = BackupMnemonicFragment.getBundle(accountName, payload)
        navController?.navigate(R.id.action_createAccountFragment_to_backupMnemonicFragment, bundle)
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
        navController?.navigate(R.id.action_mainFragment_to_setupStakingFragment)
    }

    override fun openStartChangeValidators() {
        navController?.navigate(R.id.openStartChangeValidatorsFragment)
    }

    override fun openStartChangeCollators() {
        navController?.navigate(R.id.openStartChangeCollatorsFragment)
    }

    override fun openStory(story: StoryGroupModel) {
        navController?.navigate(R.id.open_staking_story, StoryFragment.getBundle(story))
    }

    override fun openPayouts() {
        navController?.navigate(R.id.action_mainFragment_to_payoutsListFragment)
    }

    override fun openPayoutDetails(payout: PendingPayoutParcelable) {
        navController?.navigate(R.id.action_payoutsListFragment_to_payoutDetailsFragment, PayoutDetailsFragment.getBundle(payout))
    }

    override fun openConfirmPayout(payload: ConfirmPayoutPayload) {
        navController?.navigate(R.id.action_open_confirm_payout, ConfirmPayoutFragment.getBundle(payload))
    }

    override fun openStakingBalance(collatorAddress: String?) {
        val bundle = collatorAddress?.let { StakingBalanceFragment.getBundle(it) }
        navController?.navigate(R.id.action_mainFragment_to_stakingBalanceFragment, bundle)
    }

    override fun openBondMore(payload: SelectBondMorePayload) {
        navController?.navigate(R.id.action_open_selectBondMoreFragment, SelectBondMoreFragment.getBundle(payload))
    }

    override fun openConfirmBondMore(payload: ConfirmBondMorePayload) {
        navController?.navigate(R.id.action_selectBondMoreFragment_to_confirmBondMoreFragment, ConfirmBondMoreFragment.getBundle(payload))
    }

    override fun returnToStakingBalance() {
        navController?.navigate(R.id.action_return_to_staking_balance)
    }

    override fun returnToManagePoolStake() {
        navController?.navigate(R.id.action_return_to_pool_staking_balance)
    }

    override fun openCreatePoolSetup() {
        navController?.navigate(R.id.createPoolSetupFragment)
    }

    override fun openCreatePoolConfirm() {
        navController?.navigate(R.id.confirmCreatePoolFragment)
    }

    override fun openWalletSelector(tag: String) {
        navController?.navigate(R.id.walletSelectorFragment, WalletSelectorFragment.buildArguments(tag))
    }

    override fun openSelectUnbond(payload: SelectUnbondPayload) {
        navController?.navigate(R.id.action_stakingBalanceFragment_to_selectUnbondFragment, SelectUnbondFragment.getBundle(payload))
    }

    override fun openConfirmUnbond(payload: ConfirmUnbondPayload) {
        navController?.navigate(R.id.action_selectUnbondFragment_to_confirmUnbondFragment, ConfirmUnbondFragment.getBundle(payload))
    }

    override fun openRedeem(payload: RedeemPayload) {
        navController?.navigate(R.id.action_open_redeemFragment, RedeemFragment.getBundle(payload))
    }

    override fun openConfirmRebond(payload: ConfirmRebondPayload) {
        navController?.navigate(R.id.action_open_confirm_rebond, ConfirmRebondFragment.getBundle(payload))
    }

    override fun openContribute(payload: ContributePayload) {
        navController?.navigate(R.id.action_mainFragment_to_crowdloanContributeFragment, CrowdloanContributeFragment.getBundle(payload))
    }

    override val customBonusFlow: Flow<BonusPayload?>
        get() = navController!!.currentBackStackEntry!!.savedStateHandle
            .getLiveData<BonusPayload?>(CrowdloanContributeFragment.KEY_BONUS_LIVE_DATA)
            .asFlow()

    override val latestCustomBonus: BonusPayload?
        get() = navController!!.currentBackStackEntry!!.savedStateHandle
            .get(CrowdloanContributeFragment.KEY_BONUS_LIVE_DATA)

    override fun openMoonbeamContribute(payload: CustomContributePayload) {
        navController?.navigate(R.id.action_mainFragment_to_customContributeFragment, CustomContributeFragment.getBundle(payload))
    }

    override fun openMoonbeamConfirmContribute(payload: ConfirmContributePayload) {
        navController?.navigate(R.id.action_customContributeFragment_to_confirmContributeFragment, ConfirmContributeFragment.getBundle(payload))
    }

    override fun openCustomContribute(payload: CustomContributePayload) {
        navController?.navigate(R.id.action_crowdloanContributeFragment_to_customContributeFragment, CustomContributeFragment.getBundle(payload))
    }

    override fun setCustomBonus(payload: BonusPayload) {
        navController!!.previousBackStackEntry!!.savedStateHandle.set(CrowdloanContributeFragment.KEY_BONUS_LIVE_DATA, payload)
    }

    override fun openConfirmContribute(payload: ConfirmContributePayload) {
        navController?.navigate(R.id.action_crowdloanContributeFragment_to_confirmContributeFragment, ConfirmContributeFragment.getBundle(payload))
    }

    override fun back() {
        val popped = navController!!.popBackStack()

        if (!popped) {
            activity!!.finish()
        }
    }

    override fun backWithResult(vararg results: Pair<String, Any?>) {
        val savedStateHandle = navController?.previousBackStackEntry?.savedStateHandle

        if (savedStateHandle != null) {
            results.forEach { (key, value) ->
                savedStateHandle[key] = value
            }
        }
        back()
    }

    override fun openTransactionSettingsDialog(initialSettings: TransactionSettingsModel) {
        val bundle = TransactionSettingsFragment.getBundle(initialSettings)
        navController?.navigate(R.id.transactionSettingsFragment, bundle)
    }

    override fun openSwapPreviewDialog(swapDetailsViewState: SwapDetailsViewState, parcelModel: SwapDetailsParcelModel) {
        val bundle = SwapPreviewFragment.getBundle(swapDetailsViewState, parcelModel)

        navController?.navigate(R.id.swapPreviewFragment, bundle)
    }

    override fun openSelectMarketDialog() {
        navController?.navigate(R.id.selectMarketFragment)
    }

    override fun openCustomRebond() {
        navController?.navigate(R.id.action_stakingBalanceFragment_to_customRebondFragment)
    }

    override fun openCurrentValidators() {
        navController?.navigate(R.id.action_mainFragment_to_currentValidatorsFragment)
    }

    override fun returnToCurrentValidators() {
        navController?.navigate(R.id.action_confirmStakingFragment_back_to_currentValidatorsFragment)
    }

    override fun openChangeRewardDestination() {
        navController?.navigate(R.id.action_mainFragment_to_selectRewardDestinationFragment)
    }

    override fun openConfirmRewardDestination(payload: ConfirmRewardDestinationPayload) {
        navController?.navigate(
            R.id.action_selectRewardDestinationFragment_to_confirmRewardDestinationFragment,
            ConfirmRewardDestinationFragment.getBundle(payload)
        )
    }

    override fun openStakingPoolWelcome() {
        navController?.navigate(R.id.action_mainFragment_to_startStakingPoolFragment)
    }

    override fun openSetupStakingPool() {
        navController?.navigate(R.id.setupStakingPoolFragment)
    }

    override fun openConfirmJoinPool() {
        navController?.navigate(R.id.confirmJoinPoolFragment)
    }

    override fun openPoolInfo(poolInfo: PoolInfo) {
        navController?.navigate(R.id.poolInfoFragment, PoolInfoFragment.getBundle(poolInfo))
    }

    override fun openManagePoolStake() {
        navController?.navigate(R.id.managePoolStakeFragment)
    }

    override fun openPoolBondMore() {
        navController?.navigate(R.id.poolBondMoreFragment)
    }

    override fun openPoolClaim() {
        navController?.navigate(R.id.poolClaimFragment)
    }

    override fun openPoolRedeem() {
        navController?.navigate(R.id.poolRedeemFragment)
    }

    override fun openPoolUnstake() {
        navController?.navigate(R.id.poolUnstakeFragment)
    }

    override fun openPoolConfirmBondMore() {
        navController?.navigate(R.id.poolConfirmBondMoreFragment)
    }

    override fun openPoolConfirmClaim() {
        navController?.navigate(R.id.poolConfirmClaimFragment)
    }

    override fun openPoolConfirmRedeem() {
        navController?.navigate(R.id.poolConfirmRedeemFragment)
    }

    override fun openPoolConfirmUnstake() {
        navController?.navigate(R.id.poolConfirmUnstakeFragment)
    }

    override val currentStackEntryLifecycle: Lifecycle
        get() = navController!!.currentBackStackEntry!!.getLifecycle()

    override fun openControllerAccount() {
        navController?.navigate(R.id.action_stakingBalanceFragment_to_setControllerAccountFragment)
    }

    override fun openConfirmSetController(payload: ConfirmSetControllerPayload) {
        navController?.navigate(
            R.id.action_stakingSetControllerAccountFragment_to_confirmSetControllerAccountFragment,
            ConfirmSetControllerFragment.getBundle(payload)
        )
    }

    override fun openRecommendedCollators() {
        navController?.navigate(R.id.action_startChangeCollatorsFragment_to_recommendedCollatorsFragment)
    }

    override fun openSelectCustomCollators() {
        navController?.navigate(R.id.action_startChangeCollatorsFragment_to_selectCustomCollatorsFragment)
    }

    override fun openSelectPool() {
        navController?.navigate(R.id.selectPoolFramgent)
    }

    override fun openRecommendedValidators() {
        navController?.navigate(R.id.action_startChangeValidatorsFragment_to_recommendedValidatorsFragment)
    }

    override fun openSelectCustomValidators() {
        navController?.navigate(R.id.action_startChangeValidatorsFragment_to_selectCustomValidatorsFragment)
    }

    override fun openCustomValidatorsSettingsFromValidator() {
        val bundle = CustomValidatorsSettingsFragment.getBundle(Asset.StakingType.RELAYCHAIN)
        navController?.navigate(R.id.action_selectCustomValidatorsFragment_to_settingsCustomValidatorsFragment, bundle)
    }

    override fun openCustomValidatorsSettingsFromCollator() {
        val bundle = CustomValidatorsSettingsFragment.getBundle(Asset.StakingType.PARACHAIN)
        navController?.navigate(R.id.action_selectCustomCollatorsFragment_to_settingsCustomValidatorsFragment, bundle)
    }

    override fun openSearchCustomValidators() {
        navController?.navigate(R.id.action_selectCustomValidatorsFragment_to_searchCustomValidatorsFragment)
    }

    override fun openSearchCustomCollators() {
        navController?.navigate(R.id.action_selectCustomCollatorsFragment_to_searchCustomValidatorsFragment)
    }

    override fun openReviewCustomValidators() {
        navController?.navigate(R.id.action_selectCustomValidatorsFragment_to_reviewCustomValidatorsFragment)
    }

    override fun openConfirmStaking() {
        navController?.navigate(R.id.openConfirmStakingFragment)
    }

    override fun openConfirmNominations() {
        navController?.navigate(R.id.action_confirmStakingFragment_to_confirmNominationsFragment)
    }

    override fun returnToMain() {
        navController?.navigate(R.id.back_to_main)
    }

    override fun closeSwap() {
        navController?.navigate(R.id.close_swap)
    }

    override fun openValidatorDetails(validatorDetails: ValidatorDetailsParcelModel) {
        navController?.navigate(R.id.validatorDetailsFragment, ValidatorDetailsFragment.getBundle(validatorDetails))
    }

    override fun openSelectedValidators() {
        navController?.navigate(R.id.selectedValidatorsFragment)
    }

    override fun openCollatorDetails(collatorDetails: CollatorDetailsParcelModel) {
        navController?.navigate(R.id.open_collator_details, CollatorDetailsFragment.getBundle(collatorDetails))
    }

    override fun openSend(assetPayload: AssetPayload?, initialSendToAddress: String?, currencyId: String?) {
        val bundle = SendSetupFragment.getBundle(assetPayload, initialSendToAddress, currencyId)

        navController?.navigate(R.id.sendSetupFragment, bundle)
    }

    override fun openCrossChainSend(assetPayload: AssetPayload?, initialSendToAddress: String?, currencyId: String?) {
        val bundle = CrossChainSetupFragment.getBundle(assetPayload, initialSendToAddress, currencyId)

        navController?.navigate(R.id.crossChainFragment, bundle)
    }

    override fun openSelectWalletTypeWithResult(): Flow<WalletType> {
        return openWithResult(
            destinationId = R.id.selectWalletTypeFragment,
            resultKey = SelectWalletTypeFragment.KEY_WALLET_TYPE
        )
    }

    private fun <T> openWithResult(
        @IdRes destinationId: Int,
        resultKey: String
    ): Flow<T> {
        val resultFlow = observeResultInternal<T>(resultKey)
        val backStackEntryFlow = getCurrentBackStackEntryFlow()
        return combine(resultFlow, backStackEntryFlow) { result, backStackEntry ->
            Pair(result, backStackEntry)
        }
            .onStart { navController?.navigate(destinationId) }
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
            .onEach { removeSavedStateHandle(resultKey) }
    }

    override fun openSwapTokensScreen(chainId: String, assetIdFrom: String?, assetIdTo: String?) {
        val bundle = SwapTokensFragment.getBundle(chainId, assetIdFrom, assetIdTo)

        navController?.navigate(R.id.swapTokensFragment, bundle)
    }

    override fun showBuyCrypto() {
        navController?.navigate(R.id.buyCryptoFragment)
    }

    override fun openSelectChain(
        assetId: String,
        chainId: ChainId?,
        chooserMode: Boolean,
        isSelectAsset: Boolean
    ) {
        val bundle = ChainSelectFragment.getBundle(
            assetId = assetId,
            chainId = chainId,
            chooserMode = chooserMode,
            isSelectAsset = isSelectAsset
        )
        navController?.navigate(R.id.chainSelectFragment, bundle)
    }

    override fun openSelectChain(
        selectedChainId: ChainId?,
        filterChainIds: List<ChainId>?,
        chooserMode: Boolean,
        currencyId: String?,
        showAllChains: Boolean,
        isSelectAsset: Boolean
    ) {
        val bundle = ChainSelectFragment.getBundle(
            selectedChainId,
            filterChainIds,
            chooserMode,
            currencyId,
            showAllChains,
            isSelectAsset
        )
        navController?.navigate(R.id.chainSelectFragment, bundle)
    }

    override fun openSelectChainForXcm(
        selectedChainId: ChainId?,
        xcmChainType: XcmChainType,
        selectedOriginalChainId: String?
    ) {
        val bundle = ChainSelectFragment.getBundleForXcmChains(
            selectedChainId = selectedChainId,
            xcmChainType = xcmChainType,
            xcmSelectedOriginalChainId = selectedOriginalChainId
        )
        navController?.navigate(R.id.chainSelectFragment, bundle)
    }

    override fun openSelectAsset(selectedAssetId: String) {
        val bundle = AssetSelectFragment.getBundle(selectedAssetId)
        navController?.navigate(R.id.assetSelectFragment, bundle)
    }

    override fun openSelectAsset(chainId: ChainId, selectedAssetId: String?, isFilterXcmAssets: Boolean) {
        val bundle = AssetSelectFragment.getBundle(chainId, selectedAssetId, isFilterXcmAssets)
        navController?.navigate(R.id.assetSelectFragment, bundle)
    }

    override fun openSelectAsset(chainId: ChainId, selectedAssetId: String?, excludeAssetId: String?) {
        val bundle = AssetSelectFragment.getBundle(chainId, selectedAssetId, excludeAssetId)
        navController?.navigate(R.id.assetSelectFragment, bundle)
    }

    override fun <T> observeResult(key: String): Flow<T> {
        return observeResultInternal<T>(key)
            .onStart { removeSavedStateHandle(key) }
            .onCompletion { removeSavedStateHandle(key) }
            .filter { it != null } as Flow<T>
    }

    private fun <T> observeResultInternal(key: String): StateFlow<T?> {
        val savedStateHandle = navController?.currentBackStackEntry?.savedStateHandle
        return savedStateHandle?.getStateFlow<T?>(key, null) ?: MutableStateFlow(null)
    }

    private fun removeSavedStateHandle(key: String) {
        val savedStateHandle = navController?.currentBackStackEntry?.savedStateHandle
        savedStateHandle?.set(key, null)
    }

    override fun getCurrentBackStackEntryFlow(): Flow<NavBackStackEntry> {
        return navController!!.currentBackStackEntryFlow
    }

    override fun openSelectChainAsset(chainId: ChainId) {
        val bundle = AssetSelectFragment.getBundleFilterByChain(chainId)
        navController?.navigate(R.id.assetSelectFragment, bundle)
    }

    override fun openFilter() {
        navController?.navigate(R.id.action_mainFragment_to_filterFragment)
    }

    override fun openSendConfirm(transferDraft: TransferDraft, phishingType: PhishingType?) {
        val bundle = ConfirmSendFragment.getBundle(transferDraft, phishingType)

        navController?.navigate(R.id.confirmSendFragment, bundle)
    }

    override fun openCrossChainSendConfirm(transferDraft: CrossChainTransferDraft, phishingType: PhishingType?) {
        val bundle = CrossChainConfirmFragment.getBundle(transferDraft, phishingType)

        navController?.navigate(R.id.confirmCrossChainSendFragment, bundle)
    }

    override fun openOperationSuccess(operationHash: String?, chainId: ChainId) {
        openOperationSuccess(operationHash, chainId, null)
    }

    override fun openPolkaswapDisclaimer() {
        navController?.navigate(R.id.polkaswapDisclaimerFragment)
    }

    override fun openOperationSuccess(operationHash: String?, chainId: ChainId, customMessage: String?) {
        val bundle = SuccessFragment.getBundle(operationHash, chainId, customMessage)

        navController?.navigate(R.id.successSheetFragment, bundle)
    }

    override fun finishSendFlow() {
        navController?.navigate(R.id.finish_send_flow)
    }

    override fun openTransferDetail(transaction: OperationParcelizeModel.Transfer, assetPayload: AssetPayload) {
        val bundle = TransferDetailFragment.getBundle(transaction, assetPayload)

        navController?.navigate(R.id.open_transfer_detail, bundle)
    }

    override fun openRewardDetail(payload: RewardDetailsPayload) {
        val bundle = RewardDetailFragment.getBundle(payload)

        navController?.navigate(R.id.open_reward_detail, bundle)
    }

    override fun openExtrinsicDetail(payload: ExtrinsicDetailsPayload) {
        val bundle = ExtrinsicDetailFragment.getBundle(payload)

        navController?.navigate(R.id.open_extrinsic_detail, bundle)
    }

    override fun openSwapDetail(operation: OperationParcelizeModel.Swap) {
        val bundle = SwapDetailFragment.getBundle(operation)

        navController?.navigate(R.id.swapDetailFragment, bundle)
    }

    override fun openWallets(accountChosenNavDirection: AccountChosenNavDirection) {
        navController?.navigate(R.id.action_open_accounts, AccountListFragment.getBundle(accountChosenNavDirection))
    }

    override fun openNodes(chainId: ChainId) {
        navController?.navigate(R.id.action_open_nodesFragment, NodesFragment.getBundle(chainId))
    }

    override fun openLanguages() {
        navController?.navigate(R.id.action_mainFragment_to_languagesFragment)
    }

    override fun openAddAccount() {
        navController?.navigate(R.id.action_open_onboarding, WelcomeFragment.getBundle(true))
    }

    override fun openChangeAccountFromWallet() {
        openWallets(AccountChosenNavDirection.BACK)
    }

    override fun openChangeAccountFromStaking() {
        openWallets(AccountChosenNavDirection.BACK)
    }

    override fun openReceive(assetPayload: AssetPayload) {
        val bundle = ReceiveFragment.getBundle(assetPayload)

        navController?.navigate(R.id.action_open_receive, bundle)
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
        val extras = AccountDetailsFragment.getBundle(metaAccountId)

        navController?.navigate(R.id.action_open_accountDetailsFragment, extras)
    }

    override fun openExportWallet(metaAccountId: Long) {
        val extras = WalletExportFragment.getBundle(metaAccountId)

        navController?.navigate(R.id.action_open_walletExportFragment, extras)
    }

    override fun openAccountsForExport(metaId: Long, from: AccountInChain.From) {
        val extras = AccountsForExportFragment.getBundle(metaId, from)

        navController?.navigate(R.id.action_open_accountsForExportFragment, extras)
    }

    override fun openEditAccounts() {
        navController?.navigate(R.id.action_accountsFragment_to_editAccountsFragment)
    }

    override fun backToMainScreen() {
        navController?.navigate(R.id.action_editAccountsFragment_to_mainFragment)
    }

    override fun openNodeDetails(payload: NodeDetailsPayload) {
        navController?.navigate(R.id.action_nodesFragment_to_nodeDetailsFragment, NodeDetailsFragment.getBundle(payload))
    }

    override fun openAssetDetails(assetPayload: AssetPayload) {
        val bundle = BalanceDetailFragment.getBundle(assetPayload)

        navController?.navigate(R.id.action_mainFragment_to_balanceDetailFragment, bundle)
    }

    override fun openAddressHistory(chainId: ChainId) {
        val bundle = AddressHistoryFragment.getBundle(chainId)

        navController?.navigate(R.id.addressHistoryFragment, bundle)
    }

    override fun openCreateContact(chainId: ChainId?, address: String?) {
        val bundle = CreateContactFragment.getBundle(chainId, address)

        navController?.navigate(R.id.createContactFragment, bundle)
    }

    override fun openAddNode(chainId: ChainId) {
        navController?.navigate(R.id.action_nodesFragment_to_addNodeFragment, AddNodeFragment.getBundle(chainId))
    }

    override fun openExportMnemonic(metaId: Long, chainId: ChainId, isExportWallet: Boolean): DelayedNavigation {
        val extras = ExportMnemonicFragment.getBundle(metaId, chainId, isExportWallet)

        return NavComponentDelayedNavigation(R.id.action_export_mnemonic, extras)
    }

    override fun openExportMnemonic(metaId: Long, chainId: ChainId): DelayedNavigation {
        return openExportMnemonic(metaId, chainId, isExportWallet = false)
    }

    override fun openExportSeed(metaId: Long, chainId: ChainId, isExportWallet: Boolean): DelayedNavigation {
        val extras = ExportSeedFragment.getBundle(metaId, chainId, isExportWallet)

        return NavComponentDelayedNavigation(R.id.action_export_seed, extras)
    }

    override fun openExportSeed(metaId: Long, chainId: ChainId): DelayedNavigation {
        return openExportSeed(metaId, chainId, isExportWallet = false)
    }

    override fun openExportJsonPassword(metaId: Long, chainId: ChainId, isExportWallet: Boolean): DelayedNavigation {
        val extras = ExportJsonPasswordFragment.getBundle(metaId, chainId, isExportWallet)

        return NavComponentDelayedNavigation(R.id.action_export_json, extras)
    }

    override fun openConfirmMnemonicOnExport(mnemonic: List<String>) {
        val extras = ConfirmMnemonicFragment.getBundle(ConfirmMnemonicPayload(mnemonic, null))

        navController?.navigate(R.id.action_exportMnemonicFragment_to_confirmExportMnemonicFragment, extras)
    }

    override fun openExportJsonPassword(metaId: Long, chainId: ChainId): DelayedNavigation {
        return openExportJsonPassword(metaId, chainId, isExportWallet = false)
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
        navController?.navigate(R.id.action_mainFragment_to_pinCodeFragment, bundle)
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

    private fun buildCreatePinBundle(): Bundle {
        val delayedNavigation = NavComponentDelayedNavigation(R.id.action_open_main)
        val action = PinCodeAction.Create(delayedNavigation)
        return PincodeFragment.getPinCodeBundle(action)
    }

    override fun openEducationalStories(stories: StoryGroupModel) {
        navController?.navigate(R.id.action_splash_to_stories, StoryFragment.getBundle(stories))
    }

    override fun openSelectWallet() {
        navController?.navigate(R.id.selectWalletFragment)
    }

    override fun openNetworkIssues() {
        navController?.navigate(R.id.networkIssuesFragment)
    }

    override fun openOptionsAddAccount(payload: AddAccountBottomSheet.Payload) {
        val bundle = OptionsAddAccountFragment.getBundle(payload)
        navController?.navigate(R.id.optionsAddAccountFragment, bundle)
    }

    override fun openAlert(payload: AlertViewState) {
        openAlert(payload, emptyResultKey)
    }

    override fun openAlert(payload: AlertViewState, resultKey: String) {
        val currentDestination = requireNotNull(navController?.currentDestination?.id)
        openAlert(payload, resultKey, currentDestination)
    }

    override fun openAlert(payload: AlertViewState, resultKey: String, resultDestinationId: Int) {
        val bundle = AlertFragment.getBundle(payload, resultKey, resultDestinationId)
        navController?.navigate(R.id.alertFragment, bundle)
    }

    override fun openSearchAssets() {
        navController?.navigate(R.id.searchAssetsFragment)
    }

    override fun openOptionsWallet(walletId: Long) {
        val bundle = OptionsWalletFragment.getBundle(walletId)
        navController?.navigate(R.id.optionsWalletFragment, bundle)
    }

    override fun openFrozenTokens(payload: FrozenAssetPayload) {
        val bundle = FrozenTokensFragment.getBundle(payload)
        navController?.navigate(R.id.frozenTokensFragment, bundle)
    }

    fun educationalStoriesCompleted() {
        navController?.previousBackStackEntry?.savedStateHandle?.set(StoryFragment.KEY_STORY, true)
        navController?.navigateUp()
    }

    override val educationalStoriesCompleted: Flow<Boolean>
        get() {
            return combineLiveData(
                navController?.currentBackStackEntry?.getLifecycle()?.onResumeObserver() ?: return flowOf(false),
                navController?.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>(StoryFragment.KEY_STORY) ?: return flowOf(false),
                combiner = { (isResumed: Boolean, storiesCompleted: Boolean) ->
                    isResumed && storiesCompleted
                }
            ).asFlow()
        }

    override fun openExperimentalFeatures() {
        navController?.navigate(R.id.experimentalFragment)
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
        navController?.previousBackStackEntry?.savedStateHandle?.set(WalletSelectorPayload::class.java.name, payload)
    }

    override fun openStartSelectValidators() {
        navController?.navigate(R.id.startSelectValidatorsFragment)
    }

    override fun openSelectValidators() {
        navController?.navigate(R.id.selectValidatorsFragment)
    }

    override fun openValidatorsSettings() {
        navController?.navigate(R.id.validatorsSettingsFragment)
    }

    override fun openConfirmSelectValidators() {
        navController?.navigate(R.id.confirmSelectValidatorsFragment)
    }

    override fun openPoolInfoOptions(poolInfo: PoolInfo) {
        navController?.navigate(R.id.poolOptionsInfoFragment, PoolInfoOptionsFragment.getBundle(poolInfo))
    }

    override fun openEditPool() {
        navController?.navigate(R.id.editPoolFragment)
    }

    override fun openEditPoolConfirm() {
        navController?.navigate(R.id.editPoolConfirmFragment)
    }

    override fun openGetSoraCard() {
        navController?.navigate(R.id.getSoraCardFragment)
    }

    override val walletSelectorPayloadFlow: Flow<WalletSelectorPayload?>
        get() = navController?.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<WalletSelectorPayload?>(WalletSelectorPayload::class.java.name)
            ?.asFlow() ?: emptyFlow()

    override fun setAlertResult(key: String, result: Result<*>, resultDestinationId: Int?) {
        val resultBackStackEntry = resultDestinationId?.let { navController?.getBackStackEntry(it) } ?: navController?.previousBackStackEntry
        resultBackStackEntry?.savedStateHandle?.set(
            key,
            result
        )
    }

    override fun alertResultFlow(key: String): Flow<Result<Unit>> {
        val currentEntry = navController?.currentBackStackEntry
        val onResumeObserver = currentEntry?.getLifecycle()?.onResumeObserver()

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
        val currentEntry = navController?.getBackStackEntry(R.id.startSelectValidatorsFragment)
        val onResumeObserver = currentEntry?.getLifecycle()?.onResumeObserver()

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

    override fun openWebViewer(title: String, url: String) {
        navController?.navigate(R.id.webViewerFragment, WebViewerFragment.getBundle(title, url))
    }

    override fun setChainSelectorPayload(chainId: ChainId?) {
        navController?.previousBackStackEntry?.savedStateHandle?.set(ChainSelectFragment.KEY_SELECTED_CHAIN_ID, chainId)
    }

    override val chainSelectorPayloadFlow: Flow<ChainId?>
        get() = navController?.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<ChainId?>(ChainSelectFragment.KEY_SELECTED_CHAIN_ID)
            ?.asFlow() ?: emptyFlow()

    override fun openPoolFullUnstakeDepositorAlertFragment(amount: String) {
        val bundle = PoolFullUnstakeDepositorAlertFragment.getBundle(amount)
        navController?.navigate(R.id.poolFullUnstakeDepositorAlertFragment, bundle)
    }

    override fun openGetMoreXor() {
        navController?.navigate(R.id.getMoreXorFragment)
    }
}
