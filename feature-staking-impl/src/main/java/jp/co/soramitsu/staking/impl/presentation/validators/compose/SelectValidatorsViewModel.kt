package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
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
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.staking.impl.presentation.validators.buildSegmentedValidatorsListState
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

private val filtersSet =
    setOf(Filters.HavingOnChainIdentity, Filters.NotSlashedFilter, Filters.NotOverSubscribed)
private val sortingSet =
    setOf(Sorting.EstimatedRewards, Sorting.TotalStake, Sorting.ValidatorsOwnStake)

@HiltViewModel
class SelectValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val resourceManager: ResourceManager,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val settingsStorage: SettingsStorage,
    private val interactor: StakingInteractor
) : BaseViewModel(), SelectValidatorsScreenInterface {

    private val asset: Asset
    private val chain: Chain

    private val recommendationSettingsProvider: Deferred<RecommendationSettingsProvider<Validator>> by lazyAsync {
        recommendationSettingsProviderFactory.createRelayChain(router.currentStackEntryLifecycle)
    }

    private val selectedItems: MutableStateFlow<List<String>>
    private val selectValidatorsState = stakingPoolSharedStateProvider.requireSelectValidatorsState
    private val selectMode = selectValidatorsState.requireSelectMode
    private val recommendedSettings: MutableStateFlow<RecommendationSettings<Validator>?> =
        MutableStateFlow(null)
    private val validatorRecommendator by lazyAsync { validatorRecommendatorFactory.create(router.currentStackEntryLifecycle) }
    private val searchQueryFlow = MutableStateFlow("")

    init {
        setupFilters()
        val mainState = stakingPoolSharedStateProvider.requireMainState
        asset = mainState.requireAsset
        chain = mainState.requireChain
        selectedItems =
            MutableStateFlow(selectValidatorsState.selectedValidators.map { it.toHexString(false) })

        subscribeOnSettings()
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

    private val recommendedValidators = recommendedSettings.mapNotNull {
        val settings = it ?: recommendationSettingsProvider().defaultSelectCustomSettings()
        val recommendations = validatorRecommendator().recommendations(settings)
        if (selectMode == SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED) {
            selectedItems.update {
                recommendations.map { it.accountIdHex }
            }
        }
        LoadingState.Loaded(recommendations)
    }.inBackground().stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loading())

    private val toolbarTitle: String
        get() = when (selectMode) {
            SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> resourceManager.getString(R.string.staking_select_custom)
            SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> resourceManager.getString(R.string.staking_select_suggested)
        }

    private val listState = combine(
        recommendedValidators,
        selectedItems,
        recommendedSettings,
        searchQueryFlow
    ) { validatorsLoading, selectedValidators, settings, searchQuery ->
        validatorsLoading.map { validators ->
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
    }

    val state = listState.map {
        SelectValidatorsScreenViewState(
            toolbarTitle = toolbarTitle,
            isCustom = selectMode == SelectValidatorFlowState.ValidatorSelectMode.CUSTOM,
            searchQuery = searchQueryFlow.value,
            listState = it
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SelectValidatorsScreenViewState(
            toolbarTitle = toolbarTitle,
            isCustom = selectMode == SelectValidatorFlowState.ValidatorSelectMode.CUSTOM,
            searchQuery = searchQueryFlow.value,
            listState = LoadingState.Loading()
        )
    )

    override fun onNavigationClick() = router.back()

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
        selectedItems.value = selectedListClone
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
        validator?.let {
            interactor.validatorDetailsCache.update { prev ->
                prev + (it.accountIdHex to mapValidatorToValidatorDetailsParcelModel(it))
            }
            router.openValidatorDetails(it.accountIdHex)
        }
    }

    override fun onChooseClick() {
        stakingPoolSharedStateProvider.selectValidatorsState.mutate {
            requireNotNull(it).copy(selectedValidators = selectedItems.value.map(String::fromHex))
        }
        router.openConfirmSelectValidators()
    }

    override fun onOptionsClick() {
        router.openValidatorsSettings()
    }

    override fun onSearchQueryInput(query: String) {
        searchQueryFlow.value = query
    }
}

