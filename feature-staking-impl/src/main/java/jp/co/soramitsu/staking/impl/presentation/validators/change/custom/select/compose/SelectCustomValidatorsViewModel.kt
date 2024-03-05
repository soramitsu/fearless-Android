package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.select.compose

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.dataOrNull
import jp.co.soramitsu.common.presentation.map
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProvider
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Sorting
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.staking.impl.presentation.validators.buildSegmentedValidatorsListState
import jp.co.soramitsu.staking.impl.presentation.validators.change.setCustomValidators
import jp.co.soramitsu.staking.impl.presentation.validators.change.setRecommendedValidators
import jp.co.soramitsu.staking.impl.presentation.validators.compose.SelectValidatorsScreenInterface
import jp.co.soramitsu.staking.impl.presentation.validators.compose.SelectValidatorsScreenViewState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val filtersSet =
    setOf(Filters.HavingOnChainIdentity, Filters.NotSlashedFilter, Filters.NotOverSubscribed)
private val sortingSet =
    setOf(Sorting.EstimatedRewards, Sorting.TotalStake, Sorting.ValidatorsOwnStake)

@HiltViewModel
class SelectCustomValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val resourceManager: ResourceManager,
    private val settingsStorage: SettingsStorage,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val interactor: StakingInteractor,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), SelectValidatorsScreenInterface {

    private val selectMode: SelectValidatorFlowState.ValidatorSelectMode =
        requireNotNull(savedStateHandle[SelectCustomValidatorsFragment.KEY_SELECTION_MODE])

    val stakingSharedState =
        setupStakingSharedState.getOrNull<SetupStakingProcess.SelectBlockProducersStep.Validators>()

    private val recommendationSettingsProvider: Deferred<RecommendationSettingsProvider<Validator>> by lazyAsync {
        recommendationSettingsProviderFactory.createRelayChain(router.currentStackEntryLifecycle)
    }

    private val selectedItems = MutableStateFlow<Set<String>>(emptySet())

    private val recommendedSettings: MutableStateFlow<RecommendationSettings<Validator>?> =
        MutableStateFlow(null)

    private val validatorRecommendator by lazyAsync { validatorRecommendatorFactory.create(router.currentStackEntryLifecycle) }

    private val searchQueryFlow = MutableStateFlow("")

    val state = MutableStateFlow(
        SelectValidatorsScreenViewState(
            toolbarTitle = toolbarTitle,
            isCustom = selectMode == SelectValidatorFlowState.ValidatorSelectMode.CUSTOM,
            searchQuery = searchQueryFlow.value,
            listState = LoadingState.Loading()
        )
    )

    private val recommendedValidators = recommendedSettings.mapNotNull {
        val settings = it ?: recommendationSettingsProvider().defaultSelectCustomSettings()
        val recommendations = validatorRecommendator().recommendations(settings)
        LoadingState.Loaded(recommendations)
    }.inBackground().stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loading())

    private val toolbarTitle: String
        get() = when (selectMode) {
            SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> resourceManager.getString(R.string.staking_select_custom)
            SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> resourceManager.getString(R.string.staking_select_suggested)
        }
    private var initiallySelected = emptySet<String>()
    init {
        setupFilters()

        subscribeOnSettings()

        viewModelScope.launch {
            selectedItems.value = setupStakingSharedState.setupStakingProcess
                .filterIsInstance<SetupStakingProcess.ReadyToSubmit.Stash>()
                .map { it.payload.blockProducers.map(Validator::accountIdHex).toSet() }
                .first()
            initiallySelected = selectedItems.value
        }

        subscribeListState()

        searchQueryFlow.onEach { query ->
            state.update { it.copy(searchQuery = query) }
        }.launchIn(viewModelScope)
    }

    private fun setupFilters() {
        settingsStorage.setDefaultSelectedFilters(emptySet())
        settingsStorage.currentFiltersSet.value = filtersSet
        settingsStorage.currentSortingSet.value = sortingSet
        viewModelScope.launch {
            val schema = settingsStorage.schema.first()
            recommendationSettingsProvider().settingsChanged(schema, BigInteger.ZERO)
        }
    }

    private fun subscribeOnSettings() = viewModelScope.launch {
        val settingsProvider = recommendationSettingsProvider.await()
        when (selectMode) {
            SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> settingsProvider.observeRecommendationSettings()
            SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> flowOf(settingsProvider.defaultSettings())
        }.collect {
            recommendedSettings.value = it
        }
    }

    private fun subscribeListState() = combine(
        recommendedValidators,
        selectedItems,
        recommendedSettings,
        searchQueryFlow
    ) { validatorsLoading, selectedValidators, settings, searchQuery ->
        val listState = validatorsLoading.map { validators ->
            val asset = interactor.currentAssetFlow().first()
            buildSegmentedValidatorsListState(
                resourceManager,
                validators,
                settings,
                searchQuery,
                selectMode,
                selectedValidators,
                asset
            )
        }
        state.update { it.copy(listState = listState) }
    }.launchIn(viewModelScope)

    override fun onNavigationClick() {
        when (selectMode) {
            SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> {

            }
            SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> {
                setupStakingSharedState.mutate {
                    if (it is SetupStakingProcess.ReadyToSubmit<*> && it.payload.selectionMethod == SetupStakingProcess.ReadyToSubmit.SelectionMethod.RECOMMENDED) {
                        it.previous()
                    } else {
                        it
                    }
                }
                router.openConfirmStaking()
            }
        }

        router.back()
    }

    override fun onSelected(item: SelectableListItemState<String>) {
        if (selectMode == SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED) {
            return
        }
        val selectedIds = selectedItems.value
        val isOverSubscribed =
            item.additionalStatuses.contains(SelectableListItemState.SelectableListItemAdditionalStatus.OVERSUBSCRIBED)
        if (isOverSubscribed && selectedIds.contains(item.id).not()) {
            openOversubscribedAlert()
        }

        val selectedListClone = selectedItems.value.toMutableList()
        if (item.id in selectedIds) {
            selectedListClone.removeIf { it == item.id }
        } else {
            selectedListClone.add(item.id)
        }
        selectedItems.value = selectedListClone.toSet()
    }

    private fun openOversubscribedAlert() {
        val payload = AlertViewState(
            title = resourceManager.getString(R.string.alert_oversubscribed_alert_title),
            message = resourceManager.getString(R.string.alert_oversubscribed_alert_message),
            buttonText = resourceManager.getString(R.string.common_close),
            iconRes = R.drawable.ic_alert_16,
            textSize = 12
        )
        router.openAlert(payload)
    }

    override fun onInfoClick(item: SelectableListItemState<String>) {
        val validator =
            recommendedValidators.value.dataOrNull()?.find { it.accountIdHex == item.id }

        router.openValidatorDetails(
            mapValidatorToValidatorDetailsParcelModel(
                requireNotNull(
                    validator
                )
            )
        )
    }

    override fun onChooseClick() {
        recommendedValidators.value.dataOrNull()?.let { allValidators ->
            when (selectMode) {
                SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> {
                    val selected = allValidators.filter { selectedItems.value.contains(it.accountIdHex) }
                    setupStakingSharedState.setCustomValidators(selected)
                    router.openReviewCustomValidators()
                }
                SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> {
                    setupStakingSharedState.setRecommendedValidators(allValidators)
                    router.openConfirmStaking()
                }
            }
        }
    }

    override fun onOptionsClick() {
        router.openValidatorsSettings()
    }

    override fun onSearchQueryInput(query: String) {
        searchQueryFlow.value = query
    }
}