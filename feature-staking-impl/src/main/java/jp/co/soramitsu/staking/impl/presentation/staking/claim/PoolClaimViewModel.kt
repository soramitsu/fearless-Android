package jp.co.soramitsu.staking.impl.presentation.staking.claim

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.PoolManagePreparationReview
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import javax.inject.Inject

@HiltViewModel
class PoolClaimViewModel private constructor(
    resourceManager: ResourceManager,
    private val review: PoolManagePreparationReview,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter
) : BaseEnterAmountViewModel(
    onAmountChanged = review::amountChanged,
    nextButtonTextRes = R.string.common_continue,
    toolbarTextRes = R.string.common_claim,
    balanceHintRes = R.string.common_balance_format,
    asset = review.main.requireAsset,
    initialAmount = review.fields.claimableInPlanks.let {
        val asset = review.main.requireAsset
        asset.token.amountFromPlanks(it)
    },
    isInputActive = false,
    resourceManager = resourceManager,
    feeEstimator = {
        review.requireReadable()
        stakingPoolInteractor.estimateClaimFee(review.main.requireAddress).also { review.requireReadable() }
    },
    onNextStep = { review.prepare { attempt -> review.continueTo(attempt) { router.openPoolConfirmClaim() } } },
    buttonValidation = { true },
    errorAlertPresenter = {
        review.present { router.openAlert(it) }
    }
) {
    @Inject constructor(
        resourceManager: ResourceManager,
        stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
        stakingPoolInteractor: StakingPoolInteractor,
        router: StakingRouter
    ) : this(resourceManager, stakingPoolSharedStateProvider.managePreparationReview(), stakingPoolInteractor, router)

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
