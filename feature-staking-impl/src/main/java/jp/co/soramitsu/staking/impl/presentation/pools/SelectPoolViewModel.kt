package jp.co.soramitsu.staking.impl.presentation.pools

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
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolSorting
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SingleSelectListItemViewState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SelectPoolViewModel @Inject constructor(
    poolInteractor: StakingPoolInteractor,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val router: StakingRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val chain: Chain
    private val asset: Asset

    private val selectedItem: MutableStateFlow<SelectableListItemState<Int>?>

    init {
        val setupState = requireNotNull(stakingPoolSharedStateProvider.joinFlowState.get())
        val mainState = requireNotNull(stakingPoolSharedStateProvider.mainState.get())
        chain = requireNotNull(mainState.chain)
        asset = requireNotNull(mainState.asset)
        selectedItem = MutableStateFlow(setupState.selectedPool?.toState(asset, true))
    }

    private val poolsFlow = flowOf { poolInteractor.getAllPools(chain.id) }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf())

    private val sortingFlow = MutableStateFlow(PoolSorting.TotalStake)

    private val poolItemsFlow: Flow<List<SelectableListItemState<Int>>> =
        combine(poolsFlow, sortingFlow.distinctUntilChanged { old, new -> old == new }) { pools, sorting ->
            pools.sortedByDescending {
                when (sorting) {
                    PoolSorting.TotalStake -> return@sortedByDescending it.stakedInPlanks
                    PoolSorting.NumberOfMembers -> return@sortedByDescending it.members
                }
            }.map { it.toState(asset, it.poolId.toInt() == selectedItem.value?.id) }
        }

    val viewState = combine(poolItemsFlow, selectedItem) { poolItems, selectedPool ->
        SingleSelectListItemViewState(poolItems, selectedPool)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SingleSelectListItemViewState(listOf(), null))

    private fun PoolInfo.toState(asset: Asset, isSelected: Boolean): SelectableListItemState<Int> {
        val staked = asset.token.amountFromPlanks(stakedInPlanks)
        val stakedFormatted = staked.formatTokenAmount(asset.token.configuration)
        val id = poolId.toInt()
        val stakedText = buildAnnotatedString {
            withStyle(style = SpanStyle(color = black1)) {
                append("${resourceManager.getString(R.string.pool_staking_choosepool_staked_title)} ")
            }
            withStyle(style = SpanStyle(color = greenText)) {
                append(stakedFormatted)
            }
        }
        val subtitle = resourceManager.getString(R.string.pool_staking_choosepool_members_count_title, members.toInt())
        return SelectableListItemState(id = id, title = name, subtitle = subtitle, stakedText, isSelected)
    }

    fun onBackClick() {
        router.back()
    }

    fun onPoolSelected(item: SelectableListItemState<Int>) {
        selectedItem.value = item
    }

    fun onInfoClick(item: SelectableListItemState<Int>) {
        val selectedPoolId = requireNotNull(item.id)
        val pool = requireNotNull(poolsFlow.value.find { it.poolId == selectedPoolId.toBigInteger() })
        router.openPoolInfo(pool)
    }

    fun onNextClick() {
        val setupFlow = requireNotNull(stakingPoolSharedStateProvider.joinFlowState.get())
        val selectedPoolId = requireNotNull(selectedItem.value?.id)
        val pool = requireNotNull(poolsFlow.value.find { it.poolId == selectedPoolId.toBigInteger() })

        stakingPoolSharedStateProvider.joinFlowState.set(setupFlow.copy(selectedPool = pool))

        router.openConfirmJoinPool()
    }

    fun onSortingSelected(sorting: PoolSorting) {
        sortingFlow.value = sorting
    }
}
