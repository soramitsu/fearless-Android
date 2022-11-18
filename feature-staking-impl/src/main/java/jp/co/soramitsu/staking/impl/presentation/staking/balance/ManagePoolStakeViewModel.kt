package jp.co.soramitsu.staking.impl.presentation.staking.balance

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.NotificationState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.staking.api.domain.model.toPoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolManageFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.staking.balance.compose.ManagePoolStakeScreenInterface
import jp.co.soramitsu.staking.impl.presentation.staking.balance.compose.ManagePoolStakeViewState
import jp.co.soramitsu.staking.impl.presentation.staking.balance.compose.PoolStakeManagementOptions
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.HOURS_IN_DAY
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ManagePoolStakeViewModel @Inject constructor(
    stakingPoolInteractor: StakingPoolInteractor,
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
                pool?.redeemable.orZero(),
                pool?.pendingRewards.orZero(),
                pool?.myStakeInPlanks.orZero()
            )
        }
    }

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
        false
    )

    private val unstakingPeriodFlow = jp.co.soramitsu.common.utils.flowOf {
        val lockupPeriodInHours = relayChainScenarioInteractor.unstakingPeriod()
        if (lockupPeriodInHours > HOURS_IN_DAY) {
            val inDays = lockupPeriodInHours / HOURS_IN_DAY
            resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, inDays, inDays)
        } else {
            resourceManager.getQuantityString(R.plurals.common_hours_format, lockupPeriodInHours, lockupPeriodInHours)
        }
    }

    val state = combine(poolStateFlow.filterNotNull(), unstakingPeriodFlow) { pool, unstakingPeriod ->
        val isFullUnstake = pool.myStakeInPlanks == BigInteger.ZERO
        val total = asset.token.amountFromPlanks(pool.myStakeInPlanks)
        val totalFormatted = total.formatTokenAmount(asset.token.configuration)

        val hasRewardsForClaim = pool.pendingRewards > BigInteger.ZERO
        val claimable = asset.token.amountFromPlanks(pool.pendingRewards).formatTokenAmount(asset.token.configuration)
        val claimNotification = if (hasRewardsForClaim) {
            NotificationState(R.drawable.ic_status_warning_16, R.string.pool_claim_reward, claimable, R.string.common_claim, colorAccent)
        } else {
            null
        }
        val redeemableNotification = pool.redeemable.takeIf { it > BigInteger.ZERO }?.let { redeemable ->
            val redeemableFormatted = asset.token.amountFromPlanks(redeemable).formatTokenAmount(asset.token.configuration)
            NotificationState(
                R.drawable.ic_status_warning_16,
                R.string.pool_redeem,
                redeemableFormatted,
                R.string.staking_redeem,
                colorAccent
            )
        }
        val canNominate = accountId.contentEquals(pool.nominator) || accountId.contentEquals(pool.root)
        val selectValidatorsNotification = pool.state == NominationPoolState.HasNoValidators && canNominate
        val noValidatorsNotification = if (selectValidatorsNotification) {
            NotificationState(
                R.drawable.ic_status_warning_16,
                R.string.pool_select_validators_notification_title,
                resourceManager.getString(R.string.pool_select_validators_notification_message),
                R.string.common_select,
                colorAccent
            )
        } else {
            null
        }

        val available = asset.transferable.formatTokenAmount(asset.token.configuration)
        val availableFiat = asset.transferable.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)
        val availableState = defaultAvailableState.copy(value = available, additionalValue = availableFiat)

        val unstaking = asset.token.amountFromPlanks(pool.unbonding)
        val unstakingFormatted = unstaking.formatTokenAmount(asset.token.configuration)
        val unstakingFiat = unstaking.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)
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
            router.openPoolInfo(pool.toPoolInfo())
        }
    }

    private fun onNominationsClick() {}
}
