package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.model.QrSharingPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream

private const val AVATAR_SIZE_DP = 32

class ReceiveViewModel(
    private val interactor: WalletInteractor,
    private val qrCodeGenerator: QrCodeGenerator,
    private val addressIconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val router: WalletRouter
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    companion object {
        private const val QR_TEMP_IMAGE_NAME = "address.png"
        private const val QR_TEMP_IMAGE_QUALITY = 100
    }

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
        viewModelScope

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
        disposables += interactor.createFileInTempStorageAndRetrieveAsset(QR_TEMP_IMAGE_NAME)
            .subscribeOn(Schedulers.io())
            .map { (file, asset) ->
                val qrFile = compressBitmapToFile(qrBitmap, file)
                val message = generateMessage(asset.token.type.networkType.readableName, asset.token.type.displayName, address)
                Pair(qrFile, message)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ (file, message) ->
                _shareEvent.value = Event(QrSharingPayload(file, message))
            }, {
                it.message.let(::showError)
            })
    }

    private fun compressBitmapToFile(bitmap: Bitmap, file: File): File {
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, QR_TEMP_IMAGE_QUALITY, outputStream)
        outputStream.close()
        return file
    }

    private fun accountIconFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow()
            .map { addressIconGenerator.createAddressModel(it.address, AVATAR_SIZE_DP) }
    }

    private fun generateMessage(network: String, token: String, address: String): String {
        return resourceManager.getString(R.string.wallet_receive_share_message).format(network, token) + " " + address
    }
}