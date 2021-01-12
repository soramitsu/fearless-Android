package jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecks

private const val ICON_IN_DP = 24

class ConfirmTransferViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val transferValidityChecks: TransferValidityChecks.Presentation,
    val transferDraft: TransferDraft
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    TransferValidityChecks by transferValidityChecks {

    private val _showBalanceDetailsEvent = MutableLiveData<Event<Unit>>()
    val showBalanceDetailsEvent: LiveData<Event<Unit>> = _showBalanceDetailsEvent

    val recipientModel = getAddressIcon().asLiveData()

    private val _transferSubmittingLiveData = MutableLiveData(false)

    val sendButtonStateLiveData = _transferSubmittingLiveData.map { submitting ->
        if (submitting) {
            ButtonState.PROGRESS
        } else {
            ButtonState.NORMAL
        }
    }

    val assetLiveData = interactor.observeAsset(transferDraft.type)
        .subscribeOn(Schedulers.io())
        .map(::mapAssetToAssetModel)
        .observeOn(AndroidSchedulers.mainThread())
        .asLiveData()

    fun backClicked() {
        router.back()
    }

    fun copyRecipientAddressClicked() {
        val payload = ExternalAccountActions.Payload(transferDraft.recipientAddress, transferDraft.type.networkType)

        externalAccountActions.showExternalActions(payload)
    }

    fun availableBalanceClicked() {
        _showBalanceDetailsEvent.value = Event(Unit)
    }

    fun submitClicked() {
        performTransfer(suppressWarnings = false)
    }

    fun warningConfirmed() {
        performTransfer(suppressWarnings = true)
    }

    fun errorAcknowledged() {
        router.back()
    }

    private fun performTransfer(suppressWarnings: Boolean) {
        val maxAllowedStatusLevel = if (suppressWarnings) TransferValidityLevel.Warning else TransferValidityLevel.Ok

        _transferSubmittingLiveData.value = true

        disposables += interactor.performTransfer(createTransfer(), transferDraft.fee, maxAllowedStatusLevel)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { _transferSubmittingLiveData.value = false }
            .subscribe({
                router.finishSendFlow()
            }, {
                if (it is NotValidTransferStatus) {
                    processInvalidStatus(it.status)
                } else {
                    showError(it)
                }
            })
    }

    private fun processInvalidStatus(status: TransferValidityStatus) {
        when (status) {
            is TransferValidityLevel.Warning.Status -> transferValidityChecks.showTransferWarning(status)
            is TransferValidityLevel.Error.Status -> transferValidityChecks.showTransferError(status)
        }
    }

    private fun getAddressIcon(): Single<AddressModel> {
        return addressIconGenerator.createAddressModel(transferDraft.recipientAddress, ICON_IN_DP)
    }

    private fun createTransfer(): Transfer {
        return with(transferDraft) {
            Transfer(
                recipient = recipientAddress,
                amount = amount,
                type = type
            )
        }
    }
}
