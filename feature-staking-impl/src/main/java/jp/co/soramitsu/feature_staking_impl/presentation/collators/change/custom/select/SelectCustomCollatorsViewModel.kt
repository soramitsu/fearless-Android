package jp.co.soramitsu.feature_staking_impl.presentation.collators.change.custom.select

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.Filters
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
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
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
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class SelectCustomCollatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val collatorRecommendatorFactory: CollatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val setupStakingSharedState: SetupStakingSharedState,
    @Named("StakingTokenUseCase") private val tokenUseCase: TokenUseCase,
    private val settingsStorage: SettingsStorage,
) : BaseViewModel() {

    val state = setupStakingSharedState.getOrNull<SetupStakingProcess.SelectBlockProducersStep.Collators>()

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

    private val sortingFlow = recommendationSettingsFlow.map { it.sorting }

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
                text = resourceManager.getString(R.string.staking_select_collator_title)
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
            BlockProducersSorting.CollatorSorting.CollatorsOwnStakeSorting -> resourceManager.getString(R.string.collator_staking_sorting_own_stake)
            BlockProducersSorting.CollatorSorting.DelegationsSorting -> resourceManager.getString(R.string.collator_staking_sorting_delegations)
            BlockProducersSorting.CollatorSorting.EffectiveAmountBondedSorting -> {
                resourceManager.getString(R.string.collator_staking_sorting_effective_amount_bonded)
            }
            BlockProducersSorting.CollatorSorting.MinimumBondSorting -> resourceManager.getString(R.string.collator_staking_sorting_minimum_bond)
            else -> throw IllegalArgumentException("Unknown sorting: ${it.sorting}")
        }
    }.inBackground().share()

//    val fillWithRecommendedEnabled = selectedCollator.map { it.size < maxSelectedValidatorsFlow.first() }
//        .share()

    val clearFiltersEnabled = recommendationSettingsFlow.map { it.customEnabledFilters.isNotEmpty() || it.postProcessors.isNotEmpty() }
        .share()

    val deselectAllEnabled = selectedCollator.value != null

    val identityFilterEnabled = settingsStorage.schema.map { schema ->
        schema.filters.find { it.filter == Filters.HavingOnChainIdentity }?.checked == true
    }.asLiveData()

    val minimumBondFilterEnabled = settingsStorage.schema.map { schema ->
        schema.filters.find { it.filter == Filters.WithRelevantBond }?.checked == true
    }.asLiveData()

    init {
        observeExternalSelectionChanges()
        state?.let {
            settingsStorage.currentFiltersSet.value = it.filtersSet
            settingsStorage.currentSortingSet.value = it.sortingSet
            settingsStorage.quickFilters = it.quickFilters

            launch {
                settingsStorage.schema.collect {
                    val amount = tokenUseCase.currentToken().configuration.planksFromAmount(state.payload.amount)
                    recommendationSettingsProvider().settingsChanged(it, amount)
                }
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

    fun havingOnChainIdentityFilterClicked() {
        val filter = state?.filtersSet?.find { it == Filters.HavingOnChainIdentity }
        filter?.let { settingsStorage.filterSelected(it) }
    }

    fun relevantBondFilterCLicked() {
        val filter = state?.filtersSet?.find { it == Filters.WithRelevantBond }
        filter?.let { settingsStorage.filterSelected(it) }
    }
}
