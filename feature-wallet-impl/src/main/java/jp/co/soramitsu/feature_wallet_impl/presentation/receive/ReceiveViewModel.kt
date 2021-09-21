package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.write
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.model.QrSharingPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val AVATAR_SIZE_DP = 32
private const val QR_TEMP_IMAGE_NAME = "address.png"

class ReceiveViewModel(
    private val interactor: WalletInteractor,
    private val qrCodeGenerator: QrCodeGenerator,
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val router: WalletRouter
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    val qrBitmapLiveData = liveData {
        val qrString = interactor.getQrCodeSharingString()

        emit(qrCodeGenerator.generateQrBitmap(qrString))
    }

    val accountLiveData = interactor.selectedAccountFlow()
        .asLiveData()

    val accountIconLiveData: LiveData<AddressModel> = accountIconFlow()
        .asLiveData()

    private val _shareEvent = MutableLiveData<Event<QrSharingPayload>>()
    val shareEvent: LiveData<Event<QrSharingPayload>> = _shareEvent

    fun recipientClicked() {
        val account = accountLiveData.value ?: return

        val payload = ExternalAccountActions.Payload(account.address, account.network.type)

        externalAccountActions.showExternalActions(payload)
    }

    fun backClicked() {
        router.back()
    }

    fun shareButtonClicked() {
        val qrBitmap = qrBitmapLiveData.value ?: return
        val address = accountIconLiveData.value?.address ?: return

        viewModelScope.launch {
            val result = interactor.createFileInTempStorageAndRetrieveAsset(QR_TEMP_IMAGE_NAME)

            if (result.isSuccess) {
                val (file, asset) = result.requireValue()

                file.write(qrBitmap)

                val message = generateMessage(asset.token.configuration, address)

                _shareEvent.value = Event(QrSharingPayload(file, message))
            } else {
                showError(result.requireException())
            }
        }
    }

    private fun accountIconFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow()
            .map { addressIconGenerator.createAddressModel(it.address, AVATAR_SIZE_DP) }
    }

    private fun generateMessage(tokenType: Token.Type, address: String): String {
        return resourceManager.getString(R.string.wallet_receive_share_message).format(
            tokenType.networkType.readableName,
            tokenType.displayName
        ) + " " + address
    }
}
