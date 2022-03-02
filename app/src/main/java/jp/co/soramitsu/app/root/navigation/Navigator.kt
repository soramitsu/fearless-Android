package jp.co.soramitsu.app.root.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.presentation.RootRouter
import jp.co.soramitsu.common.navigation.DelayedNavigation
import jp.co.soramitsu.common.utils.postToUiThread
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.account.details.AccountDetailsFragment
import jp.co.soramitsu.feature_account_impl.presentation.account.export.WalletExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts.AccountsForExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.account.list.AccountChosenNavDirection
import jp.co.soramitsu.feature_account_impl.presentation.account.list.AccountListFragment
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmFragment
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password.ExportJsonPasswordFragment
import jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic.ExportMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.exporting.seed.ExportSeedFragment
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.BackupMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicFragment
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload
import jp.co.soramitsu.feature_account_impl.presentation.node.add.AddNodeFragment
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsFragment
import jp.co.soramitsu.feature_account_impl.presentation.node.details.NodeDetailsPayload
import jp.co.soramitsu.feature_account_impl.presentation.node.list.NodesFragment
import jp.co.soramitsu.feature_account_impl.presentation.pincode.PinCodeAction
import jp.co.soramitsu.feature_account_impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.feature_account_impl.presentation.pincode.ToolbarConfiguration
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.ConfirmContributeFragment
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeFragment
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.CrowdloanContributeFragment
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.WelcomeFragment
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.ConfirmPayoutFragment
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.detail.PayoutDetailsFragment
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.confirm.ConfirmBondMoreFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.confirm.ConfirmBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMoreFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.ConfirmSetControllerFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingStoryModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.ConfirmRebondFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.ConfirmRewardDestinationFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm.ConfirmUnbondFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.confirm.ConfirmUnbondPayload
import jp.co.soramitsu.feature_staking_impl.presentation.story.StoryFragment
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.ValidatorDetailsFragment
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail.BalanceDetailFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.ReceiveFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.ChooseAmountFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm.ConfirmTransferFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.ChooseRecipientFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward.RewardDetailFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward.RewardDetailsPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.transfer.TransferDetailFragment
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.splash.SplashRouter
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.flow.Flow

@Parcelize
class NavComponentDelayedNavigation(val globalActionId: Int, val extras: Bundle? = null) : DelayedNavigation

class Navigator :
    SplashRouter,
    OnboardingRouter,
    AccountRouter,
    WalletRouter,
    RootRouter,
    StakingRouter,
    CrowdloanRouter {

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
        navController?.navigate(R.id.action_splash_to_onboarding, WelcomeFragment.getBundle(false))
    }

    override fun openInitialCheckPincode() {
        val action = PinCodeAction.Check(NavComponentDelayedNavigation(R.id.action_open_main), ToolbarConfiguration())
        val bundle = PincodeFragment.getPinCodeBundle(action)
        navController?.navigate(R.id.action_splash_to_pin, bundle)
    }

    override fun openCreateAccount() {
        navController?.navigate(R.id.action_welcomeFragment_to_createAccountFragment)
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

    override fun openOnboardingNavGraph(chainId: ChainId, metaId: Long, isImport: Boolean) {
        val bundle = WelcomeFragment.getBundle(
            displayBack = true,
            chainAccountData = ChainAccountCreatePayload(chainId, metaId, isImport),
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
            .setEnterAnim(R.anim.fragment_open_enter)
            .setExitAnim(R.anim.fragment_open_exit)
            .setPopEnterAnim(R.anim.fragment_close_enter)
            .setPopExitAnim(R.anim.fragment_close_exit)
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

    override fun openStory(story: StakingStoryModel) {
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

    override fun openStakingBalance() {
        navController?.navigate(R.id.action_mainFragment_to_stakingBalanceFragment)
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

    override fun openSelectUnbond() {
        navController?.navigate(R.id.action_stakingBalanceFragment_to_selectUnbondFragment)
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

    override val currentStackEntryLifecycle: Lifecycle
        get() = navController!!.currentBackStackEntry!!.lifecycle

    override fun openControllerAccount() {
        navController?.navigate(R.id.action_stakingBalanceFragment_to_setControllerAccountFragment)
    }

    override fun openConfirmSetController(payload: ConfirmSetControllerPayload) {
        navController?.navigate(
            R.id.action_stakingSetControllerAccountFragment_to_confirmSetControllerAccountFragment,
            ConfirmSetControllerFragment.getBundle(payload)
        )
    }

    override fun openRecommendedValidators() {
        navController?.navigate(R.id.action_startChangeValidatorsFragment_to_recommendedValidatorsFragment)
    }

    override fun openSelectCustomValidators() {
        navController?.navigate(R.id.action_startChangeValidatorsFragment_to_selectCustomValidatorsFragment)
    }

    override fun openCustomValidatorsSettings() {
        navController?.navigate(R.id.action_selectCustomValidatorsFragment_to_settingsCustomValidatorsFragment)
    }

    override fun openSearchCustomValidators() {
        navController?.navigate(R.id.action_selectCustomValidatorsFragment_to_searchCustomValidatorsFragment)
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

    override fun openValidatorDetails(validatorDetails: ValidatorDetailsParcelModel) {
        navController?.navigate(R.id.open_validator_details, ValidatorDetailsFragment.getBundle(validatorDetails))
    }

    override fun openChooseRecipient(assetPayload: AssetPayload) {
        val bundle = ChooseRecipientFragment.getBundle(assetPayload)

        navController?.navigate(R.id.action_open_send, bundle)
    }

    override fun openFilter() {
        navController?.navigate(R.id.action_mainFragment_to_filterFragment)
    }

    override fun openChooseAmount(recipientAddress: String, assetPayload: AssetPayload) {
        val bundle = ChooseAmountFragment.getBundle(recipientAddress, assetPayload)

        navController?.navigate(R.id.action_chooseRecipientFragment_to_chooseAmountFragment, bundle)
    }

    override fun openConfirmTransfer(transferDraft: TransferDraft) {
        val bundle = ConfirmTransferFragment.getBundle(transferDraft)

        navController?.navigate(R.id.action_chooseAmountFragment_to_confirmTransferFragment, bundle)
    }

    override fun finishSendFlow() {
        navController?.navigate(R.id.finish_send_flow)
    }

    override fun openRepeatTransaction(recipientAddress: String, assetPayload: AssetPayload) {
        val bundle = ChooseAmountFragment.getBundle(recipientAddress, assetPayload)

        navController?.navigate(R.id.openSelectAmount, bundle)
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

    override fun openManageAssets() {
        navController?.navigate(R.id.action_mainFragment_to_manageAssetsFragment)
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

    override fun openAddNode(chainId: ChainId) {
        navController?.navigate(R.id.action_nodesFragment_to_addNodeFragment, AddNodeFragment.getBundle(chainId))
    }

    override fun openExportMnemonic(metaId: Long, chainId: ChainId): DelayedNavigation {
        val extras = ExportMnemonicFragment.getBundle(metaId, chainId)

        return NavComponentDelayedNavigation(R.id.action_export_mnemonic, extras)
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

    override fun withPinCodeCheckRequired(
        delayedNavigation: DelayedNavigation,
        createMode: Boolean,
        pinCodeTitleRes: Int?,
    ) {
        val action = if (createMode) {
            PinCodeAction.Create(delayedNavigation)
        } else {
            PinCodeAction.Check(delayedNavigation, ToolbarConfiguration(pinCodeTitleRes, true))
        }

        val extras = PincodeFragment.getPinCodeBundle(action)

        navController?.navigate(R.id.open_pincode_check, extras)
    }

    private fun buildCreatePinBundle(): Bundle {
        val delayedNavigation = NavComponentDelayedNavigation(R.id.action_open_main)
        val action = PinCodeAction.Create(delayedNavigation)
        return PincodeFragment.getPinCodeBundle(action)
    }
}
