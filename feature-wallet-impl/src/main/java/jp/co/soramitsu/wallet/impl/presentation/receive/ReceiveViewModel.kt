package jp.co.soramitsu.wallet.impl.presentation.receive

import android.graphics.Bitmap
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
import jp.co.soramitsu.common.utils.isNotZero
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
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup.ChainAssetsManager
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup.ChainType
import jp.co.soramitsu.wallet.impl.presentation.receive.model.QrSharingPayload
import jp.co.soramitsu.wallet.impl.presentation.receive.model.ReceiveToggleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val QR_TEMP_IMAGE_NAME = "address.png"

@HiltViewModel
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

    private val assetSymbolToShow = chainRegistry.getAsset(assetPayload.chainId, assetPayload.chainAssetId)?.symbol

    private val accountFlow = interactor.selectedAccountFlow(assetPayload.chainId)
    private val assetFlow = chainAssetsManager.assetFlow.onStart {
        emit(interactor.getCurrentAsset(assetPayload.chainId, assetPayload.chainAssetId))
    }.mapNotNull { it }

    private val _shareEvent = MutableLiveData<Event<QrSharingPayload>>()
    val shareEvent: LiveData<Event<QrSharingPayload>> = _shareEvent

    private val receiveTypeSelectorState = MutableStateFlow(
        MultiToggleButtonState(
            currentSelection = ReceiveToggleType.Receive,
            toggleStates = ReceiveToggleType.values().toList()
        )
    )

    private val defaultAmountInputState = AmountInputViewState.defaultObj.copy(
        totalBalance = resourceManager.getString(R.string.common_balance_format, "...")
    )

    private val initialAmount = BigDecimal.ZERO
    private val enteredAmountFlow = MutableStateFlow(initialAmount)

    private val amountInputViewState: Flow<AmountInputViewState> = assetFlow.flatMapLatest { asset ->
        enteredAmountFlow.map { amount ->
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

            AmountInputViewState(
                tokenName = asset.token.configuration.symbol,
                tokenImage = asset.token.configuration.iconUrl,
                totalBalance = resourceManager.getString(R.string.common_transferable_format, tokenBalance),
                fiatAmount = fiatAmount,
                tokenAmount = inputAmount,
                precision = inputPrecision,
                allowAssetChoose = true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAmountInputState)

    private val qrBitmapFlow = combine(
        receiveTypeSelectorState,
        amountInputViewState,
        assetFlow
    ) { receiveType, inputState, asset ->
        if (asset.token.configuration.chainId in listOf(soraTestChainId, soraMainChainId)) {
            val amount =
                if (receiveType.currentSelection == ReceiveToggleType.Receive) {
                    null
                } else if (asset.token.configuration.currencyId == bokoloCashTokenId) {
                    inputState.tokenAmount.setScale(BOKOLO_MAX_SCALE, RoundingMode.DOWN)
                } else {
                    inputState.tokenAmount
                }
            val qrCodeSharingSoraString = interactor.getQrCodeSharingSoraString(asset.token.configuration.chainId, asset.token.configuration.id, amount)
            qrCodeSharingSoraString
        } else {
            currentAccountAddress.invoke(assetPayload.chainId)
        }
    }.mapNotNull { qrString ->
        qrString?.let { qrCodeGenerator.generateQrBitmap(it) }
    }

    val state = combine(
        qrBitmapFlow,
        accountFlow,
        receiveTypeSelectorState,
        amountInputViewState,
        assetFlow
    ) { qrCode: Bitmap,
        account: WalletAccount,
        receiveTypeState,
        amountInputViewState: AmountInputViewState,
        asset ->

        val allowRequest = asset.token.configuration.chainId in listOf(
            soraMainChainId, soraTestChainId
        )

        LoadingState.Loaded(
            ReceiveScreenViewState(
                account = account,
                qrCode = qrCode,
                assetSymbol = assetSymbolToShow.orEmpty().uppercase(),
                multiToggleButtonState = receiveTypeState,
                amountInputViewState = amountInputViewState,
                requestAllowed = allowRequest
            )
        )
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

    private fun copyAddress() = launch {
        val account = accountFlow.firstOrNull() ?: return@launch

        clipboardManager.addToClipboard(account.address)

        val message = resourceManager.getString(R.string.common_copied)
        showMessage(message)
    }

    override fun backClicked() {
        router.back()
    }

    private fun shareWallet() {
        viewModelScope.launch {
            val address = currentAccountAddress(assetPayload.chainId) ?: return@launch
            val result = interactor.createFileInTempStorageAndRetrieveAsset(QR_TEMP_IMAGE_NAME)

            if (result.isSuccess) {
                val file = result.requireValue()

                file.write(qrBitmapFlow.first())

                val message = generateMessage(address)

                _shareEvent.value = Event(QrSharingPayload(file, message))
            } else {
                showError(result.requireException())
            }
        }
    }

    private suspend fun generateMessage(address: String): String {
        val chain = chainRegistry.getChain(assetPayload.chainId)
        val asset = chain.assetsById[assetPayload.chainAssetId]
        return resourceManager.getString(R.string.wallet_receive_share_message).format(
            chain.name,
            asset?.symbol?.uppercase()
        ) + " " + address
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
