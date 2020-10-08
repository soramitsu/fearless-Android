package jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.AddressModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft

// TODO use dp
private const val ICON_SIZE_IN_PX = 70

class ConfirmTransferViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val iconGenerator: IconGenerator,
    private val clipboardManager: ClipboardManager,
    val transferDraft: TransferDraft
) : BaseViewModel() {
    val recipientModel = generateAddressModel(transferDraft.recipientAddress).asLiveData()

    private val _transferSubmittingLiveData = MutableLiveData(false)
    val transferSubmittingLiveData : LiveData<Boolean> = _transferSubmittingLiveData

    fun backClicked() {
        router.back()
    }

    fun submitClicked() {
        _transferSubmittingLiveData.value = true

        disposables += interactor.performTransfer(createTransfer())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { _transferSubmittingLiveData.value = false }
            .subscribe ({
                router.finishSendFlow()
            }, {
                showError(it.message!!)
            })
    }

    fun copyRecipientAddressClicked() {
        clipboardManager.addToClipboard(transferDraft.recipientAddress)

        showMessage(resourceManager.getString(R.string.common_copied))
    }

    private fun createTransfer(): Transfer {
        return with(transferDraft) {
            Transfer(
                recipient = recipientAddress,
                amount = amount,
                token = token
            )
        }
    }

    private fun generateAddressModel(address: String): Single<AddressModel> {
        return interactor.getAddressId(address)
            .map { iconGenerator.getSvgImage(it, ICON_SIZE_IN_PX) }
            .map { AddressModel(address, it) }
    }
}
