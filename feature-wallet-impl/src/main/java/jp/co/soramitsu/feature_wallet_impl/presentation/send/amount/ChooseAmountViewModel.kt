package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.Optional
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.mapExcludingNull
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel.Error
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel.Ok
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel.Warning
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferValidityChecks
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

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
    private val recipientAddress: String
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    TransferValidityChecks by transferValidityChecks {

    val recipientModelLiveData = generateAddressModel(recipientAddress).asLiveData()

    private val amountEventsSubject = BehaviorSubject.createDefault("0")
    private val amountRawLiveData = amountEventsSubject.asLiveData()

    private val currentAssetObservable = interactor.currentAssetFlow()

    private val _feeLoadingLiveData = MutableLiveData<Boolean>()
    val feeLoadingLiveData = _feeLoadingLiveData

    val feeLiveData = observeFee().asOptionalLiveData()

    private val _feeErrorLiveData = MutableLiveData<Event<RetryReason>>()
    val feeErrorLiveData = _feeErrorLiveData

    private val checkingEnoughFundsLiveData = MutableLiveData<Boolean>(false)

    private val _showBalanceDetailsEvent = MutableLiveData<Event<TransferDraft>>()
    val showBalanceDetailsEvent: LiveData<Event<TransferDraft>> = _showBalanceDetailsEvent

    val assetLiveData = currentAssetObservable
        .subscribeOn(Schedulers.io())
        .map(::mapAssetToAssetModel)
        .observeOn(AndroidSchedulers.mainThread())
        .asLiveData()

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
        amountEventsSubject.onNext(newAmountRaw)
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
        val recipientAddress = recipientModelLiveData.value.address ?: return
        val networkType = assetLiveData.value.token.type.networkType ?: return

        externalAccountActions.showExternalActions(ExternalAccountActions.Payload(recipientAddress, networkType))
    }

    fun availableBalanceClicked() {
        val transferDraft = buildTransferDraft() ?: return

        _showBalanceDetailsEvent.value = Event(transferDraft)
    }

    fun warningConfirmed() {
        openConfirmationScreen()
    }

    private fun observeFee(): Observable<Optional<Fee>> {
        val debouncedAmountEvents = amountEventsSubject
            .subscribeOn(Schedulers.io())
            .mapExcludingNull(String::toBigDecimalOrNull)
            .throttleLatest(500, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .doOnNext { _feeLoadingLiveData.postValue(true) }

        return Observable.combineLatest(debouncedAmountEvents, currentAssetObservable, BiFunction<BigDecimal, Asset, Transfer> { amount, asset ->
            Transfer(recipientAddress, amount, asset.token.type)
        })
            .switchMapSingle { transfer ->
                interactor.getTransferFee(transfer)
                    .retry(RETRY_TIMES)
                    .doOnError {
                        _feeErrorLiveData.postValue(Event(RetryReason.LOAD_FEE))
                        DEFAULT_ERROR_HANDLER(it)
                    }
                    .map { Optional(it) }
                    .onErrorReturn { Optional(null) }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { _feeLoadingLiveData.value = false }
    }

    private fun generateAddressModel(address: String): Single<AddressModel> {
        return addressIconGenerator.createAddressModel(address, AVATAR_SIZE_DP)
    }

    private fun checkEnoughFunds() {
        val fee = feeLiveData.value ?: return

        checkingEnoughFundsLiveData.value = true

        disposables += currentAssetObservable.firstOrError()
            .subscribeOn(Schedulers.io())
            .map { Transfer(recipientAddress, fee.transferAmount, it.token.type) }
            .flatMap(interactor::checkTransferValidityStatus)
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { checkingEnoughFundsLiveData.value = false }
            .subscribe({
                processHasEnoughFunds(it)
            }, {
                _feeErrorLiveData.value = Event(RetryReason.CHECK_ENOUGH_FUNDS)
            })
    }

    private fun processHasEnoughFunds(status: TransferValidityStatus) {
        when (status) {
            is Ok -> openConfirmationScreen()
            is Warning.Status -> transferValidityChecks.showTransferWarning(status)
            is Error.Status -> transferValidityChecks.showTransferError(status)
        }
    }

    private fun openConfirmationScreen() {
        val transferDraft = buildTransferDraft() ?: return

        router.openConfirmTransfer(transferDraft)
    }

    private fun buildTransferDraft(): TransferDraft? {
        val fee = feeLiveData.value ?: return null
        val asset = assetLiveData.value ?: return null

        return TransferDraft(fee.transferAmount, fee.feeAmount, asset.token.type, recipientAddress)
    }

    private fun retryLoadFee() {
        amountEventsSubject.onNext(amountEventsSubject.value!!)
    }
}
