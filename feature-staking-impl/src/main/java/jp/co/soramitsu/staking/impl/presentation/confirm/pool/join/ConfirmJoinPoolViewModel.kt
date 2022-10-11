package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
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
        val feeFormatted = fee.formatTokenAmount(asset.token.configuration)
        val feeFiat = fee.formatAsCurrency(asset.token.fiatSymbol)
        TitleValueViewState(
            resourceManager.getString(R.string.network_fee),
            feeFormatted,
            feeFiat
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        defaultFeeState
    )

    val viewState = feeViewStateFlow.map { feeViewState ->
        val amount = this.amount.formatTokenAmount(asset.token.configuration)
        ConfirmJoinPoolScreenViewState(
            toolbarViewState,
            amount,
            addressViewState,
            poolViewState,
            feeViewState,
            asset.token.configuration.iconUrl
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)

    fun onBackClick() {
        router.back()
    }

    fun onConfirm() {
        launch {
            val amountInPlanks = asset.token.planksFromAmount(amount)
            poolInteractor.joinPool(address, amountInPlanks, selectedPool.poolId).fold({
                stakingPoolSharedStateProvider.joinFlowState.complete()
                router.returnToMain()
            }, {
                showError(it)
            })
        }
    }

    private val defaultFeeState
        get() = TitleValueViewState(
            resourceManager.getString(R.string.network_fee),
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
            asset.token.configuration.iconUrl
        )
}
