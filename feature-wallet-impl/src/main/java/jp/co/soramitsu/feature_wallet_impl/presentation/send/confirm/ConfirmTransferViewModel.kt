package jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.common.AddressIconGenerator
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft

private const val ICON_IN_DP = 24

class ConfirmTransferViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    addressIconGenerator: AddressIconGenerator,
    private val clipboardManager: ClipboardManager,
    val transferDraft: TransferDraft
) : BaseViewModel() {
    val recipientModel = addressIconGenerator.createAddressIcon(transferDraft.recipientAddress, ICON_IN_DP)
        .asLiveData()

    private val _transferSubmittingLiveData = MutableLiveData(false)
    val transferSubmittingLiveData: LiveData<Boolean> = _transferSubmittingLiveData

    fun backClicked() {
        router.back()
    }

    fun submitClicked() {
        _transferSubmittingLiveData.value = true

        disposables += interactor.performTransfer(createTransfer(), transferDraft.fee)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { _transferSubmittingLiveData.value = false }
            .subscribe({
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
}
