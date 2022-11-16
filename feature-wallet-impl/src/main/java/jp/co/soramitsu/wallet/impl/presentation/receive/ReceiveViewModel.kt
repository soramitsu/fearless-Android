package jp.co.soramitsu.wallet.impl.presentation.receive

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.copyAddressClicked
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.write
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraKusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.receive.model.QrSharingPayload
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val QR_TEMP_IMAGE_NAME = "address.png"

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val qrCodeGenerator: QrCodeGenerator,
    private val resourceManager: ResourceManager,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val router: WalletRouter,
    private val chainRegistry: ChainRegistry,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    val receiveScreenInterface: ReceiveScreenInterface = object : ReceiveScreenInterface {
        override fun copyClicked() {
            copyAddress()
        }
        override fun shareClicked() {
            shareWallet()
        }
    }

    private val assetPayload = savedStateHandle.get<AssetPayload>(ReceiveFragment.KEY_ASSET_PAYLOAD)!!

    private val assetSymbolToShow = chainRegistry.getAsset(assetPayload.chainId, assetPayload.chainAssetId)?.symbolToShow

    private val qrBitmapFlow = flow {
        val qrString = if (assetPayload.chainId in listOf(soraKusamaChainId, soraTestChainId)) {
            interactor.getQrCodeSharingSoraString(assetPayload.chainId, assetPayload.chainAssetId)
        } else {
            currentAccountAddress(assetPayload.chainId) ?: return@flow
        }

        emit(qrCodeGenerator.generateQrBitmap(qrString))
    }

    private val accountFlow = interactor.selectedAccountFlow(assetPayload.chainId)

    private val _shareEvent = MutableLiveData<Event<QrSharingPayload>>()
    val shareEvent: LiveData<Event<QrSharingPayload>> = _shareEvent

    val state = combine(
        qrBitmapFlow,
        accountFlow
    ) { qrCode: Bitmap,
        account: WalletAccount ->

        LoadingState.Loaded(
            ReceiveScreenViewState(
                account = account,
                qrCode = qrCode,
                assetSymbol = assetSymbolToShow.orEmpty().uppercase()
            )
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = LoadingState.Loading())

    private fun copyAddress() = launch {
        val account = accountFlow.firstOrNull() ?: return@launch

        copyAddressClicked(account.address)
    }

    fun backClicked() {
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
            asset?.symbolToShow?.uppercase()
        ) + " " + address
    }
}

interface ReceiveScreenInterface {
    fun copyClicked()
    fun shareClicked()
}
