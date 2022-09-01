package jp.co.soramitsu.staking.impl.presentation.pools

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.ShortPoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSetupFlowSharedState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolItemState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectPoolScreenViewState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SelectPoolViewModel @Inject constructor(
    resourceManager: ResourceManager,
    poolInteractor: StakingPoolInteractor,
    private val setupPoolSharedState: StakingPoolSetupFlowSharedState,
    private val router: StakingRouter
) : BaseViewModel() {

    private val chain: Chain
    private val asset: Asset

    private val selectedItem: MutableStateFlow<PoolItemState?>

    init {
        val setupState = requireNotNull(setupPoolSharedState.get())
        chain = requireNotNull(setupState.chain)
        asset = requireNotNull(setupState.asset)
        selectedItem = MutableStateFlow(setupState.selectedPool?.toState(asset, true))
    }

    private val toolbarViewState = ToolbarViewState(
        title = resourceManager.getString(R.string.pool_staking_choosepool_title),
        navigationIcon = R.drawable.ic_arrow_back_24dp,
        menuItems = listOf(MenuIconItem(R.drawable.ic_dots_horizontal_24, onClick = ::optionsClick))
    )

    private val poolItemsFlow: Flow<List<PoolItemState>> = jp.co.soramitsu.common.utils.flowOf {
        val pools = poolInteractor.getAllPools(chain.id)
        pools.map {
            it.toState(asset, it.poolId.toInt() == selectedItem.value?.id)
        }
    }

    val viewState = combine(poolItemsFlow, selectedItem) { poolItems, selectedPool ->
        SelectPoolScreenViewState(toolbarViewState, poolItems, selectedPool)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SelectPoolScreenViewState(toolbarViewState, listOf(), null))

    private fun optionsClick() {}

    private fun ShortPoolInfo.toState(asset: Asset, isSelected: Boolean): PoolItemState {
        val staked = asset.token.amountFromPlanks(stakedInPlanks)
        val stakedFormatted = staked.formatTokenAmount(asset.token.configuration)
        val id = poolId.toInt()
        return PoolItemState(id, name, members.toInt(), staked, stakedFormatted, isSelected)
    }

    private fun PoolItemState.toShortInfo(asset: Asset): ShortPoolInfo {
        val stakedInPlanks = asset.token.planksFromAmount(stakedAmount)

        return ShortPoolInfo(id.toBigInteger(), name, stakedInPlanks, membersCount.toBigInteger())
    }

    fun onBackClick() {
        router.back()
    }

    fun onPoolSelected(item: PoolItemState) {
        selectedItem.value = item
    }

    fun onInfoClick(item: PoolItemState) {}

    fun onNextClick() {
        val setupFlow = requireNotNull(setupPoolSharedState.get())
        val pool = requireNotNull(selectedItem.value).toShortInfo(asset)

        setupPoolSharedState.set(setupFlow.copy(selectedPool = pool))

        router.openConfirmJoinPool()
    }
}
