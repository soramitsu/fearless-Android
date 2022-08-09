package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_wallet_api.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.model.QrSharingPayload
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val AVATAR_SIZE_DP = 32
private const val QR_TEMP_IMAGE_NAME = "address.png"

class ReceiveViewModel @AssistedInject constructor(
    private val interactor: WalletInteractor,
    private val qrCodeGenerator: QrCodeGenerator,
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    @Assisted private val assetPayload: AssetPayload,
    private val router: WalletRouter,
    private val chainRegistry: ChainRegistry,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

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

    @AssistedFactory
    interface ReceiveViewModelFactory {
        fun create(assetPayload: AssetPayload): ReceiveViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: ReceiveViewModelFactory,
            assetPayload: AssetPayload
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(assetPayload) as T
            }
        }
    }
}
