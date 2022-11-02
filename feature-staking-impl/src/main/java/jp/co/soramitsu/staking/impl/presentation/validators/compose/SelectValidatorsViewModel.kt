package jp.co.soramitsu.staking.impl.presentation.validators.compose

import android.util.Log
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.lazyAsync
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProvider
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Sorting
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.mappers.mapValidatorToValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.wallet.api.presentation.formatters.tokenAmountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val filtersSet = setOf(Filters.HavingOnChainIdentity, Filters.NotSlashedFilter, Filters.NotOverSubscribed)
private val sortingSet = setOf(Sorting.EstimatedRewards, Sorting.TotalStake, Sorting.ValidatorsOwnStake)

@HiltViewModel
class SelectValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val resourceManager: ResourceManager,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val settingsStorage: SettingsStorage
) : BaseViewModel(), SelectValidatorsScreenInterface {

    private val asset: Asset
    private val chain: Chain

    private val recommendationSettingsProvider: Deferred<RecommendationSettingsProvider<Validator>> by lazyAsync {
        recommendationSettingsProviderFactory.createRelayChain(router.currentStackEntryLifecycle)
    }

    private val selectedItems: MutableStateFlow<List<String>>
    private val selectValidatorsState = stakingPoolSharedStateProvider.requireSelectValidatorsState
    private val selectMode = selectValidatorsState.requireSelectMode
    private val recommendedSettings: MutableStateFlow<RecommendationSettings<Validator>?> = MutableStateFlow(null)
    private val validatorRecommendator by lazyAsync { validatorRecommendatorFactory.create(router.currentStackEntryLifecycle) }
    private val searchQueryFlow = MutableStateFlow("")

    init {
        Log.d("&&&", "SelectValidatorsViewModel init")
        setupFilters()
        val mainState = stakingPoolSharedStateProvider.requireMainState
        asset = mainState.requireAsset
        chain = mainState.requireChain
        selectedItems = MutableStateFlow(selectValidatorsState.selectedValidators.map { it.toHexString(false) })

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
        Log.d("&&&", "SelectValidatorsViewModel subscribeOnSettings")
    }

    private val recommendedValidators = recommendedSettings.mapNotNull { settings ->
        validatorRecommendator().recommendations(settings ?: recommendationSettingsProvider().defaultSelectCustomSettings())
    }.inBackground().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val toolbarTitle: String
        get() = when (selectMode) {
            SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> resourceManager.getString(R.string.staking_select_custom)
            SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> resourceManager.getString(R.string.staking_select_suggested)
        }

    val state = combine(recommendedValidators, selectedItems, recommendedSettings, searchQueryFlow) { validators, selectedValidators, settings, searchQuery ->
        val items = validators.filter {
            val searchQueryLowerCase = searchQuery.lowercase()
            val identityNameLowerCase = it.identity?.display?.lowercase().orEmpty()
            val addressLowerCase = it.address.lowercase()
            identityNameLowerCase.contains(searchQueryLowerCase) || addressLowerCase.contains(searchQueryLowerCase)
        }.map {
            it.toModel(it.accountIdHex in selectedValidators, settings?.sorting, asset, resourceManager)
        }
        val selectedItems = items.filter { it.isSelected }
        val listState = MultiSelectListViewState(items, selectedItems)
        SelectValidatorsScreenViewState(
            toolbarTitle = toolbarTitle,
            isCustom = selectMode == SelectValidatorFlowState.ValidatorSelectMode.CUSTOM,
            searchQuery = searchQuery,
            listState = listState
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SelectValidatorsScreenViewState(
            toolbarTitle = toolbarTitle,
            isCustom = selectMode == SelectValidatorFlowState.ValidatorSelectMode.CUSTOM,
            searchQuery = searchQueryFlow.value,
            listState = MultiSelectListViewState(
                emptyList(),
                emptyList()
            )
        )
    )

    override fun onNavigationClick() = router.back()

    override fun onSelected(item: SelectableListItemState<String>) {
        val selectedIds = selectedItems.value
        val isOverSubscribed = item.additionalStatuses.contains(SelectableListItemState.SelectableListItemAdditionalStatus.OVERSUBSCRIBED)
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
            resourceManager.getString(R.string.alert_oversubscribed_alert_title),
            resourceManager.getString(R.string.alert_oversubscribed_alert_message),
            resourceManager.getString(R.string.common_close),
            R.drawable.ic_alert_16
        )
        router.openAlert(payload)
    }

    override fun onInfoClick(item: SelectableListItemState<String>) {
        val validator = recommendedValidators.value.find { it.accountIdHex == item.id }
        router.openValidatorDetails(mapValidatorToValidatorDetailsParcelModel(requireNotNull(validator)))
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

fun Validator.toModel(
    isSelected: Boolean,
    sortingCaption: BlockProducersSorting<Validator>?,
    asset: Asset,
    resourceManager: ResourceManager
): SelectableListItemState<String> {
    val totalStake = electedInfo?.totalStake.orZero().tokenAmountFromPlanks(asset)
    val ownStake = electedInfo?.ownStake.orZero().tokenAmountFromPlanks(asset)

    val captionHeader = when (sortingCaption) {
        BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting -> resourceManager.getString(R.string.staking_filter_title_own_stake)
        else -> resourceManager.getString(R.string.staking_rewards_apy)
    }
    val captionValue = when (sortingCaption) {
        BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting -> ownStake
        else -> electedInfo?.apy.orZero().formatAsPercentage()
    }
    val captionText = buildAnnotatedString {
        withStyle(style = SpanStyle(color = black1)) {
            append("$captionHeader ")
        }
        withStyle(style = SpanStyle(color = greenText)) {
            append(captionValue)
        }
    }

    val additionalStatuses = if (this.slashed) {
        listOf(SelectableListItemState.SelectableListItemAdditionalStatus.WARNING)
    } else {
        emptyList()
    }

    return SelectableListItemState(
        id = accountIdHex,
        title = identity?.display ?: address,
        subtitle = resourceManager.getString(R.string.staking_validator_total_stake_token, totalStake),
        caption = captionText,
        isSelected = isSelected,
        additionalStatuses = additionalStatuses
    )
}
