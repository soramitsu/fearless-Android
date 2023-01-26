package jp.co.soramitsu.staking.impl.presentation.staking.redeem

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
class ConfirmPoolRedeemViewModel @Inject constructor(
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter
) : StakingConfirmViewModel(
    existentialDepositUseCase = existentialDepositUseCase,
    chain = poolSharedStateProvider.requireMainState.requireChain,
    router = router,
    address = requireNotNull(poolSharedStateProvider.mainState.get()?.address),
    resourceManager = resourceManager,
    asset = requireNotNull(poolSharedStateProvider.mainState.get()?.asset),
    amountInPlanks = requireNotNull(poolSharedStateProvider.manageState.get()?.redeemInPlanks),
    feeEstimator = { stakingPoolInteractor.estimateRedeemFee(requireNotNull(poolSharedStateProvider.mainState.get()?.address)) },
    executeOperation = { address, _ -> stakingPoolInteractor.redeem(address) },
    onOperationSuccess = { router.returnToManagePoolStake() },
    accountNameProvider = { stakingPoolInteractor.getAccountName(it) },
    titleRes = R.string.staking_redeem
) {

    override suspend fun isValid(): Result<Any> {
        val fee = feeInPlanksFlow.value ?: return Result.failure(WaitForFeeCalculationException(resourceManager))

        val existentialDeposit = existentialDepositUseCase(asset.token.configuration)

        val resultBalance = asset.transferableInPlanks - fee
        if (resultBalance < existentialDeposit || resultBalance <= BigInteger.ZERO) {
            return Result.failure(FeeInsufficientBalanceException(resourceManager))
        }
        return Result.success(Unit)
    }

    fun onBackClick() {
        router.back()
    }
}
