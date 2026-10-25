package jp.co.soramitsu.staking.impl.presentation.staking.claim

import jp.co.soramitsu.staking.impl.presentation.common.stakingBalanceUnavailable

import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ExistentialDepositUnknownException
import jp.co.soramitsu.common.validation.FeeInsufficientBalanceException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingConfirmViewModel
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.PoolManageConfirmationReview
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor

@HiltViewModel
class ConfirmPoolClaimViewModel private constructor(
    walletInteractor: WalletInteractor,
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val review: PoolManageConfirmationReview
) : StakingConfirmViewModel(
    walletInteractor = walletInteractor,
    existentialDepositUseCase = existentialDepositUseCase,
    chain = review.main.requireChain,
    router = router,
    address = review.main.requireAddress,
    resourceManager = resourceManager,
    asset = review.main.requireAsset,
    amountInPlanks = requireNotNull(review.fields.claimableInPlanks),
    feeEstimator = { _ ->
        review.requireCurrent()
        stakingPoolInteractor.estimateClaimFee(review.main.requireAddress).also { review.requireCurrent() }
    },
    executeOperation = { address, _ -> review.execute { stakingPoolInteractor.claim(address) } },
    onOperationSuccess = { router.returnToManagePoolStake() },
    accountNameProvider = { stakingPoolInteractor.getAccountName(it) },
    titleRes = R.string.common_claim,
    withCurrentPresentation = review::present,
    withSuccessPresentation = review::withSuccessPresentation
) {
    @Inject constructor(
        walletInteractor: WalletInteractor,
        existentialDepositUseCase: ExistentialDepositUseCase,
        poolSharedStateProvider: StakingPoolSharedStateProvider,
        stakingPoolInteractor: StakingPoolInteractor,
        resourceManager: ResourceManager,
        router: StakingRouter
    ) : this(walletInteractor, existentialDepositUseCase, stakingPoolInteractor,
        resourceManager, router, poolSharedStateProvider.manageConfirmationReview())

    override suspend fun isValid(): Result<Any> {
        review.requireCurrent()
        val fee = feeInPlanksFlow.value ?: return Result.failure(missingFeeError())

        val existentialDeposit = existentialDepositUseCase(asset.token.configuration).getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            return Result.failure(ExistentialDepositUnknownException(resourceManager, asset.token.configuration.symbol.uppercase()))
        }

        review.requireCurrent()
        val available = asset.transferableInPlanks ?: return Result.failure(stakingBalanceUnavailable(resourceManager))
        val resultBalance = available - fee
        if (resultBalance < existentialDeposit || resultBalance <= BigInteger.ZERO) {
            return Result.failure(FeeInsufficientBalanceException(resourceManager))
        }
        return Result.success(Unit)
    }

    fun onBackClick() {
        if (review.revoke()) router.back()
    }

    /** A dismissed sheet must not retain submit or callback authority. */
    fun onDismissed() { review.revoke() }

    override fun onCleared() {
        review.revoke()
        super.onCleared()
    }
}
