package jp.co.soramitsu.liquiditypools.impl.presentation.demeter

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.theme.black50
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@Immutable
data class DemeterFarmingState(
    val loading: Boolean = true,
    val positions: List<DemeterFarmingPool> = emptyList(),
    val availablePools: List<DemeterFarmingBasicPool> = emptyList(),
    val depositCapabilityReason: String? = null,
    val withdrawCapabilityReason: String? = null,
    val claimCapabilityReason: String? = null,
    val actionKey: String? = null,
    val message: String? = null
)

private data class DemeterRefreshResult(
    val positions: List<DemeterFarmingPool>,
    val availablePools: List<DemeterFarmingBasicPool>,
    val depositCapabilityReason: String?,
    val withdrawCapabilityReason: String?,
    val claimCapabilityReason: String?
)

@HiltViewModel
class DemeterFarmingViewModel @Inject constructor(
    private val interactor: DemeterFarmingInteractor
) : BaseViewModel() {
    private val mutableState = MutableStateFlow(DemeterFarmingState())
    val state: StateFlow<DemeterFarmingState> = mutableState

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, message = null) }
            runCatching {
                if (force) interactor.refresh(soraMainChainId)
                coroutineScope {
                    val positions = async { interactor.getFarmedPools(soraMainChainId).orEmpty() }
                    val available = async { interactor.getAvailablePools(soraMainChainId) }
                    val depositCapability = async {
                        interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Deposit)
                    }
                    val withdrawCapability = async {
                        interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Withdraw)
                    }
                    val claimCapability = async {
                        interactor.mutationCapabilityReason(soraMainChainId, DemeterMutationAction.Claim)
                    }
                    DemeterRefreshResult(
                        positions = positions.await(),
                        availablePools = available.await(),
                        depositCapabilityReason = depositCapability.await(),
                        withdrawCapabilityReason = withdrawCapability.await(),
                        claimCapabilityReason = claimCapability.await()
                    )
                }
            }.onSuccess { result ->
                mutableState.value = DemeterFarmingState(
                    loading = false,
                    positions = result.positions,
                    availablePools = result.availablePools.sortedByDescending { it.apr },
                    depositCapabilityReason = result.depositCapabilityReason,
                    withdrawCapabilityReason = result.withdrawCapabilityReason,
                    claimCapabilityReason = result.claimCapabilityReason
                )
            }.onFailure { error ->
                val reason = error.message ?: "Demeter farming is unavailable."
                mutableState.value = DemeterFarmingState(
                    loading = false,
                    depositCapabilityReason = reason,
                    withdrawCapabilityReason = reason,
                    claimCapabilityReason = reason
                )
            }
        }
    }

    fun deposit(pool: DemeterFarmingBasicPool, amount: String) {
        mutate(pool.key("deposit"), DemeterMutationAction.Deposit) {
            interactor.deposit(soraMainChainId, pool, amount.decimalAmount())
        }
    }

    fun withdraw(pool: DemeterFarmingPool, amount: String) {
        mutate(pool.key("withdraw"), DemeterMutationAction.Withdraw) {
            interactor.withdraw(soraMainChainId, pool, amount.decimalAmount())
        }
    }

    fun claim(pool: DemeterFarmingPool) {
        mutate(pool.key("claim"), DemeterMutationAction.Claim) {
            interactor.claimRewards(soraMainChainId, pool)
        }
    }

    private fun mutate(
        key: String,
        mutationAction: DemeterMutationAction,
        action: suspend () -> Result<String>
    ) {
        if (mutableState.value.capabilityReason(mutationAction) != null) return
        viewModelScope.launch {
            interactor.mutationCapabilityReason(soraMainChainId, mutationAction)?.let { reason ->
                mutableState.update { it.withCapabilityReason(mutationAction, reason).copy(message = reason) }
                return@launch
            }
            mutableState.update { it.copy(actionKey = key, message = null) }
            action().onSuccess { hash ->
                mutableState.update { it.copy(actionKey = null, message = "Transaction submitted: $hash") }
                refresh()
            }.onFailure { error ->
                mutableState.update { it.copy(actionKey = null, message = error.message ?: "Transaction failed") }
            }
        }
    }

    private fun DemeterFarmingState.capabilityReason(action: DemeterMutationAction): String? = when (action) {
        DemeterMutationAction.Deposit -> depositCapabilityReason
        DemeterMutationAction.Withdraw -> withdrawCapabilityReason
        DemeterMutationAction.Claim -> claimCapabilityReason
    }

    private fun DemeterFarmingState.withCapabilityReason(
        action: DemeterMutationAction,
        reason: String
    ): DemeterFarmingState = when (action) {
        DemeterMutationAction.Deposit -> copy(depositCapabilityReason = reason)
        DemeterMutationAction.Withdraw -> copy(withdrawCapabilityReason = reason)
        DemeterMutationAction.Claim -> copy(claimCapabilityReason = reason)
    }

    private fun String.decimalAmount(): BigDecimal = trim().toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?: throw IllegalArgumentException("Enter a positive amount")

    private fun DemeterFarmingBasicPool.key(action: String) =
        "$action:${tokenBase.token.configuration.currencyId}:${tokenTarget.token.configuration.currencyId}:${tokenReward.token.configuration.currencyId}"

    private fun DemeterFarmingPool.key(action: String) =
        "$action:${tokenBase.token.configuration.currencyId}:${tokenTarget.token.configuration.currencyId}:${tokenReward.token.configuration.currencyId}"
}

@AndroidEntryPoint
class DemeterFarmingFragment : BaseComposeFragment<DemeterFarmingViewModel>() {
    override val viewModel: DemeterFarmingViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        DemeterFarmingScreen(
            state = state,
            scrollState = scrollState,
            onRefresh = { viewModel.refresh() },
            onDeposit = viewModel::deposit,
            onWithdraw = viewModel::withdraw,
            onClaim = viewModel::claim
        )
    }
}

@Composable
private fun DemeterFarmingScreen(
    state: DemeterFarmingState,
    scrollState: ScrollState,
    onRefresh: () -> Unit,
    onDeposit: (DemeterFarmingBasicPool, String) -> Unit,
    onWithdraw: (DemeterFarmingPool, String) -> Unit,
    onClaim: (DemeterFarmingPool) -> Unit
) {
    val inputs = remember { mutableStateMapOf<String, String>() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        H1(text = "Demeter farming")
        B0(text = "SORA farms. Deposits and rewards use SORA runtime calls; network fees are paid in XOR.", color = white50)
        state.depositCapabilityReason?.let { B0(text = "Deposit unavailable: $it", color = white50) }
        state.withdrawCapabilityReason?.let { B0(text = "Withdraw unavailable: $it", color = white50) }
        state.claimCapabilityReason?.let { B0(text = "Claim unavailable: $it", color = white50) }
        state.message?.let { B0(text = it, color = white) }
        GrayButton(
            modifier = Modifier.fillMaxWidth(),
            text = if (state.loading) "Refreshing…" else "Refresh pools",
            enabled = !state.loading,
            onClick = onRefresh
        )

        H3(text = "Your positions")
        if (!state.loading && state.positions.isEmpty()) {
            B0(text = "No active farming positions.", color = white50)
        }
        state.positions.forEach { position ->
            val rowKey = "${position.tokenBase.token.configuration.currencyId}:${position.tokenTarget.token.configuration.currencyId}:${position.tokenReward.token.configuration.currencyId}"
            FarmingSurface {
                H3(text = "${position.tokenBase.token.configuration.symbol} / ${position.tokenTarget.token.configuration.symbol}")
                B0(text = "APR ${"%.2f".format(position.apr)}% · Deposited ${position.amount.toPlainString()}", color = white50)
                B0(text = "Rewards ${position.amountReward.toPlainString()} ${position.tokenReward.token.configuration.symbol}", color = white50)
                AmountInput(
                    value = inputs["withdraw:$rowKey"].orEmpty(),
                    label = "Amount to withdraw",
                    onValueChange = { inputs["withdraw:$rowKey"] = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GrayButton(
                        modifier = Modifier.weight(1f),
                        text = "Withdraw",
                        enabled = state.withdrawCapabilityReason == null && state.actionKey == null,
                        onClick = { onWithdraw(position, inputs["withdraw:$rowKey"].orEmpty()) }
                    )
                    AccentButton(
                        modifier = Modifier.weight(1f),
                        text = "Claim",
                        enabled = state.claimCapabilityReason == null && state.actionKey == null &&
                            position.amountReward > BigDecimal.ZERO,
                        onClick = { onClaim(position) }
                    )
                }
            }
        }

        H3(text = "Available farms")
        if (!state.loading && state.availablePools.isEmpty()) {
            B0(text = "No active Demeter farms are available on SORA.", color = white50)
        }
        state.availablePools.forEach { pool ->
            val rowKey = "${pool.tokenBase.token.configuration.currencyId}:${pool.tokenTarget.token.configuration.currencyId}:${pool.tokenReward.token.configuration.currencyId}"
            FarmingSurface {
                H3(text = "${pool.tokenBase.token.configuration.symbol} / ${pool.tokenTarget.token.configuration.symbol}")
                B0(text = "APR ${"%.2f".format(pool.apr)}% · TVL ${pool.tvl.toPlainString()} · Deposit fee ${"%.2f".format(pool.fee)}%", color = white50)
                B0(text = "Rewards in ${pool.tokenReward.token.configuration.symbol}", color = white50)
                AmountInput(
                    value = inputs["deposit:$rowKey"].orEmpty(),
                    label = "LP amount to deposit",
                    onValueChange = { inputs["deposit:$rowKey"] = it }
                )
                AccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Deposit",
                    enabled = state.depositCapabilityReason == null && state.actionKey == null,
                    onClick = { onDeposit(pool, inputs["deposit:$rowKey"].orEmpty()) }
                )
            }
        }
    }
}

@Composable
private fun FarmingSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(black50, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun AmountInput(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = { candidate ->
            if (candidate.isEmpty() || candidate.matches(Regex("^[0-9]*([.][0-9]*)?$"))) {
                onValueChange(candidate)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
