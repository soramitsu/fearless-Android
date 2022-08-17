package jp.co.soramitsu.featurewalletimpl.presentation.receive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.write
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.featureaccountapi.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.featurewalletapi.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.featurewalletapi.domain.interfaces.WalletInteractor
import jp.co.soramitsu.featurewalletimpl.presentation.AssetPayload
import jp.co.soramitsu.featurewalletimpl.presentation.WalletRouter
import jp.co.soramitsu.featurewalletimpl.presentation.receive.model.QrSharingPayload
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val AVATAR_SIZE_DP = 32
private const val QR_TEMP_IMAGE_NAME = "address.png"

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val qrCodeGenerator: QrCodeGenerator,
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val router: WalletRouter,
    private val chainRegistry: ChainRegistry,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    private val assetPayload = savedStateHandle.get<AssetPayload>(KEY_ASSET_PAYLOAD)!!

    val assetSymbol = chainRegistry.getAsset(assetPayload.chainId, assetPayload.chainAssetId)?.symbol

    val qrBitmapLiveData = liveData {
        val qrString = interactor.getQrCodeSharingString(assetPayload.chainId)

        emit(qrCodeGenerator.generateQrBitmap(qrString))
    }

    val accountLiveData = interactor.selectedAccountFlow(assetPayload.chainId)
        .asLiveData()

    val accountIconLiveData: LiveData<AddressModel> = accountIconFlow()
        .asLiveData()

    private val _shareEvent = MutableLiveData<Event<QrSharingPayload>>()
    val shareEvent: LiveData<Event<QrSharingPayload>> = _shareEvent

    fun recipientClicked() = launch {
        val account = accountLiveData.value ?: return@launch
        val chain = chainRegistry.getChain(assetPayload.chainId)
        val supportedExplorers = chain.explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, account.address)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = account.address,
            chainId = assetPayload.chainId,
            chainName = chain.name,
            explorers = supportedExplorers
        )

        externalAccountActions.showExternalActions(externalActionsPayload)
    }

    fun backClicked() {
        router.back()
    }

    fun shareButtonClicked() {
        val qrBitmap = qrBitmapLiveData.value ?: return

        viewModelScope.launch {
            val address = currentAccountAddress(assetPayload.chainId) ?: return@launch
            val result = interactor.createFileInTempStorageAndRetrieveAsset(QR_TEMP_IMAGE_NAME)

            if (result.isSuccess) {
                val file = result.requireValue()

                file.write(qrBitmap)

                val message = generateMessage(address)

                _shareEvent.value = Event(QrSharingPayload(file, message))
            } else {
                showError(result.requireException())
            }
        }
    }

    private fun accountIconFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow(polkadotChainId)
            .map { addressIconGenerator.createAddressModel(it.address, AVATAR_SIZE_DP) }
    }

    private suspend fun generateMessage(address: String): String {
        val chain = chainRegistry.getChain(assetPayload.chainId)
        val asset = chain.assetsById[assetPayload.chainAssetId]
        return resourceManager.getString(R.string.wallet_receive_share_message).format(
            chain.name,
            asset?.symbol
        ) + " " + address
    }
}
