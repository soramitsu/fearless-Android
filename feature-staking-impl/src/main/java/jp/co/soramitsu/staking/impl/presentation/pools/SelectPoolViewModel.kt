package jp.co.soramitsu.staking.impl.presentation.pools

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolItemState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolSorting
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectPoolScreenViewState
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
    private val router: StakingRouter
) : BaseViewModel() {

    private val chain: Chain
    private val asset: Asset

    private val selectedItem: MutableStateFlow<PoolItemState?>

    init {
        val setupState = requireNotNull(stakingPoolSharedStateProvider.joinFlowState.get())
        val mainState = requireNotNull(stakingPoolSharedStateProvider.mainState.get())
        chain = requireNotNull(mainState.chain)
        asset = requireNotNull(mainState.asset)
        selectedItem = MutableStateFlow(setupState.selectedPool?.toState(asset, true))
    }

    private val poolsFlow = flowOf { poolInteractor.getAllPools(chain.id) }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf())

    private val sortingFlow = MutableStateFlow(PoolSorting.TotalStake)

    private val poolItemsFlow: Flow<List<PoolItemState>> = combine(poolsFlow, sortingFlow.distinctUntilChanged { old, new -> old == new }) { pools, sorting ->
        pools.sortedByDescending {
            when (sorting) {
                PoolSorting.TotalStake -> return@sortedByDescending it.stakedInPlanks
                PoolSorting.NumberOfMembers -> return@sortedByDescending it.members
            }
        }.map { it.toState(asset, it.poolId.toInt() == selectedItem.value?.id) }
    }

    val viewState = combine(poolItemsFlow, selectedItem) { poolItems, selectedPool ->
        SelectPoolScreenViewState(poolItems, selectedPool)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SelectPoolScreenViewState(listOf(), null))

    private fun PoolInfo.toState(asset: Asset, isSelected: Boolean): PoolItemState {
        val staked = asset.token.amountFromPlanks(stakedInPlanks)
        val stakedFormatted = staked.formatTokenAmount(asset.token.configuration)
        val id = poolId.toInt()
        return PoolItemState(id, name, members.toInt(), staked, stakedFormatted, isSelected)
    }

    fun onBackClick() {
        router.back()
    }

    fun onPoolSelected(item: PoolItemState) {
        selectedItem.value = item
    }

    fun onInfoClick(item: PoolItemState) {
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
