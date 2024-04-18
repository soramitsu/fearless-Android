package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConfirmJoinPoolViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val stakingPoolSharedStateProvider: StakingPoolSharedStateProvider,
    private val poolInteractor: StakingPoolInteractor,
    private val router: StakingRouter
) : BaseViewModel() {

    private val chain: Chain
    private val asset: Asset
    private val amount: BigDecimal
    private val selectedPool: PoolInfo
    private val address: String

    init {
        val setupState = requireNotNull(stakingPoolSharedStateProvider.joinFlowState.get())
        val mainState = requireNotNull(stakingPoolSharedStateProvider.mainState.get())
        chain = requireNotNull(mainState.chain)
        asset = requireNotNull(mainState.asset)
        amount = requireNotNull(setupState.amount)
        selectedPool = requireNotNull(setupState.selectedPool)
        address = requireNotNull(mainState.address)
    }

    private val toolbarViewState = ToolbarViewState(
        resourceManager.getString(R.string.common_confirm),
        R.drawable.ic_arrow_back_24dp
    )

    private val addressViewState = TitleValueViewState(
        resourceManager.getString(R.string.transaction_details_from),
        resourceManager.getString(R.string.pool_account_for_join),
        address
    )
    private val poolViewState = TitleValueViewState(
        resourceManager.getString(R.string.pool_staking_selected_pool),
        selectedPool.name,
        null
    )

    private val feeViewStateFlow = jp.co.soramitsu.common.utils.flowOf {
        val amountInPlanks = asset.token.planksFromAmount(amount)
        val feeInPlanks = poolInteractor.estimateJoinFee(amountInPlanks, selectedPool.poolId)
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatCryptoDetail(asset.token.configuration.symbol)
        val feeFiat = fee.formatFiat(asset.token.fiatSymbol)
        TitleValueViewState(
            resourceManager.getString(R.string.common_network_fee),
            feeFormatted,
            feeFiat
        )
    }
        .catch { emit(defaultFeeState) }
        .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        defaultFeeState
    )

    private val isLoadingViewState = MutableStateFlow(false)

    val viewState = combine(feeViewStateFlow, isLoadingViewState) { feeViewState, isLoading ->
        val amount = this.amount.formatCryptoDetail(asset.token.configuration.symbol)
        val validators = poolInteractor.getValidatorsIds(chain, selectedPool.poolId)

        val additionalMessage = if (validators.isEmpty()) {
            resourceManager.getString(R.string.pool_join_no_validators_message)
        } else {
            null
        }

        ConfirmJoinPoolScreenViewState(
            toolbarViewState,
            amount,
            addressViewState,
            poolViewState,
            feeViewState,
            GradientIconState.Local(R.drawable.ic_vector),
            additionalMessage,
            isLoading
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)

    fun onBackClick() {
        router.back()
    }

    fun onConfirm() {
        launch {
            isLoadingViewState.value = true
            val amountInPlanks = asset.token.planksFromAmount(amount)
            poolInteractor.joinPool(address, amountInPlanks, selectedPool.poolId).fold({
                stakingPoolSharedStateProvider.joinFlowState.complete()
                router.returnToMain()
                router.openOperationSuccess(it, chain.id)
            }, {
                showError(it)
            })
            isLoadingViewState.value = false
        }
    }

    private val defaultFeeState
        get() = TitleValueViewState(
            resourceManager.getString(R.string.common_network_fee),
            null,
            null
        )

    private val defaultScreenState
        get() = ConfirmJoinPoolScreenViewState(
            toolbarViewState,
            "... ${asset.token.configuration.symbol}",
            addressViewState,
            poolViewState,
            defaultFeeState,
            GradientIconState.Local(R.drawable.ic_vector),
            isLoading = false
        )
}
