package jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingConfirmViewModel
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.PoolManageConfirmationReview
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class ConfirmPoolBondMoreViewModel private constructor(
    walletInteractor: WalletInteractor,
    existentialDepositUseCase: ExistentialDepositUseCase,
    private val stakingPoolInteractor: StakingPoolInteractor,
    resourceManager: ResourceManager,
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
    amountInPlanks = requireNotNull(review.fields.amountInPlanks),
    feeEstimator = { amount ->
        review.requireCurrent()
        stakingPoolInteractor.estimateBondMoreFee(review.main.requireAddress, requireNotNull(amount)).also { review.requireCurrent() }
    },
    executeOperation = { address, amount -> review.execute { stakingPoolInteractor.bondMore(address, requireNotNull(amount)) } },
    onOperationSuccess = { router.returnToManagePoolStake() },
    accountNameProvider = {
        withContext(Dispatchers.Default) {
            stakingPoolInteractor.getAccountName(it)
        }
    },
    titleRes = R.string.staking_bond_more_v1_9_0,
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
        return super.isValid().also { review.requireCurrent() }
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
