package jp.co.soramitsu.staking.impl.presentation.pools.edit

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class EditPoolConfirmViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val poolSharedStateProvider: StakingPoolSharedStateProvider,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val router: StakingRouter
) : BaseConfirmViewModel(
    resourceManager = resourceManager,
    asset = poolSharedStateProvider.requireMainState.requireAsset,
    address = poolSharedStateProvider.requireMainState.requireAddress,
    titleRes = R.string.pool_edit_title,
    additionalMessageRes = R.string.pool_edit_confirm_warning,
    customIcon = R.drawable.ic_vector,
    accountNameProvider = { null },
    feeEstimator = {
        stakingPoolInteractor.estimateEditFee(poolSharedStateProvider.requireEditPoolState)
    },
    executeOperation = { address, _ ->
        stakingPoolInteractor.edit(poolSharedStateProvider.requireEditPoolState, address)
    },
    onOperationSuccess = {
        router.returnToManagePoolStake()
    }
) {
    override val tableItemsFlow: StateFlow<List<TitleValueViewState>> = feeViewStateFlow.map { feeState ->
        val chain = poolSharedStateProvider.requireMainState.requireChain
        val prefix = chain.addressPrefix.toShort()
        val editState = poolSharedStateProvider.requireEditPoolState
        val nameState = editState.newPoolName?.let { TitleValueViewState(resourceManager.getString(R.string.pool_staking_pool_name), it) }

        val rootState = createRoleState(
            editState.newRoot ?: editState.initialRoot,
            prefix,
            resourceManager.getString(R.string.pool_staking_root)
        )
        val nominatorState = createRoleState(
            editState.newNominator ?: editState.initialNominator,
            prefix,
            resourceManager.getString(R.string.pool_staking_nominator)
        )
        val stateTogglerState = createRoleState(
            editState.newStateToggler ?: editState.initialStateToggler,
            prefix,
            resourceManager.getString(R.string.pool_staking_state_toggler)
        )

        listOfNotNull(nameState, rootState, nominatorState, stateTogglerState, feeState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private suspend fun createRoleState(role: AccountId?, prefix: Short, title: String): TitleValueViewState {
        val address = role?.toAddress(prefix)
        val name = address?.let { stakingPoolInteractor.getAccountName(it) }
        val value = (name ?: address).orEmpty()
        val subValue = name?.let { address }
        return TitleValueViewState(title, value, subValue)
    }

    fun onBackClick() {
        router.back()
    }
}
