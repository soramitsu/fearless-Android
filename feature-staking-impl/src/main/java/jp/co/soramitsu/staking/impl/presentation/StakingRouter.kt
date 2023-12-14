package jp.co.soramitsu.staking.impl.presentation

import androidx.annotation.IdRes
import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm.ConfirmBondMorePayload
import jp.co.soramitsu.staking.impl.presentation.staking.bond.select.SelectBondMorePayload
import jp.co.soramitsu.staking.impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.staking.impl.presentation.staking.redeem.RedeemPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.ConfirmUnbondPayload
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.select.SelectUnbondPayload
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import kotlinx.coroutines.flow.Flow

interface StakingRouter {

    fun openSetupStaking()

    fun openStartChangeCollators()

    fun openRecommendedCollators()

    fun openSelectCustomCollators()

    fun openSelectPool()

    fun openStartChangeValidators()

    fun openRecommendedValidators()

    fun openSelectCustomValidators()

    fun openCustomValidatorsSettingsFromValidator()

    fun openCustomValidatorsSettingsFromCollator()

    fun openSearchCustomValidators()

    fun openSearchCustomCollators()

    fun openReviewCustomValidators()

    fun openValidatorDetails(validatorDetails: ValidatorDetailsParcelModel)

    fun openSelectedValidators()

    fun openCollatorDetails(collatorDetails: CollatorDetailsParcelModel)

    fun openConfirmStaking()

    fun openConfirmNominations()

    fun returnToMain()

    fun openSelectWallet()

    fun openStory(story: StoryGroupModel)

    fun openPayouts()

    fun openPayoutDetails(payout: PendingPayoutParcelable)

    fun openConfirmPayout(payload: ConfirmPayoutPayload)

    fun openStakingBalance(collatorAddress: String? = null)

    fun openBondMore(payload: SelectBondMorePayload)

    fun openConfirmBondMore(payload: ConfirmBondMorePayload)

    fun returnToStakingBalance()

    fun openSelectUnbond(payload: SelectUnbondPayload)

    fun openConfirmUnbond(payload: ConfirmUnbondPayload)

    fun openRedeem(payload: RedeemPayload)

    fun openConfirmRebond(payload: ConfirmRebondPayload)

    fun openControllerAccount()

    fun back()

    fun openConfirmSetController(payload: ConfirmSetControllerPayload)

    fun openCustomRebond()

    fun openCurrentValidators()

    fun returnToCurrentValidators()

    fun openChangeRewardDestination()

    fun openConfirmRewardDestination(payload: ConfirmRewardDestinationPayload)

    fun openStakingPoolWelcome()

    val currentStackEntryLifecycle: Lifecycle

    fun openSetupStakingPool()

    fun openConfirmJoinPool()

    fun openPoolInfo(poolInfo: PoolInfo)

    fun openManagePoolStake()

    fun openPoolBondMore()

    fun openPoolClaim()

    fun openPoolRedeem()

    fun openPoolUnstake()

    fun openPoolConfirmBondMore()

    fun openPoolConfirmClaim()

    fun openPoolConfirmRedeem()

    fun openPoolConfirmUnstake()

    fun returnToManagePoolStake()

    fun openCreatePoolSetup()

    fun openWalletSelector(tag: String)

    fun openCreatePoolConfirm()

    fun openStartSelectValidators()

    fun openSelectValidators()

    fun openValidatorsSettings()

    fun openConfirmSelectValidators()

    fun openPoolInfoOptions(poolInfo: PoolInfo)

    fun openEditPool()

    fun openEditPoolConfirm()

    val walletSelectorPayloadFlow: Flow<WalletSelectorPayload?>

    fun openAlert(payload: AlertViewState)

    fun openAlert(payload: AlertViewState, resultKey: String)

    fun openAlert(payload: AlertViewState, resultKey: String, @IdRes resultDestinationId: Int)

    fun openWebViewer(title: String, url: String)

    fun openOperationSuccess(operationHash: String?, chainId: ChainId?, customMessage: String? = null)

    fun setAlertResult(key: String, result: Result<*>, @IdRes resultDestinationId: Int? = null)

    fun openPoolFullUnstakeDepositorAlertFragment(amount: String)

    fun alertResultFlow(key: String): Flow<Result<Unit>>

    fun openAlertFromStartSelectValidatorsScreen(payload: AlertViewState, key: String)

    fun listenAlertResultFlowFromStartSelectValidatorsScreen(key: String): Flow<Result<Unit>>

    fun openImportAccountScreenFromWallet(blockChainType: Int)

    fun openManageControllerAccount(chainId: ChainId)
    fun openAlertFromStartChangeValidatorsScreen(payload: AlertViewState, keyAlertResult: String)
    fun listenAlertResultFlowFromStartChangeValidatorsScreen(key: String): Flow<Result<Unit>>
}
