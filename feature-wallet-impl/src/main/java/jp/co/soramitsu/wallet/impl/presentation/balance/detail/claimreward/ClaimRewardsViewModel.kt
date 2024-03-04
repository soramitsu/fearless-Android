package jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.colorFromHex
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.utils.amountFromPlanks
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ClaimRewardsViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager
) : BaseViewModel(),
    ClaimRewardsScreenInterface {

    private val chainId = savedStateHandle.get<ChainId>(ClaimRewardsFragment.KEY_CHAIN_ID) ?: error("Required data not provided for reward claiming")

    private val claimRewardsRequestInProgressFlow = MutableStateFlow(false)

    private val defaultButtonState = ButtonViewState(
        resourceManager.getString(R.string.common_confirm),
        true
    )

    private val buttonStateFlow = claimRewardsRequestInProgressFlow.map { inProgress ->
        ButtonViewState(
            text = if (inProgress) "" else {
                resourceManager.getString(R.string.common_confirm)
            },
            enabled = !inProgress
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    @OptIn(ExperimentalCoroutinesApi::class)
    val assetFlow = flowOf {
        val assetChain = interactor.getChain(chainId)
        assetChain.utilityAsset?.id
    }
        .mapNotNull { it }
        .flatMapLatest { assetId ->
            interactor.assetFlow(chainId, assetId)
        }

    private val feeFlow = flowOf {
        interactor.estimateClaimRewardsFee(chainId)
    }.share()

    private val lockedAmountFlow = flowOf {
        interactor.getVestingLockedAmount(chainId)
    }.share()


    val state: StateFlow<ClaimRewardsViewState> = kotlinx.coroutines.flow.combine(
        assetFlow,
        buttonStateFlow,
        claimRewardsRequestInProgressFlow,
        feeFlow,
        lockedAmountFlow
    ) { asset, buttonState, isRequesting, fee, lockedAmount ->

        val assetModel = mapAssetToAssetModel(asset)

        val transferableAmount: BigDecimal = asset.transferable

        val lockedDecimal = lockedAmount?.let { assetModel.token.configuration.amountFromPlanks(lockedAmount) }.orZero()
        val lockedInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.vesting_locked_title),
            value = lockedDecimal.formatCryptoDetail(assetModel.token.configuration.symbol),
            additionalValue = lockedDecimal.applyFiatRate(assetModel.token.fiatRate)?.formatFiat(assetModel.token.fiatSymbol)
        )

        val transferableInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.assetdetails_balance_transferable),
            value = transferableAmount.formatCryptoDetail(assetModel.token.configuration.symbol),
            additionalValue = transferableAmount.applyFiatRate(assetModel.token.fiatRate)?.formatFiat(assetModel.token.fiatSymbol)
        )

        val feeDecimal = assetModel.token.configuration.amountFromPlanks(fee)
        val feeFormatted = feeDecimal.formatCryptoDetail(assetModel.token.configuration.symbol)
        val feeFiat = feeDecimal.applyFiatRate(assetModel.token.fiatRate)?.formatFiat(assetModel.token.fiatSymbol)

        val feeInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.common_network_fee),
            value = feeFormatted,
            additionalValue = feeFiat
        )

        val chainIconColor = asset.token.configuration.color?.colorFromHex() ?: colorAccentDark
        ClaimRewardsViewState(
            chainIconUrl = asset.token.configuration.chainIcon ?: asset.token.configuration.iconUrl,
            chainIconColor = chainIconColor,
            lockedInfoItem = lockedInfoItem,
            transferableInfoItem = transferableInfoItem,
            feeInfoItem = feeInfoItem,
            tokenSymbol = asset.token.configuration.symbol.uppercase(),
            buttonState = buttonState,
            isLoading = isRequesting
        )
    }.stateIn(this, SharingStarted.Eagerly, ClaimRewardsViewState.default)

    override fun onNavigationClick() {
        router.back()
    }

    override fun onNextClick() {
        sendClaimExtrinsic()
    }

    override fun onItemClick(code: Int) {
    }

    private fun sendClaimExtrinsic() {
        launch {
            claimRewardsRequestInProgressFlow.value = true

            runCatching {
                interactor.claimRewards(chainId)
            }
                .onSuccess { result ->
                    claimRewardsRequestInProgressFlow.value = false
                    if (result.isSuccess) {
                        val operationHash = result.getOrNull()
                        router.back()
                        router.openOperationSuccess(operationHash, chainId)
                    } else {
                        result.exceptionOrNull()?.let {
                            showError(it)
                        }
                    }
                }
                .onFailure { error ->
                    claimRewardsRequestInProgressFlow.value = false
                    showError(error)
                }
        }
    }
}
