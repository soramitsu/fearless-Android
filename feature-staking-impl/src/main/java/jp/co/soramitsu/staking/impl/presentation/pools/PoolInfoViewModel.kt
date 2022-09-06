package jp.co.soramitsu.staking.impl.presentation.pools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.accountFromMapKey
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSetupFlowSharedState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolInfoScreenViewState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PoolInfoViewModel @Inject constructor(
    setupPoolSharedState: StakingPoolSetupFlowSharedState,
    savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val stakingPoolInteractor: StakingPoolInteractor
) : BaseViewModel() {

    private val chain: Chain
    private val asset: Asset
    private val poolInfo: PoolInfo
    private val staked: String
    private val stakedFiat: String?

    init {
        val setupState = requireNotNull(setupPoolSharedState.get())
        chain = requireNotNull(setupState.chain)
        asset = requireNotNull(setupState.asset)
        poolInfo = requireNotNull(savedStateHandle.get<PoolInfo>(PoolInfoFragment.POOL_INFO_KEY))
        val stakedAmount = asset.token.amountFromPlanks(poolInfo.stakedInPlanks)
        staked = stakedAmount.formatTokenAmount(asset.token.configuration)
        stakedFiat = stakedAmount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)
    }

    private val rolesFlow = flowOf {
        val roles = setOf(poolInfo.root, poolInfo.depositor, poolInfo.nominator, poolInfo.stateToggler).filterNotNull().toList()
        val identities = stakingPoolInteractor.getIdentities(roles)
        identities.mapNotNull {
            chain.accountFromMapKey(it.key) to it.value?.display
        }.toMap()
    }

    val state: StateFlow<PoolInfoScreenViewState> = rolesFlow.map { rolesNames ->
        val depositor = poolInfo.depositor.roleNameOrHex(rolesNames)?.let {
            TitleValueViewState(resourceManager.getString(R.string.pool_staking_depositor), it)
        }
        val root = poolInfo.root.roleNameOrHex(rolesNames)?.let {
            TitleValueViewState(resourceManager.getString(R.string.pool_staking_root), it)
        }
        val nominator = poolInfo.nominator.roleNameOrHex(rolesNames)?.let {
            TitleValueViewState(resourceManager.getString(R.string.pool_staking_nominator), it)
        }
        val stateToggler = poolInfo.stateToggler.roleNameOrHex(rolesNames)?.let {
            TitleValueViewState(resourceManager.getString(R.string.pool_staking_state_toggler), it)
        }

        defaultScreenState.copy(
            depositor = depositor,
            root = root,
            nominator = nominator,
            stateToggler = stateToggler
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)

    private val defaultScreenState: PoolInfoScreenViewState
        get() = PoolInfoScreenViewState(
            poolId = TitleValueViewState(resourceManager.getString(R.string.pool_info_index), poolInfo.poolId.toString()),
            name = TitleValueViewState(resourceManager.getString(R.string.username_setup_choose_title), poolInfo.name),
            state = TitleValueViewState(resourceManager.getString(R.string.pool_info_state), poolInfo.state.name),
            staked = TitleValueViewState(resourceManager.getString(R.string.wallet_balance_bonded), staked, stakedFiat),
            members = TitleValueViewState(resourceManager.getString(R.string.pool_info_members), poolInfo.members.toString()),
            depositor = TitleValueViewState(resourceManager.getString(R.string.pool_staking_depositor), null),
            root = TitleValueViewState(resourceManager.getString(R.string.pool_staking_root), null),
            nominator = TitleValueViewState(resourceManager.getString(R.string.pool_staking_nominator), null),
            stateToggler = TitleValueViewState(resourceManager.getString(R.string.pool_staking_state_toggler), null)
        )

    private fun AccountId?.roleNameOrHex(rolesNames: Map<String, String?>): String? {
        return this?.toHexString().let { rolesNames[it] ?: it?.requireHexPrefix() }
    }

    fun onCloseClick() {
        router.back()
    }
}
