package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import jp.co.soramitsu.common.AlertViewState
import kotlinx.coroutines.CancellationException
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.InsufficientStakeBalanceException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.RoleInPool
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.PoolManagePreparationReview
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseEnterAmountViewModel
import jp.co.soramitsu.wallet.api.presentation.Validation
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.launch
import javax.inject.Inject
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase

@HiltViewModel
class PoolUnstakeViewModel private constructor(
    resourceManager: ResourceManager,
    private val review: PoolManagePreparationReview,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter,
    private val quickInputsUseCase: QuickInputsUseCase,
) : BaseEnterAmountViewModel(
    onAmountChanged = review::amountChanged,
    nextButtonTextRes = R.string.common_continue,
    toolbarTextRes = R.string.staking_unbond_v1_9_0,
    balanceHintRes = R.string.common_available_format,
    asset = review.main.requireAsset,
    resourceManager = resourceManager,
    quickInputsUseCase  = quickInputsUseCase,
    feeEstimator = {
        review.requireReadable()
        stakingPoolInteractor.estimateUnstakeFee(
            review.main.requireAddress,
            it
        ).also { review.requireReadable() }
    },
    onNextStep = { amount ->
        review.prepare { attempt ->
            val minimum = runCatching { stakingPoolInteractor.getMinToCreate(review.main.requireChain.id) }
            review.requireCurrent()
            val minToCreate = minimum.getOrElse { error ->
                if (error is CancellationException) throw error
                review.continueTo(attempt) {
                    router.openAlert(AlertViewState(
                        title = resourceManager.getString(R.string.common_error_general_title),
                        message = error.localizedMessage ?: resourceManager.getString(R.string.common_undefined_error_message),
                        buttonText = resourceManager.getString(R.string.common_got_it),
                        iconRes = R.drawable.ic_status_warning_16
                    ))
                }
                return@prepare
            }
            val isDepositor = review.fields.userRole == RoleInPool.Depositor
            if (isDepositor && (review.fields.stakedInPlanks - amount) < minToCreate) {
                val minToCreateFormatted = minToCreate.formatCryptoDetailFromPlanks(review.main.requireAsset.token.configuration)
                review.continueTo(attempt, expectsResult = true) { router.openPoolFullUnstakeDepositorAlertFragment(minToCreateFormatted) }
            } else {
                review.continueTo(attempt, amount) { router.openPoolConfirmUnstake() }
            }
        }
    },
    validations = arrayOf(
        Validation(
            condition = {
                review.requireCurrent()
                val stakedInPlanks = review.fields.stakedInPlanks
                it <= stakedInPlanks
            },
            error = InsufficientStakeBalanceException(resourceManager)
        )
    ),
    availableAmountForOperation = { it.token.amountFromPlanks(review.fields.stakedInPlanks) },
    errorAlertPresenter = {
        review.present { router.openAlert(it) }
    },
    quickInputsCalculator = {
        review.requireReadable()
        val asset = review.main.requireAsset
        quickInputsUseCase.calculateStakingQuickInputs(
            asset.token.configuration.chainId,
            asset.token.configuration.id,
            calculateAvailableAmount = {
                review.requireReadable()
                asset.token.amountFromPlanks(
                    review.fields.stakedInPlanks
                )
            },
            calculateFee = { review.requireReadable(); BigInteger.ZERO }).also { review.requireReadable() }
    }
) {
    @Inject constructor(
        resourceManager: ResourceManager,
        stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
        stakingPoolInteractor: StakingPoolInteractor,
        router: StakingRouter,
        quickInputsUseCase: QuickInputsUseCase
    ) : this(resourceManager, stakingPoolSharedStateProvider.managePreparationReview(), stakingPoolInteractor, router, quickInputsUseCase)

    init {
        viewModelScope.launch {
            router.alertResultFlow(PoolFullUnstakeDepositorAlertFragment.KEY_RESULT).collect {
                review.presentResult {
                    if (it.isSuccess) router.openWebViewer(
                        resourceManager.getString(R.string.pool_depositor_unstake_polkadot_js_plus_button_text),
                        BuildConfig.POLKADOT_JS_PLUS_EXTENSION
                    )
                }
            }
        }
    }

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
