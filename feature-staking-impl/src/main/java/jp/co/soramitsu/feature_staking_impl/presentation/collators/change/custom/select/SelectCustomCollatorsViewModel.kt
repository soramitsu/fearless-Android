package jp.co.soramitsu.feature_staking_impl.presentation.collators.change.custom.select

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProvider
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapCollatorToCollatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.select.model.ContinueButtonState
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.setCustomCollators
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.IdentityParcelModel
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SelectCustomCollatorsViewModel(
    private val router: StakingRouter,
    private val collatorRecommendatorFactory: CollatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val setupStakingSharedState: SetupStakingSharedState,
    tokenUseCase: TokenUseCase,
    private val settingsStorage: SettingsStorage,
) : BaseViewModel() {

    val state = setupStakingSharedState.get<SetupStakingProcess.SelectBlockProducersStep.Collators>()

    private val collatorRecommendator by lazyAsync {
        collatorRecommendatorFactory.create(router.currentStackEntryLifecycle)
    }

    private val recommendationSettingsProvider: Deferred<RecommendationSettingsProvider<Collator>> by lazyAsync {
        recommendationSettingsProviderFactory.createParachain(router.currentStackEntryLifecycle)
    }

    private val recommendationSettingsFlow: Flow<RecommendationSettings<Collator>> = flow {
        emitAll(recommendationSettingsProvider().observeRecommendationSettings())
    }.share()

    private val shownCollators = recommendationSettingsFlow.map {
        recommendator().recommendations(it)
    }.share()

    private val tokenFlow = tokenUseCase.currentTokenFlow()
        .inBackground()
        .share()

    private val selectedCollator = MutableStateFlow<Collator?>(null)

    private val iconsCache: MutableMap<String, AddressModel> = mutableMapOf()

    val sortingFlow = recommendationSettingsFlow.map { it.sorting }

    val collatorModelsFlow = combine(
        shownCollators,
        selectedCollator,
        tokenFlow,
        sortingFlow
    ) { shown, selected, token, sorting ->
        convertToModels(shown, token, selected?.address, sorting)
    }
        .inBackground()
        .share()

    val selectedTitle = shownCollators.map {
        resourceManager.getString(R.string.staking_custom_header_collators_title, it.size, recommendator().availableCollators.size)
    }.inBackground().share()

    val buttonState = selectedCollator.map {
        if (it == null) {
            ContinueButtonState(
                enabled = false,
                text = resourceManager.getString(R.string.staking_custom_collators_default_button_state_text)
            )
        } else {
            ContinueButtonState(
                enabled = true,
                text = resourceManager.getString(R.string.common_select)
            )
        }
    }

    val scoringHeader = recommendationSettingsFlow.map {
        when (it.sorting) {
            BlockProducersSorting.CollatorSorting.APYSorting -> resourceManager.getString(R.string.staking_rewards_apy)
            BlockProducersSorting.CollatorSorting.CollatorsOwnStakeSorting -> "Own stake"
            BlockProducersSorting.CollatorSorting.DelegationsSorting -> "Delegations"
            BlockProducersSorting.CollatorSorting.EffectiveAmountBondedSorting -> "Effective amount bonded"
            BlockProducersSorting.CollatorSorting.MinimumBondSorting -> "Minimum bond"
            else -> throw IllegalArgumentException("Unknown sorting: ${it.sorting}")
        }
    }.inBackground().share()

//    val fillWithRecommendedEnabled = selectedCollator.map { it.size < maxSelectedValidatorsFlow.first() }
//        .share()

    val clearFiltersEnabled = recommendationSettingsFlow.map { it.customEnabledFilters.isNotEmpty() || it.postProcessors.isNotEmpty() }
        .share()

    val deselectAllEnabled = selectedCollator.value != null

    init {
        observeExternalSelectionChanges()

        settingsStorage.currentFiltersSet.value = state.filtersSet
        settingsStorage.currentSortingSet.value = state.sortingSet

        launch {
            settingsStorage.schema.collect {
                recommendationSettingsProvider().settingsChanged(it)
            }
        }
    }

    fun backClicked() {
        // todo do we need apply selected collators when moving back?
//        updateSetupStakingState()

        router.back()
    }

    fun nextClicked() {
        updateSetupStakingState()

        router.openConfirmStaking()
    }

    fun collatorInfoClicked(collatorModel: CollatorModel) {
        router.openCollatorDetails(
            CollatorDetailsParcelModel(
                collatorModel.accountIdHex,
                CollatorStakeParcelModel(
                    status = collatorModel.collator.status,
                    selfBonded = collatorModel.collator.bond,
                    delegations = collatorModel.collator.delegationCount.toInt(),
                    totalStake = collatorModel.collator.totalCounted,
                    minBond = collatorModel.collator.lowestTopDelegationAmount,
                    estimatedRewards = collatorModel.collator.apy,
                ),
                collatorModel.collator.identity?.let {
                    IdentityParcelModel(
                        display = it.display,
                        legal = it.legal,
                        web = it.web,
                        riot = it.riot,
                        email = it.email,
                        pgpFingerprint = it.pgpFingerprint,
                        image = it.image,
                        twitter = it.twitter,
                    )
                },
                collatorModel.collator.request.orEmpty(),
            )
        )
    }

    fun collatorClicked(collatorModel: CollatorModel) {
        selectedCollator.value = collatorModel.collator
    }

    fun settingsClicked() {
        router.openCustomValidatorsSettingsFromCollator()
    }

    fun searchClicked() {
        updateSetupStakingState()

        router.openSearchCustomCollators()
    }

    private fun updateSetupStakingState() {
        setupStakingSharedState.setCustomCollators(selectedCollator.value?.let { listOf(it) } ?: emptyList())
    }

    fun clearFilters() {
        settingsStorage.resetFilters()
    }

    fun deselectAll() {
        selectedCollator.value = null
    }

//    fun fillRestWithRecommended() {
//        mutateSelected { selected ->
//            val recommended = recommendator().recommendations(recommendationSettingsProvider().defaultSettings())
//            selected ?: return@mutateSelected recommended
//            val missingFromRecommended = recommended.toSet().filter { it.address != selected.address }
//            val neededToFill = maxSelectedValidatorsFlow.first() - selected.size
//
//            selected + missingFromRecommended.take(neededToFill).toSet()
//        }
//    }

    private fun observeExternalSelectionChanges() {
        setupStakingSharedState.setupStakingProcess
            .filterIsInstance<SetupStakingProcess.ReadyToSubmit.Parachain>()
            .onEach { selectedCollator.value = it.payload.blockProducers.firstOrNull() }
            .launchIn(viewModelScope)
    }

    private suspend fun convertToModels(
        collators: List<Collator>,
        token: Token,
        selectedCollator: String?,
        sorting: BlockProducersSorting<Collator>,
    ): List<CollatorModel> {
        return collators.map {
            mapCollatorToCollatorModel(
                collator = it,
                iconGenerator = addressIconGenerator,
                token = token,
                selectedCollatorAddress = selectedCollator,
                sorting = sorting
            )
        }
    }

    private suspend fun recommendator() = collatorRecommendator.await()
}
