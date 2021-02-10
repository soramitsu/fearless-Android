package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel.Error
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel.Ok
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel.Warning
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecks
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api.PhishingWarning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

private const val AVATAR_SIZE_DP = 24

private const val RETRY_TIMES = 3L

enum class RetryReason(val reasonRes: Int) {
    CHECK_ENOUGH_FUNDS(R.string.choose_amount_error_balance),
    LOAD_FEE(R.string.choose_amount_error_fee)
}

class ChooseAmountViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val transferValidityChecks: TransferValidityChecks.Presentation,
    private val recipientAddress: String,
    private val phishingAddress: PhishingWarning
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    TransferValidityChecks by transferValidityChecks,
    PhishingWarning by phishingAddress {

    val recipientModelLiveData = liveData {
        emit(generateAddressModel(recipientAddress))
    }

    private val amountEvents = MutableStateFlow("0")
    private val amountRawLiveData = amountEvents.asLiveData()

    private val _feeLoadingLiveData = MutableLiveData<Boolean>(true)
    val feeLoadingLiveData = _feeLoadingLiveData

    val feeLiveData = feeFlow().asLiveData()

    private val _feeErrorLiveData = MutableLiveData<Event<RetryReason>>()
    val feeErrorLiveData = _feeErrorLiveData

    private val checkingEnoughFundsLiveData = MutableLiveData(false)

    private val _showBalanceDetailsEvent = MutableLiveData<Event<TransferDraft>>()
    val showBalanceDetailsEvent: LiveData<Event<TransferDraft>> = _showBalanceDetailsEvent

    val assetLiveData = liveData {
        val asset = interactor.getCurrentAsset()

        emit(mapAssetToAssetModel(asset))
    }

    private val minimumPossibleAmountLiveData = assetLiveData.map {
        it.token.type.amountFromPlanks(BigInteger.ONE)
    }

    val continueButtonStateLiveData = combine(
        feeLoadingLiveData,
        feeLiveData,
        checkingEnoughFundsLiveData,
        amountRawLiveData,
        minimumPossibleAmountLiveData
    ) { (feeLoading: Boolean, fee: Fee?, checkingFunds: Boolean, amountRaw: String, minimumPossibleAmount: BigDecimal) ->
        when {
            feeLoading || checkingFunds -> ButtonState.PROGRESS
            fee != null && fee.transferAmount >= minimumPossibleAmount
                && amountRaw.isNotEmpty() -> ButtonState.NORMAL
            else -> ButtonState.DISABLED
        }
    }

    fun nextClicked() {
        checkEnoughFunds()
    }

    fun amountChanged(newAmountRaw: String) {
        viewModelScope.launch {
            amountEvents.emit(newAmountRaw)
        }
    }

    fun backClicked() {
        router.back()
    }

    fun retry(retryReason: RetryReason) {
        when (retryReason) {
            RetryReason.LOAD_FEE -> retryLoadFee()
            RetryReason.CHECK_ENOUGH_FUNDS -> checkEnoughFunds()
        }
    }

    fun recipientAddressClicked() {
        val recipientAddress = recipientModelLiveData.value?.address ?: return
        val networkType = assetLiveData.value?.token?.type?.networkType ?: return

        externalAccountActions.showExternalActions(ExternalAccountActions.Payload(recipientAddress, networkType))
    }

    fun availableBalanceClicked() {
        val transferDraft = buildTransferDraft() ?: return

        _showBalanceDetailsEvent.value = Event(transferDraft)
    }

    fun warningConfirmed() {
        openConfirmationScreen()
    }

    override fun proceedAddress(address: String) {
        val transferDraft = buildTransferDraft() ?: return
        router.openConfirmTransfer(transferDraft)
    }

    @OptIn(ExperimentalTime::class)
    private fun feeFlow(): Flow<Fee?> = amountEvents
        .mapNotNull(String::toBigDecimalOrNull)
        .debounce(500.milliseconds)
        .distinctUntilChanged()
        .onEach { _feeLoadingLiveData.postValue(true) }
        .mapLatest<BigDecimal, Fee?> { amount ->
            val asset = interactor.getCurrentAsset()
            val transfer = Transfer(recipientAddress, amount, asset.token.type)

            interactor.getTransferFee(transfer)
        }
        .retry(RETRY_TIMES)
        .catch {
            _feeErrorLiveData.postValue(Event(RetryReason.LOAD_FEE))

            it.printStackTrace()

            emit(null)
        }.onEach {
            _feeLoadingLiveData.value = false
        }

    private suspend fun generateAddressModel(address: String): AddressModel {
        return addressIconGenerator.createAddressModel(address, AVATAR_SIZE_DP)
    }

    private fun checkEnoughFunds() {
        val fee = feeLiveData.value ?: return

        checkingEnoughFundsLiveData.value = true

        viewModelScope.launch {
            val asset = interactor.getCurrentAsset()
            val transfer = Transfer(recipientAddress, fee.transferAmount, asset.token.type)

            val result = interactor.checkTransferValidityStatus(transfer)

            if (result.isSuccess) {
                processHasEnoughFunds(result.requireValue())
            } else {
                _feeErrorLiveData.value = Event(RetryReason.CHECK_ENOUGH_FUNDS)
            }

            checkingEnoughFundsLiveData.value = false
        }
    }

    private fun processHasEnoughFunds(status: TransferValidityStatus) {
        when (status) {
            is Ok -> openConfirmationScreen()
            is Warning.Status -> transferValidityChecks.showTransferWarning(status)
            is Error.Status -> transferValidityChecks.showTransferError(status)
        }
    }

    private fun openConfirmationScreen() {
        viewModelScope.launch {
            checkAddressForPhishing(recipientAddress)
        }
    }

    private fun buildTransferDraft(): TransferDraft? {
        val fee = feeLiveData.value ?: return null
        val asset = assetLiveData.value ?: return null

        return TransferDraft(fee.transferAmount, fee.feeAmount, asset.token.type, recipientAddress)
    }

    private fun retryLoadFee() {
        amountChanged(amountEvents.value)
    }
}
