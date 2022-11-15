package jp.co.soramitsu.staking.impl.presentation.pools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.DropDownViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.accountFromMapKey
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectedValidatorsFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolInfoScreenInterface
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolInfoScreenViewState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolStatusViewState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PoolInfoViewModel @Inject constructor(
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val stakingInteractor: StakingInteractor
) : BaseViewModel(), PoolInfoScreenInterface {

    companion object {
        private const val NOMINATOR_WALLET_SELECTOR_TAG = "nominator"
        private const val STATE_TOGGLER_WALLET_SELECTOR_TAG = "state_toggler"
    }

    private val chain: Chain
    private val asset: Asset
    private val poolInfo: PoolInfo
    private val staked: String
    private val stakedFiat: String?
    private val canChangeRoles: Boolean
    private val currentUserAccountId: AccountId

    init {
        val mainState = stakingPoolSharedStateProvider.requireMainState
        chain = mainState.requireChain
        asset = mainState.requireAsset
        currentUserAccountId = chain.accountIdOf(mainState.requireAddress)
        poolInfo = requireNotNull(savedStateHandle.get<PoolInfo>(PoolInfoFragment.POOL_INFO_KEY))
        canChangeRoles = poolInfo.root.contentEquals(currentUserAccountId)

        val stakedAmount = asset.token.amountFromPlanks(poolInfo.stakedInPlanks)
        staked = stakedAmount.formatTokenAmount(asset.token.configuration)
        stakedFiat = stakedAmount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        setupSelectedValidatorsSharedState()
        listenWalletSelectorResult()
    }

    private fun setupSelectedValidatorsSharedState() {
        val canChangeValidators = poolInfo.root.contentEquals(currentUserAccountId) || poolInfo.nominator.contentEquals(currentUserAccountId)
        stakingPoolSharedStateProvider.selectedValidatorsState.set(
            SelectedValidatorsFlowState(
                canChangeValidators = canChangeValidators,
                poolId = poolInfo.poolId,
                poolName = poolInfo.name
            )
        )
    }

    private fun listenWalletSelectorResult() = viewModelScope.launch {
        router.walletSelectorPayloadFlow.collect { payload ->
            payload?.let { onWalletSelected(it) }
        }
    }

    private val rolesFlow = flowOf {
        val roles = setOf(poolInfo.root, poolInfo.depositor, poolInfo.nominator, poolInfo.stateToggler).filterNotNull().toList()
        val identities = stakingPoolInteractor.getIdentities(roles)
        identities.mapNotNull {
            chain.accountFromMapKey(it.key) to it.value?.display
        }.toMap()
    }

    private val validatorsState = flowOf {
        val validators = stakingPoolInteractor.getValidatorsIds(chain, poolInfo.poolId)
        stakingPoolSharedStateProvider.selectedValidatorsState.mutate { requireNotNull(it).copy(selectedValidators = validators) }
        TitleValueViewState(resourceManager.getString(R.string.staking_recommended_title), validators.count().toString())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TitleValueViewState(resourceManager.getString(R.string.staking_recommended_title), null))
    val state: StateFlow<PoolInfoScreenViewState> = combine(rolesFlow, validatorsState) { rolesNames, validatorsState ->

        val depositor = poolInfo.depositor.roleNameOrHex(rolesNames).let {
            DropDownViewState(hint = resourceManager.getString(R.string.pool_staking_depositor), text = it)
        }
        val root = poolInfo.root.roleNameOrHex(rolesNames)?.let {
            DropDownViewState(hint = resourceManager.getString(R.string.pool_staking_root), text = it)
        }
        val nominator = poolInfo.nominator.roleNameOrHex(rolesNames)?.let {
            DropDownViewState(hint = resourceManager.getString(R.string.pool_staking_nominator), text = it, isActive = canChangeRoles)
        }
        val stateToggler = poolInfo.stateToggler.roleNameOrHex(rolesNames)?.let {
            DropDownViewState(hint = resourceManager.getString(R.string.pool_staking_state_toggler), text = it, isActive = canChangeRoles)
        }

        defaultScreenState.copy(
            depositor = depositor,
            root = root,
            nominator = nominator,
            stateToggler = stateToggler,
            validators = validatorsState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)

    private val defaultScreenState: PoolInfoScreenViewState
        get() = PoolInfoScreenViewState(
            poolId = TitleValueViewState(resourceManager.getString(R.string.pool_info_index), poolInfo.poolId.toString()),
            name = TitleValueViewState(resourceManager.getString(R.string.username_setup_choose_title), poolInfo.name),
            state = TitleValueViewState(resourceManager.getString(R.string.pool_info_state), poolInfo.state.name),
            staked = TitleValueViewState(resourceManager.getString(R.string.wallet_balance_bonded), staked, stakedFiat),
            members = TitleValueViewState(resourceManager.getString(R.string.pool_info_members), poolInfo.members.toString()),
            validators = TitleValueViewState(resourceManager.getString(R.string.staking_recommended_title), null),
            depositor = DropDownViewState(hint = resourceManager.getString(R.string.pool_staking_depositor), text = null),
            root = DropDownViewState(hint = resourceManager.getString(R.string.pool_staking_root), text = null),
            nominator = DropDownViewState(hint = resourceManager.getString(R.string.pool_staking_nominator), text = null, isActive = canChangeRoles),
            stateToggler = DropDownViewState(hint = resourceManager.getString(R.string.pool_staking_state_toggler), text = null, isActive = canChangeRoles),
            poolStatus = poolInfo.state.toViewState()
        )

    private fun NominationPoolState.toViewState() = when (this) {
        NominationPoolState.Open -> PoolStatusViewState.Active
        NominationPoolState.HasNoValidators -> PoolStatusViewState.ValidatorsAreNotSelected
        NominationPoolState.Destroying,
        NominationPoolState.Blocked -> PoolStatusViewState.Inactive
    }

    private fun AccountId?.roleNameOrHex(rolesNames: Map<String, String?>): String? {
        return this?.toHexString().let { rolesNames[it] ?: it?.requireHexPrefix() }
    }

    override fun onCloseClick() {
        router.back()
    }

    override fun onTableItemClick(identifier: Int) {
        if (identifier == PoolInfoScreenViewState.VALIDATORS_CLICK_STATE_IDENTIFIER) {
            router.openSelectedValidators()
        }
    }

    override fun onNominatorClick() {
        router.openWalletSelector(NOMINATOR_WALLET_SELECTOR_TAG)
    }

    override fun onStateTogglerClick() {
        router.openWalletSelector(STATE_TOGGLER_WALLET_SELECTOR_TAG)
    }

    private fun onWalletSelected(item: WalletSelectorPayload) {
        viewModelScope.launch {
            val (tag, selectedWalletId) = item
            val metaAccount = stakingInteractor.getMetaAccount(selectedWalletId)
            val accountId = metaAccount.accountId(chain)
            val address = metaAccount.address(chain) ?: return@launch
            when (tag) {
                NOMINATOR_WALLET_SELECTOR_TAG -> {
                    // todo implement change roles
                }
                STATE_TOGGLER_WALLET_SELECTOR_TAG -> {
                    // todo implement change roles
                }
            }
        }
    }
}
