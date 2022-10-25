package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.wallet.api.presentation.formatters.tokenAmountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel

class SelectValidatorsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val validatorRecommendatorFactory: ValidatorRecommendatorFactory,
    private val recommendationSettingsProviderFactory: RecommendationSettingsProviderFactory,
    private val resourceManager: ResourceManager,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider
) : BaseViewModel() {

    private val asset: Asset
    private val chain: Chain

    private val selectedItems: MutableStateFlow<List<String>>
    private val selectValidatorsState = stakingPoolSharedStateProvider.requireSelectValidatorsState
    private val selectMode = selectValidatorsState.requireSelectMode

    init {
        val mainState = stakingPoolSharedStateProvider.requireMainState
        asset = mainState.requireAsset
        chain = mainState.requireChain
        selectedItems = MutableStateFlow(selectValidatorsState.selectedValidators.map { it.toHexString(false) })
    }

    private val recommendedSettings by lazyAsync {
        val settingsProvider = recommendationSettingsProviderFactory.createRelayChain(router.currentStackEntryLifecycle)
        when (selectMode) {
            SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> settingsProvider.currentSettings()
            SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> settingsProvider.defaultSettings()
        }
    }

    private val recommendedValidators = flow {
        val validatorRecommendator = validatorRecommendatorFactory.create(router.currentStackEntryLifecycle)
        val validators = validatorRecommendator.recommendations(recommendedSettings())

        emit(validators)
    }.inBackground().share()

    private val toolbarTitle: String
        get() = when (selectMode) {
            SelectValidatorFlowState.ValidatorSelectMode.CUSTOM -> resourceManager.getString(R.string.staking_select_custom)
            SelectValidatorFlowState.ValidatorSelectMode.RECOMMENDED -> resourceManager.getString(R.string.staking_select_suggested)
        }

    val state = combine(recommendedValidators, selectedItems) { validators, selectedValidators ->
        val items = validators.map {
            it.toModel(it.accountIdHex in selectedValidators)
        }
        val selectedItems = items.filter { it.isSelected }
        val listState = MultiSelectListItemViewState(items, selectedItems)
        SelectValidatorsScreenViewState(
            toolbarTitle = toolbarTitle,
            listState
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SelectValidatorsScreenViewState(
            toolbarTitle, MultiSelectListItemViewState(
                emptyList(),
                emptyList()
            )
        )
    )

    private fun Validator.toModel(isSelected: Boolean): SelectableListItemState<String> {
        val totalStake = electedInfo?.totalStake.orZero().tokenAmountFromPlanks(asset)

        val apyText = buildAnnotatedString {
            withStyle(style = SpanStyle(color = black1)) {
                append("${resourceManager.getString(R.string.staking_only_apy)} ")
            }
            withStyle(style = SpanStyle(color = greenText)) {
                append(electedInfo?.apy.orZero().formatAsPercentage())
            }
        }
        return SelectableListItemState(
            id = accountIdHex,
            title = identity?.display ?: address,
            subtitle = resourceManager.getString(R.string.staking_validator_total_stake_token, totalStake),
            caption = apyText,
            isSelected = isSelected
        )
    }

    fun onBackClicked() {
        router.back()
    }

    fun onValidatorSelected(item: SelectableListItemState<String>) {
        val selectedIds = selectedItems.value
        val selectedListClone = selectedItems.value.toMutableList()
        if (item.id in selectedIds) {
            selectedListClone.removeIf { it == item.id }
        } else {
            selectedListClone.add(item.id)
        }
        selectedItems.value = selectedListClone
    }

    fun onValidatorInfoClick(item: SelectableListItemState<String>) {

    }

    fun onCompleteClick() {
        stakingPoolSharedStateProvider.selectValidatorsState.mutate {
            requireNotNull(it).copy(selectedValidators = selectedItems.value.map(String::fromHex))
        }
        router.openConfirmSelectValidators()
    }

    fun onOptionsClick() {

    }
}
