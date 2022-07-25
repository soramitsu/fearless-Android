package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.start

import androidx.lifecycle.MutableLiveData
import java.math.BigDecimal
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProvider
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    private val stakingInteractor: StakingInteractor,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
) : BaseViewModel(), Browserable {

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val maxValidatorsPerNominator = flowOf {
        stakingParachainScenarioInteractor.maxDelegationsPerDelegator()
    }.share()

    private val collatorRecommendator by lazyAsync {
        collatorRecommendatorFactory.create(router.currentStackEntryLifecycle)
    }

    private val recommendationSettingsProvider: Deferred<RecommendationSettingsProvider<Collator>> by lazyAsync {
        recommendationSettingsProviderFactory.createParachain(router.currentStackEntryLifecycle)
    }

    private val recommendationSettingsFlow: Flow<RecommendationSettings<Collator>> = flow {
        emitAll(recommendationSettingsProvider().observeRecommendationSettings())
    }.share()

    private val customCollatorsCount = recommendationSettingsFlow.map {
        recommendator().recommendations(it).size
    }.inBackground().share()

    val collatorsLoading = MutableStateFlow(true)

    val customCollatorsTexts = combine(setupStakingSharedState.setupStakingProcess, customCollatorsCount) { (setupStakingProcess, collatorsCount) ->
        when {
            setupStakingProcess is SetupStakingProcess.ReadyToSubmit<*> && setupStakingProcess.payload.blockProducers.isNotEmpty() ->
                CustomValidatorsTexts(
                    title = resourceManager.getString(R.string.staking_custom_validators_update_list),
                    badge = resourceManager.getString(
                        R.string.staking_max_format,
                        setupStakingProcess.payload.blockProducers.size,
                        maxValidatorsPerNominator.first()
                    )
                )
            setupStakingProcess is SetupStakingProcess.SelectBlockProducersStep ->
                CustomValidatorsTexts(
                    title = resourceManager.getString(R.string.staking_select_custom),
                    badge = collatorsCount.toString()
                )
            else -> null
        }
    }.filterNotNull()

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
        val token = stakingInteractor.currentAssetFlow().first().token
        val collators = recommendator().suggestedCollators(token.planksFromAmount(userInputAmount))
        return collators.isNotEmpty()
    }

    fun backClicked() {
//        setupStakingSharedState.retractValidators()

        router.back()
    }

    private suspend fun recommendator() = collatorRecommendator.await()
}
