package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start

import androidx.lifecycle.MutableLiveData
import java.math.BigDecimal
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

private val RECOMMENDED_FEATURES_IDS = listOf(
    R.string.staking_recommended_feature_1,
    R.string.staking_recommended_feature_2,
//    R.string.staking_recommended_feature_3,
//    R.string.staking_recommended_feature_4,
//    R.string.staking_recommended_feature_5,
)

class CustomCollatorsTexts(
    val title: String,
    val badge: String?
)

class StartChangeCollatorsViewModel(
    private val router: StakingRouter,
    private val collatorRecommendatorFactory: CollatorRecommendatorFactory,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val resourceManager: ResourceManager,
    private val stakingParachainScenarioInteractor: StakingParachainScenarioInteractor,
    private val stakingInteractor: StakingInteractor
) : BaseViewModel(), Browserable {

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val maxValidatorsPerNominator = flowOf {
        stakingParachainScenarioInteractor.maxDelegationsPerDelegator()
    }.share()

    val collatorsLoading = MutableStateFlow(true)

    val customCollatorsTexts = setupStakingSharedState.setupStakingProcess.transform {
        when {
            it is SetupStakingProcess.ReadyToSubmit<*> && it.payload.blockProducers.isNotEmpty() -> emit(
                CustomValidatorsTexts(
                    title = resourceManager.getString(R.string.staking_custom_validators_update_list),
                    badge = resourceManager.getString(
                        R.string.staking_max_format,
                        it.payload.blockProducers.size,
                        maxValidatorsPerNominator.first()
                    )
                )
            )
            it is SetupStakingProcess.SelectBlockProducersStep -> emit(
                CustomValidatorsTexts(
                    title = resourceManager.getString(R.string.staking_select_custom),
                    badge = null
                )
            )
        }
    }

    fun getRecommendedFeaturesIds() = RECOMMENDED_FEATURES_IDS

    init {
        launch {
            collatorRecommendatorFactory.awaitBlockCreatorsLoading(router.currentStackEntryLifecycle)

            collatorsLoading.value = false
        }
    }

    fun goToCustomClicked() {
        router.openSelectCustomCollators()
    }

    fun goToRecommendedClicked() {
        launch {
            if (canShowRecommendedCollators()) {
                router.openRecommendedCollators()
            } else {
                showError(resourceManager.getString(R.string.staking_recommended_collators_empty_text))
            }
        }
    }

    private suspend fun canShowRecommendedCollators(): Boolean {
        val userInputAmount = setupStakingSharedState.getOrNull<SetupStakingProcess.SelectBlockProducersStep.Collators>()
            ?.payload?.amount ?: BigDecimal.ZERO
        val collatorRecommendator = collatorRecommendatorFactory.create(router.currentStackEntryLifecycle)
        val token = stakingInteractor.currentAssetFlow().first().token
        val collators = collatorRecommendator.suggestedCollators(token.planksFromAmount(userInputAmount))
        return collators.isNotEmpty()
    }

    fun backClicked() {
//        setupStakingSharedState.retractValidators()

        router.back()
    }
}
