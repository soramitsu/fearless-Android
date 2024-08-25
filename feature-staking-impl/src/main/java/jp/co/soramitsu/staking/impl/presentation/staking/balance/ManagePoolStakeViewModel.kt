package jp.co.soramitsu.staking.impl.presentation.staking.balance

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.NotificationState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.staking.api.domain.model.getUserRole
import jp.co.soramitsu.staking.api.domain.model.toPoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.SelectedValidatorsFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolManageFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.staking.balance.compose.ManagePoolStakeScreenInterface
import jp.co.soramitsu.staking.impl.presentation.staking.balance.compose.ManagePoolStakeViewState
import jp.co.soramitsu.staking.impl.presentation.staking.balance.compose.PoolStakeManagementOptions
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.HOURS_IN_DAY
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class ManagePoolStakeViewModel @Inject constructor(
    private val stakingPoolInteractor: StakingPoolInteractor,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val resourceManager: ResourceManager,
    private val relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val router: StakingRouter
) : BaseViewModel(), ManagePoolStakeScreenInterface {

    private val mainState = stakingPoolSharedStateProvider.requireMainState
    private val chain = mainState.requireChain
    private val asset = mainState.requireAsset
    private val accountId = mainState.accountId

    private val poolStateFlow = stakingPoolInteractor.observeCurrentPool(chain, accountId).onEach { pool ->

        stakingPoolSharedStateProvider.manageState.mutate {
            StakingPoolManageFlowState(
                redeemInPlanks = pool?.redeemable.orZero(),
                claimableInPlanks = pool?.pendingRewards.orZero(),
                stakedInPlanks = pool?.myStakeInPlanks.orZero(),
                userRole = pool?.getUserRole(accountId)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val validatorIdsFlow = poolStateFlow.filterNotNull()
        .map { pool -> stakingPoolInteractor.getValidatorsIds(chain, pool.poolId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val defaultAvailableState = TitleValueViewState(
        resourceManager.getString(R.string.wallet_balance_available)
    )

    private val defaultUnstakingState = TitleValueViewState(
        resourceManager.getString(R.string.wallet_balance_unbonding_v1_9_0)
    )

    private val defaultPoolInfoState = TitleValueViewState(
        resourceManager.getString(R.string.pool_staking_pool_info),
        null
    )

    private val defaultTimeBeforeRedeemState = TitleValueViewState(
        resourceManager.getString(R.string.pool_time_before_redeem),
        null
    )

    private val defaultScreenViewState = ManagePoolStakeViewState(
        null,
        null,
        null,
        null,
        defaultAvailableState,
        defaultUnstakingState,
        defaultPoolInfoState,
        defaultTimeBeforeRedeemState,
        true
    )

    private val unstakingPeriodFlow = jp.co.soramitsu.common.utils.flowOf {
        val lockupPeriodInHours = relayChainScenarioInteractor.unstakingPeriod()
        if (lockupPeriodInHours > HOURS_IN_DAY) {
            val inDays = lockupPeriodInHours / HOURS_IN_DAY
            resourceManager.getQuantityString(R.plurals.common_days_format, inDays, inDays)
        } else {
            resourceManager.getQuantityString(R.plurals.common_hours_format, lockupPeriodInHours, lockupPeriodInHours)
        }
    }

    val state = combine(poolStateFlow.filterNotNull(), unstakingPeriodFlow) { pool, unstakingPeriod ->
        val isFullUnstake = pool.myStakeInPlanks == BigInteger.ZERO
        val total = asset.token.amountFromPlanks(pool.myStakeInPlanks)
        val totalFormatted = total.formatCryptoDetail(asset.token.configuration.symbol)

        val hasRewardsForClaim = pool.pendingRewards > BigInteger.ZERO
        val claimable = asset.token.amountFromPlanks(pool.pendingRewards).formatCryptoDetail(asset.token.configuration.symbol)
        val claimNotification = if (hasRewardsForClaim) {
            NotificationState(
                R.drawable.ic_status_warning_16,
                resourceManager.getString(R.string.pool_claim_reward),
                claimable,
                resourceManager.getString(R.string.common_claim),
                colorAccent
            )
        } else {
            null
        }
        val redeemableNotification = pool.redeemable.takeIf { it > BigInteger.ZERO }?.let { redeemable ->
            val redeemableFormatted = asset.token.amountFromPlanks(redeemable).formatCryptoDetail(asset.token.configuration.symbol)
            NotificationState(
                R.drawable.ic_status_warning_16,
                resourceManager.getString(R.string.pool_redeem),
                redeemableFormatted,
                resourceManager.getString(R.string.staking_redeem),
                colorAccent
            )
        }
        val canNominate = accountId.contentEquals(pool.nominator) || accountId.contentEquals(pool.root)
        val selectValidatorsNotification = pool.state == NominationPoolState.HasNoValidators && canNominate
        val noValidatorsNotification = if (selectValidatorsNotification) {
            NotificationState(
                R.drawable.ic_status_warning_16,
                resourceManager.getString(R.string.pool_select_validators_notification_title),
                resourceManager.getString(R.string.pool_select_validators_notification_message),
                resourceManager.getString(R.string.common_select),
                colorAccent
            )
        } else {
            null
        }

        val available = asset.transferable.formatCryptoDetail(asset.token.configuration.symbol)
        val availableFiat = asset.transferable.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)
        val availableState = defaultAvailableState.copy(value = available, additionalValue = availableFiat)

        val unstaking = asset.token.amountFromPlanks(pool.unbonding)
        val unstakingFormatted = unstaking.formatCryptoDetail(asset.token.configuration.symbol)
        val unstakingFiat = unstaking.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)
        val unstakingState = defaultUnstakingState.copy(value = unstakingFormatted, additionalValue = unstakingFiat)

        val poolInfoViewState = defaultPoolInfoState.copy(value = pool.name ?: "Pool #${pool.poolId}")

        val timeBeforeRedeemState = defaultTimeBeforeRedeemState.copy(value = unstakingPeriod)

        ManagePoolStakeViewState(
            total = totalFormatted,
            claimNotification = claimNotification,
            redeemNotification = redeemableNotification,
            noValidatorsNotification = noValidatorsNotification,
            available = availableState,
            unstaking = unstakingState,
            poolInfo = poolInfoViewState,
            timeBeforeRedeem = timeBeforeRedeemState,
            isFullUnstake = isFullUnstake
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenViewState)

    override fun onBackClick() {
        router.back()
    }

    override fun onClaimClick() {
        router.openPoolClaim()
    }

    override fun onRedeemClick() {
        router.openPoolRedeem()
    }

    override fun onStakeMoreClick() {
        router.openPoolBondMore()
    }

    override fun onUnstakeClick() {
        router.openPoolUnstake()
    }

    override fun onSelectValidatorsClick() {
        viewModelScope.launch {
            val pool = requireNotNull(poolStateFlow.first { it != null })
            stakingPoolSharedStateProvider.selectValidatorsState.set(
                SelectValidatorFlowState(
                    poolName = pool.name,
                    poolId = pool.poolId
                )
            )
            router.openStartSelectValidators()
        }
    }

    override fun onBottomSheetOptionSelected(option: PoolStakeManagementOptions) {
        when (option) {
            PoolStakeManagementOptions.Nominations -> onNominationsClick()
            PoolStakeManagementOptions.PoolInfo -> onPoolInfoClick()
        }
    }

    override fun onInfoTableItemSelected(itemIdentifier: Int) {
        if (itemIdentifier == ManagePoolStakeViewState.POOL_INFO_CLICK_IDENTIFIER) {
            onPoolInfoClick()
        }
    }

    private fun onPoolInfoClick() {
        viewModelScope.launch {
            val pool = requireNotNull(poolStateFlow.first { it != null })
            stakingPoolSharedStateProvider.poolsCache.update { prevState ->
                prevState + (pool.poolId.toInt() to pool.toPoolInfo())
            }
            router.openPoolInfo(pool.poolId.toInt())
        }
    }

    private fun onNominationsClick() {
        viewModelScope.launch {
            val pool = poolStateFlow.first { it != null } ?: return@launch

            val canChangeValidators = pool.root.contentEquals(accountId) || pool.nominator.contentEquals(accountId)
            stakingPoolSharedStateProvider.selectedValidatorsState.set(
                SelectedValidatorsFlowState(
                    canChangeValidators = canChangeValidators,
                    poolId = pool.poolId,
                    poolName = pool.name,
                    selectedValidators = validatorIdsFlow.value
                )
            )
            router.openSelectedValidators()
        }
    }
}
