package jp.co.soramitsu.wallet.impl.presentation.receive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.lang.Integer.max
import java.lang.Integer.min
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.write
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup.ChainAssetsManager
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup.ChainType
import jp.co.soramitsu.wallet.impl.presentation.receive.model.QrSharingPayload
import jp.co.soramitsu.wallet.impl.presentation.receive.model.ReceiveToggleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

private const val QR_TEMP_IMAGE_NAME = "address.png"

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val qrCodeGenerator: QrCodeGenerator,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val router: WalletRouter,
    private val chainRegistry: ChainRegistry,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val chainAssetsManager: ChainAssetsManager
) : BaseViewModel(), ReceiveScreenInterface {
    companion object {
        const val BOKOLO_MAX_SCALE = 2
    }

    private val assetPayload = savedStateHandle.get<AssetPayload>(ReceiveFragment.KEY_ASSET_PAYLOAD)!!

    private val accountFlow = interactor.selectedAccountFlow(assetPayload.chainId)
    private val assetFlow = chainAssetsManager.assetFlow.onStart {
        emit(interactor.getCurrentAsset(assetPayload.chainId, assetPayload.chainAssetId))
    }.mapNotNull { it }.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val _shareEvent = MutableLiveData<Event<QrSharingPayload>>()
    val shareEvent: LiveData<Event<QrSharingPayload>> = _shareEvent

    private val receiveTypeSelectorState = MutableStateFlow(
        MultiToggleButtonState(
            currentSelection = ReceiveToggleType.Receive,
            toggleStates = ReceiveToggleType.values().toList()
        )
    )

    private val initialAmount = BigDecimal.ZERO
    private val enteredAmountFlow = MutableStateFlow(initialAmount)
    private var shareInProgress = false

    private fun amountInputState(asset: Asset, amount: BigDecimal): AmountInputViewState {
        val tokenBalance = asset.transferable.formatCrypto(asset.token.configuration.symbol)
        val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        val inputPrecision = if (asset.token.configuration.currencyId == bokoloCashTokenId) {
            max(amount.scale(), BOKOLO_MAX_SCALE)
        } else {
            asset.token.configuration.precision
        }

        val inputAmount = if (asset.token.configuration.currencyId == bokoloCashTokenId) {
            amount.setScale(min(amount.scale(), BOKOLO_MAX_SCALE), RoundingMode.DOWN)
        } else {
            amount
        }

        return AmountInputViewState(
            tokenName = asset.token.configuration.symbol,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(R.string.common_transferable_format, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = inputAmount,
            precision = inputPrecision,
            allowAssetChoose = true
        )
    }

    private val receiveInputs = combine(
        accountFlow,
        assetFlow,
        receiveTypeSelectorState,
        enteredAmountFlow
    ) { account, asset, receiveType, amount ->
        asset to ReceiveScreenViewState(
            account = account,
            qrCode = null,
            assetSymbol = asset.token.configuration.symbol.orEmpty().uppercase(),
            multiToggleButtonState = receiveType,
            amountInputViewState = amountInputState(asset, amount),
            requestAllowed = asset.token.configuration.chainId in listOf(soraMainChainId, soraTestChainId),
            networkName = chainRegistry.getChain(asset.token.configuration.chainId).name
        )
    }

    val state = receiveInputs.transformLatest<Pair<Asset, ReceiveScreenViewState>, LoadingState<ReceiveScreenViewState>> { (asset, snapshot) ->
        // Keep the form mounted, but never show an earlier QR beside new asset/account text.
        emit(LoadingState.Loaded(snapshot))
        val qrString = if (snapshot.requestAllowed) {
            val amount = if (snapshot.multiToggleButtonState.currentSelection == ReceiveToggleType.Receive) {
                null
            } else if (asset.token.configuration.currencyId == bokoloCashTokenId) {
                snapshot.amountInputViewState.tokenAmount.setScale(BOKOLO_MAX_SCALE, RoundingMode.DOWN)
            } else {
                snapshot.amountInputViewState.tokenAmount
            }
            val encoded = interactor.getQrCodeSharingSoraString(asset.token.configuration.chainId, asset.token.configuration.id, amount)
            // The existing encoder reads the selected account. Discard an in-flight account change.
            if (currentAccountAddress(asset.token.configuration.chainId) != snapshot.account.address) return@transformLatest
            encoded
        } else {
            snapshot.account.address
        }
        val qrCode = qrCodeGenerator.generateQrBitmap(qrString)
        emit(LoadingState.Loaded(snapshot.copy(qrCode = qrCode)))
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    init {
        setInitialChainsAndAssetIds()
    }

    override fun copyClicked() {
        copyAddress()
    }

    override fun shareClicked() {
        shareWallet()
    }

    override fun tokenClicked() {
        chainAssetsManager.observeChainIdAndAssetIdResult(
            scope = viewModelScope,
            chainType = ChainType.Origin,
            onError = { showError(it) }
        )
        launch {
            assetFlow.firstOrNull()?.token?.configuration?.let {
                router.openSelectAsset(chainId = it.chainId, selectedAssetId = it.id, excludeAssetId = null)
            }
        }
    }

    override fun receiveChanged(type: ReceiveToggleType) {
        receiveTypeSelectorState.value = receiveTypeSelectorState.value.copy(currentSelection = type)
    }

    override fun onAmountInput(amount: BigDecimal?) {
        enteredAmountFlow.value = amount
    }

    private fun copyAddress() {
        val snapshot = (state.value as? LoadingState.Loaded)?.data ?: return
        if (snapshot.qrCode == null) return

        clipboardManager.addToClipboard(snapshot.account.address)

        val message = resourceManager.getString(R.string.common_copied)
        showMessage(message)
    }

    override fun backClicked() {
        router.back()
    }

    private fun shareWallet() {
        val snapshot = (state.value as? LoadingState.Loaded)?.data ?: return
        val qrCode = snapshot.qrCode ?: return
        if (shareInProgress) return
        shareInProgress = true
        viewModelScope.launch {
            try {
                val result = interactor.createFileInTempStorageAndRetrieveAsset(QR_TEMP_IMAGE_NAME)
                if (result.isSuccess) {
                    val file = result.requireValue()
                    file.write(qrCode)
                    if ((state.value as? LoadingState.Loaded)?.data === snapshot) {
                        _shareEvent.value = Event(QrSharingPayload(file, generateMessage(snapshot)))
                    }
                } else {
                    showError(result.requireException())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showError(failure)
            } finally {
                shareInProgress = false
            }
        }
    }

    private fun generateMessage(snapshot: ReceiveScreenViewState): String {
        return resourceManager.getString(R.string.wallet_receive_share_message).format(
            snapshot.networkName,
            snapshot.assetSymbol
        ) + " " + snapshot.account.address
    }

    private fun setInitialChainsAndAssetIds() {
        viewModelScope.launch {
            chainAssetsManager.setInitialIds(
                chainId = assetPayload.chainId,
                assetId = assetPayload.chainAssetId
            )
        }
    }
}
