package jp.co.soramitsu.staking.impl.presentation.staking.bond.select

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.StakeInsufficientBalanceException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.PoolManagePreparationReview
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel
import jp.co.soramitsu.wallet.api.presentation.Validation
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount

@HiltViewModel
class PoolBondMoreViewModel private constructor(
    resourceManager: ResourceManager,
    private val review: PoolManagePreparationReview,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter,
    private val quickInputsUseCase: QuickInputsUseCase,
) : BaseEnterAmountViewModel(
    onAmountChanged = review::amountChanged,
    nextButtonTextRes = R.string.common_continue,
    toolbarTextRes = R.string.staking_bond_more_v1_9_0,
    balanceHintRes = R.string.common_available_format,
    asset = review.main.requireAsset,
    resourceManager = resourceManager,
    quickInputsUseCase  = quickInputsUseCase,
    feeEstimator = { amount ->
        review.requireReadable()
        stakingPoolInteractor.estimateBondMoreFee(review.main.requireAddress, amount).also { review.requireReadable() }
    },
    onNextStep = { amount ->
        review.prepare { attempt ->
            review.continueTo(attempt, amount) { router.openPoolConfirmBondMore() }
        }
    },
    validations = arrayOf(
        Validation(
            condition = {
                review.requireCurrent()
                val asset = review.main.requireAsset
                val transferableInPlanks = asset.transferableInPlanks
                transferableInPlanks != null && it < transferableInPlanks
            },
            error = StakeInsufficientBalanceException(resourceManager)
        )
    ),
    errorAlertPresenter = {
        review.present { router.openAlert(it) }
    },
    quickInputsCalculator = {
        review.requireReadable()
        val asset = review.main.requireAsset
        quickInputsUseCase.calculateStakingQuickInputs(
            asset.token.configuration.chainId,
            asset.token.configuration.id,
            calculateAvailableAmount = { review.requireReadable(); asset.transferable },
            calculateFee = { amount ->
                review.requireReadable()
                stakingPoolInteractor.estimateBondMoreFee(review.main.requireAddress, amount).also { review.requireReadable() }
            }
        ).also { review.requireReadable() }
    }
) {
    @Inject constructor(
        resourceManager: ResourceManager,
        stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
        stakingPoolInteractor: StakingPoolInteractor,
        router: StakingRouter,
        quickInputsUseCase: QuickInputsUseCase
    ) : this(resourceManager, stakingPoolSharedStateProvider.managePreparationReview(), stakingPoolInteractor, router, quickInputsUseCase)

    fun onBackClick() {
        review.goBack { router.back() }
    }

    fun onDismissed() { review.revoke() }

    fun onResumed() { review.resume() }

    override fun onCleared() {
        review.revoke()
        super.onCleared()
    }
}
