package jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.colorFromHex
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.isCallMovedToAssetHub
import jp.co.soramitsu.common.validation.toFeeError
import jp.co.soramitsu.core.utils.amountFromPlanks
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.hash.isPositive
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
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

    // Retrying a failed lookup reloads the fee and the locked amount.
    private val reloadTrigger = MutableStateFlow(0)
    private var isLoadingErrorShown = false

    // Both lookups start eagerly in viewModelScope, where an uncaught failure would crash the app.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val feeFlow = reloadTrigger.flatMapLatest {
        flowOf {
            interactor.estimateClaimRewardsFee(chainId)
        }.catch { showLoadingError(it.toFeeError(resourceManager), canRetry = !it.isCallMovedToAssetHub()) }
    }.share()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lockedAmountFlow = reloadTrigger.flatMapLatest {
        flowOf<BigInteger?> {
            interactor.getVestingLockedAmount(chainId).orZero()
        }.catch {
            val title = resourceManager.getString(R.string.choose_amount_network_error)
            val message = resourceManager.getString(R.string.choose_amount_error_balance)
            showLoadingError(ValidationException(title, message), canRetry = true)
        }
    }
        .onEach {
            if (it != null && it.isPositive().not()) {
                showError(
                    title = resourceManager.getString(R.string.common_warning),
                    message = resourceManager.getString(R.string.warning_message_nothing_to_claim),
                    positiveButtonText = resourceManager.getString(R.string.common_confirm),
                    positiveClick = router::back,
                    onBackClick = router::back
                )
            }
        }

    val state: StateFlow<ClaimRewardsViewState> = kotlinx.coroutines.flow.combine(
        assetFlow,
        buttonStateFlow,
        claimRewardsRequestInProgressFlow,
        feeFlow,
        lockedAmountFlow
    ) { asset, buttonState, isRequesting, fee, lockedAmount ->

        val assetModel = mapAssetToAssetModel(asset)

        val transferableAmount = asset.transferable

        val lockedDecimal = lockedAmount?.let { assetModel.token.configuration.amountFromPlanks(lockedAmount) }.orZero()
        val lockedInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.vesting_locked_title),
            value = lockedDecimal.formatCryptoDetail(assetModel.token.configuration.symbol),
            additionalValue = lockedDecimal.applyFiatRate(assetModel.token.fiatRate)?.formatFiat(assetModel.token.fiatSymbol)
        )

        val transferableInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.assetdetails_balance_transferable),
            value = transferableAmount?.formatCryptoDetail(assetModel.token.configuration.symbol)
                ?: resourceManager.getString(R.string.portfolio_balance_unavailable),
            additionalValue = transferableAmount?.applyFiatRate(assetModel.token.fiatRate)?.formatFiat(assetModel.token.fiatSymbol)
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
            buttonState = buttonState.copy(enabled = buttonState.enabled && transferableAmount != null),
            isLoading = isRequesting || lockedAmount.isZero()
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

    // One dialog for both lookups; Retry reloads both.
    private fun showLoadingError(error: ValidationException, canRetry: Boolean) {
        if (isLoadingErrorShown) return
        isLoadingErrorShown = true
        val (title, message) = error

        if (canRetry) {
            showError(
                title = title,
                message = message,
                positiveButtonText = resourceManager.getString(R.string.common_retry),
                negativeButtonText = resourceManager.getString(R.string.common_cancel),
                positiveClick = {
                    isLoadingErrorShown = false
                    reloadTrigger.value++
                },
                negativeClick = router::back,
                onBackClick = router::back,
                isHideable = false
            )
        } else {
            showError(
                title = title,
                message = message,
                positiveButtonText = resourceManager.getString(R.string.common_close),
                positiveClick = router::back,
                onBackClick = router::back,
                isHideable = false
            )
        }
    }

    private fun sendClaimExtrinsic() {
        launch {
            claimRewardsRequestInProgressFlow.value = true

            runCatching {
                requireNotNull(assetFlow.first().transferable) {
                    resourceManager.getString(R.string.portfolio_balance_unavailable)
                }
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
                            if (it is CancellationException) throw it
                            showError(it)
                        }
                    }
                }
                .onFailure { error ->
                    claimRewardsRequestInProgressFlow.value = false
                    if (error is CancellationException) throw error
                    showError(error)
                }
        }
    }
}
