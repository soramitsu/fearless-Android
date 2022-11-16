package jp.co.soramitsu.staking.impl.presentation.pools

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.EditPoolFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolInfoOptionsViewState

@HiltViewModel
class PoolInfoOptionsViewModel @Inject constructor(
    private val router: StakingRouter,
    private val poolSharedStateProvider: StakingPoolSharedStateProvider,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    val poolInfo = requireNotNull(savedStateHandle.get<PoolInfo>(PoolInfoOptionsFragment.POOL_INFO_KEY))
    val state = PoolInfoOptionsViewState(listOf(PoolInfoOptionsViewState.Option.Edit))

    fun onOptionSelected(option: PoolInfoOptionsViewState.Option) {
        when (option) {
            PoolInfoOptionsViewState.Option.Edit -> openEditPoolScreen()
            else -> Unit // do nothing - only one option available now
        }
    }

    private fun openEditPoolScreen() {
        poolInfo.apply {
            poolSharedStateProvider.editPoolState.set(
                EditPoolFlowState(
                    poolName = name,
                    poolId = poolId,
                    depositor = depositor,
                    root = root,
                    nominator = nominator,
                    stateToggler = stateToggler
                )
            )
        }
    }
}
