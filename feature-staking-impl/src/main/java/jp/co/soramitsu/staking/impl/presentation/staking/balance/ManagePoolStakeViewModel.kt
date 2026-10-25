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
import jp.co.soramitsu.staking.api.domain.model.OwnPool
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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

    private val context = stakingPoolSharedStateProvider.mainState.capture()
    private val review = stakingPoolSharedStateProvider.poolContextReview(context)
    private var visible = true
    private var pendingAction: Any? = null
    private val mainState = requireNotNull(context.value)
    private val chain = mainState.requireChain
    private val asset = mainState.requireAsset
    private val accountId = mainState.accountId

    private fun managementFields(pool: OwnPool?) = StakingPoolManageFlowState(
        redeemInPlanks = pool?.redeemable.orZero(),
        claimableInPlanks = pool?.pendingRewards.orZero(),
        stakedInPlanks = pool?.myStakeInPlanks.orZero(),
        userRole = pool?.getUserRole(accountId),
        context = context
    )

    private val poolStateFlow = stakingPoolInteractor.observeCurrentPool(chain, accountId).map { it.getOrNull() }.onEach { pool ->
        review.withCurrent {
            if (visible && pendingAction == null) {
                stakingPoolSharedStateProvider.publishManageState(context, managementFields(pool))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    val state = combine(poolStateFlow, unstakingPeriodFlow) { pool, unstakingPeriod ->
        pool ?: return@combine defaultScreenViewState
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

        val available = asset.transferable?.formatCryptoDetail(asset.token.configuration.symbol)
            ?: resourceManager.getString(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable)
        val availableFiat = asset.transferable?.let(asset.token::fiatAmount)?.formatFiat(asset.token.fiatSymbol)
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
        review.withCurrent {
            if (visible) {
                review.revoke()
                pendingAction = null
                router.back()
            }
        }
    }

    fun onPaused() {
        review.withCurrent {
            visible = false
            pendingAction = null
        }
    }

    fun onResumed() {
        review.withCurrent {
            visible = true
            pendingAction = null
        }
    }

    fun onDismissed() { review.revoke() }

    override fun onCleared() {
        review.revoke()
        super.onCleared()
    }

    override fun onClaimClick() = openMemberAction(router::openPoolClaim)

    override fun onRedeemClick() = openMemberAction(router::openPoolRedeem)

    override fun onStakeMoreClick() = openMemberAction(router::openPoolBondMore)

    override fun onUnstakeClick() = openMemberAction(router::openPoolUnstake)

    private fun openMemberAction(navigate: () -> Unit) {
        openPoolAction(
            prepare = { pool -> { stakingPoolSharedStateProvider.publishManageState(context, managementFields(pool)) } },
            navigate = { navigate() }
        )
    }

    override fun onSelectValidatorsClick() {
        val state = stakingPoolSharedStateProvider.selectValidatorsState
        val original = state.capture()
        openPoolAction(
            prepare = { pool ->
                {
                    state.replaceIfCurrent(original, SelectValidatorFlowState(poolName = pool.name, poolId = pool.poolId))
                        ?.let(state::isEditable) == true
                }
            },
            navigate = { router.openStartSelectValidators() }
        )
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
        val cache = stakingPoolSharedStateProvider.poolsCache
        val original = cache.value
        openPoolAction(
            prepare = { pool ->
                val updated = original + (pool.poolId.toInt() to pool.toPoolInfo())
                val publish: () -> Boolean = {
                    if (cache.compareAndSet(original, updated)) {
                        val current = cache.value
                        current === updated || (updated == original && current === original)
                    } else false
                }
                publish
            },
            navigate = { pool -> router.openPoolInfo(pool.poolId.toInt()) }
        )
    }

    private fun onNominationsClick() {
        val state = stakingPoolSharedStateProvider.selectedValidatorsState
        val original = state.capture()
        openPoolAction(
            prepare = { pool ->
                // Load nominations for this exact pool; a cached list may belong to an earlier pool.
                val validators = stakingPoolInteractor.getValidatorsIds(chain, pool.poolId)
                val fields = SelectedValidatorsFlowState(
                    canChangeValidators = pool.root.contentEquals(accountId) || pool.nominator.contentEquals(accountId),
                    poolId = pool.poolId,
                    poolName = pool.name,
                    selectedValidators = validators
                )
                val publish: () -> Boolean = { state.replaceIfCurrent(original, fields)?.let(state::isEditable) == true }
                publish
            },
            navigate = { router.openSelectedValidators() }
        )
    }

    /** One action owns its original context, pool and destination until navigation or cancellation. */
    private fun openPoolAction(
        prepare: suspend (OwnPool) -> (() -> Boolean),
        navigate: (OwnPool) -> Unit
    ) {
        val attempt = Any()
        val admitted = review.withCurrent {
            if (visible && pendingAction == null) {
                pendingAction = attempt
                true
            } else false
        } == true
        if (!admitted) return
        viewModelScope.launch {
            var preparingPool: OwnPool? = null
            try {
                val pool = poolStateFlow.filterNotNull().first()
                preparingPool = pool
                currentCoroutineContext().ensureActive()
                // Withdrawn work must not begin a new public lookup when a held pool arrives.
                if (review.withCurrent { visible && pendingAction === attempt && poolStateFlow.value === pool } != true) {
                    return@launch
                }
                val publish = prepare(pool)
                val job = currentCoroutineContext()
                job.ensureActive()
                review.withCurrent {
                    if (visible && pendingAction === attempt && poolStateFlow.value === pool) {
                        if (publish()) {
                            // Publication may synchronously replace context, dismiss, or clear the owner.
                            job.ensureActive()
                            if (review.isCurrent() && visible && pendingAction === attempt && poolStateFlow.value === pool) {
                                visible = false
                                pendingAction = null
                                navigate(pool)
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                review.withCurrent {
                    if (visible && pendingAction === attempt && preparingPool != null && poolStateFlow.value === preparingPool) {
                        showError(error)
                    }
                }
            } finally {
                review.withCurrent { if (pendingAction === attempt) pendingAction = null }
            }
        }
    }
}
