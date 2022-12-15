package jp.co.soramitsu.staking.impl.presentation.pools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.DropDownViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.accountFromMapKey
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
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

@HiltViewModel
class PoolInfoViewModel @Inject constructor(
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val router: StakingRouter,
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val clipboardManager: ClipboardManager
) : BaseViewModel(), PoolInfoScreenInterface {

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
        TitleValueViewState(resourceManager.getString(R.string.staking_recommended_title), validators.size.toString()) to validators.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TitleValueViewState(resourceManager.getString(R.string.staking_recommended_title), null) to false)

    val state: StateFlow<PoolInfoScreenViewState> = combine(rolesFlow, validatorsState) { rolesNames, validatorsStatePair ->
        val (validatorsState, hasValidators) = validatorsStatePair
        val depositor = poolInfo.depositor.roleNameOrAddress(rolesNames).let {
            DropDownViewState(
                hint = resourceManager.getString(R.string.pool_staking_depositor),
                text = it,
                clickableMode = DropDownViewState.ClickableMode.AlwaysClickable,
                endIcon = R.drawable.ic_copy_16
            )
        }
        val root = poolInfo.root.roleNameOrAddress(rolesNames)?.let {
            DropDownViewState(
                hint = resourceManager.getString(R.string.pool_staking_root),
                text = it,
                clickableMode = DropDownViewState.ClickableMode.AlwaysClickable,
                endIcon = R.drawable.ic_copy_16
            )
        }
        val nominator = poolInfo.nominator.roleNameOrAddress(rolesNames)?.let {
            DropDownViewState(
                hint = resourceManager.getString(R.string.pool_staking_nominator),
                text = it,
                clickableMode = DropDownViewState.ClickableMode.AlwaysClickable,
                endIcon = R.drawable.ic_copy_16
            )
        }
        val stateToggler = poolInfo.stateToggler.roleNameOrAddress(rolesNames)?.let {
            DropDownViewState(
                hint = resourceManager.getString(R.string.pool_staking_state_toggler),
                text = it,
                clickableMode = DropDownViewState.ClickableMode.AlwaysClickable,
                endIcon = R.drawable.ic_copy_16
            )
        }

        val state = when {
            poolInfo.state == NominationPoolState.Open && hasValidators.not() -> NominationPoolState.HasNoValidators
            else -> poolInfo.state
        }

        defaultScreenState.copy(
            depositor = depositor,
            root = root,
            nominator = nominator,
            stateToggler = stateToggler,
            validators = validatorsState,
            state = TitleValueViewState(resourceManager.getString(R.string.pool_info_state), state.name),
            poolStatus = state.toViewState()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)

    private val defaultScreenState: PoolInfoScreenViewState
        get() = PoolInfoScreenViewState(
            poolId = TitleValueViewState(resourceManager.getString(R.string.pool_info_index), poolInfo.poolId.toString()),
            name = TitleValueViewState(resourceManager.getString(R.string.username_setup_choose_title), poolInfo.name),
            state = TitleValueViewState(resourceManager.getString(R.string.pool_info_state), null),
            staked = TitleValueViewState(resourceManager.getString(R.string.wallet_balance_bonded), staked, stakedFiat),
            members = TitleValueViewState(resourceManager.getString(R.string.pool_info_members), poolInfo.members.toString()),
            validators = TitleValueViewState(resourceManager.getString(R.string.staking_recommended_title), null),
            depositor = DropDownViewState(
                hint = resourceManager.getString(R.string.pool_staking_depositor),
                text = null,
                clickableMode = DropDownViewState.ClickableMode.AlwaysClickable,
                endIcon = R.drawable.ic_copy_16
            ),
            root = DropDownViewState(
                hint = resourceManager.getString(R.string.pool_staking_root),
                text = null,
                clickableMode = DropDownViewState.ClickableMode.AlwaysClickable,
                endIcon = R.drawable.ic_copy_16
            ),
            nominator = DropDownViewState(
                hint = resourceManager.getString(R.string.pool_staking_nominator),
                text = null,
                clickableMode = DropDownViewState.ClickableMode.AlwaysClickable,
                endIcon = R.drawable.ic_copy_16
            ),
            stateToggler = DropDownViewState(
                hint = resourceManager.getString(R.string.pool_staking_state_toggler),
                text = null,
                clickableMode = DropDownViewState.ClickableMode.AlwaysClickable,
                endIcon = R.drawable.ic_copy_16
            ),
            poolStatus = null,
            showOptions = canChangeRoles
        )

    private fun NominationPoolState.toViewState() = when (this) {
        NominationPoolState.Open -> PoolStatusViewState.Active
        NominationPoolState.HasNoValidators -> PoolStatusViewState.ValidatorsAreNotSelected
        NominationPoolState.Destroying,
        NominationPoolState.Blocked -> PoolStatusViewState.Inactive
    }

    private fun AccountId?.roleNameOrAddress(rolesNames: Map<String, String?>): String? {
        this ?: return null
        val address = this.toAddress(chain.addressPrefix.toShort())
        return rolesNames[this.toHexString()] ?: address
    }

    override fun onCloseClick() {
        router.back()
    }

    override fun onTableItemClick(identifier: Int) {
        if (identifier == PoolInfoScreenViewState.VALIDATORS_CLICK_STATE_IDENTIFIER) {
            router.openSelectedValidators()
        }
    }

    override fun onDepositorClick() {
        val address = poolInfo.depositor.toAddress(chain.addressPrefix.toShort())
        copyToClipboard(address)
    }

    override fun onRootClick() {
        val address = poolInfo.root?.toAddress(chain.addressPrefix.toShort()) ?: return
        copyToClipboard(address)
    }

    override fun onNominatorClick() {
        val address = poolInfo.nominator?.toAddress(chain.addressPrefix.toShort()) ?: return
        copyToClipboard(address)
    }

    override fun onStateTogglerClick() {
        val address = poolInfo.stateToggler?.toAddress(chain.addressPrefix.toShort()) ?: return
        copyToClipboard(address)
    }

    private fun copyToClipboard(text: String) {
        clipboardManager.addToClipboard(text)
        val message = resourceManager.getString(R.string.common_copied)
        showMessage(message)
    }

    override fun onOptionsClick() {
        router.openPoolInfoOptions(poolInfo)
    }
}
