package jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm

import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.FeeInsufficientBalanceException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingConfirmViewModel
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase

@HiltViewModel
class ConfirmPoolUnbondViewModel @Inject constructor(
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter
) : StakingConfirmViewModel(
    existentialDepositUseCase = existentialDepositUseCase,
    chain = poolSharedStateProvider.requireMainState.requireChain,
    router = router,
    address = poolSharedStateProvider.requireMainState.requireAddress,
    resourceManager = resourceManager,
    asset = poolSharedStateProvider.requireMainState.requireAsset,
    amountInPlanks = requireNotNull(poolSharedStateProvider.manageState.get()?.amountInPlanks),
    feeEstimator = { stakingPoolInteractor.estimateUnstakeFee(poolSharedStateProvider.requireMainState.requireAddress, requireNotNull(it)) },
    executeOperation = { address, amount -> stakingPoolInteractor.unstake(address, requireNotNull(amount)) },
    onOperationSuccess = { router.returnToManagePoolStake() },
    accountNameProvider = { stakingPoolInteractor.getAccountName(it) },
    titleRes = R.string.staking_unbond_v1_9_0,
    additionalMessageRes = R.string.pool_staking_unstake_alert
) {
    fun onBackClick() {
        router.back()
    }

    override suspend fun isValid(): Result<Any> {
        val fee = feeInPlanksFlow.value ?: return Result.failure(WaitForFeeCalculationException(resourceManager))

        val existentialDeposit = existentialDepositUseCase(asset.token.configuration)

        val resultBalance = asset.transferableInPlanks - fee
        if (resultBalance < existentialDeposit || resultBalance <= BigInteger.ZERO) {
            return Result.failure(FeeInsufficientBalanceException(resourceManager))
        }
        return Result.success(Unit)
    }
}
