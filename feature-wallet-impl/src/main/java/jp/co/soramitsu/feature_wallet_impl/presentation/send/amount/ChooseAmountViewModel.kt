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
import jp.co.soramitsu.common.account.externalActions.ExternalAccountActions
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.CheckFundsStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import java.math.BigDecimal
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
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val recipientAddress: String
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    val recipientModelLiveData = generateAddressModel(recipientAddress).asLiveData()

    private val amountEventsSubject = BehaviorSubject.createDefault(BigDecimal.ZERO)

    private val currentAssetObservable = interactor.observeCurrentAsset()

    val feeLiveData = observeFee().asLiveData()

    private val _feeLoadingLiveData = MutableLiveData<Boolean>()
    val feeLoadingLiveData = _feeLoadingLiveData

    private val _feeErrorLiveData = MutableLiveData<Event<RetryReason>>()
    val feeErrorLiveData = _feeErrorLiveData

    private val _checkingEnoughFundsLiveData = MutableLiveData<Boolean>(false)
    val checkingEnoughFundsLiveData = _checkingEnoughFundsLiveData

    private val _showBalanceDetailsEvent = MutableLiveData<Event<TransferDraft>>()
    val showBalanceDetailsEvent: LiveData<Event<TransferDraft>> = _showBalanceDetailsEvent

    private val _showAccountRemovalWarning = MutableLiveData<Event<Unit>>()
    val showAccountRemovalWarning: LiveData<Event<Unit>> = _showAccountRemovalWarning

    val continueEnabledLiveData = combine(
        feeLoadingLiveData,
        feeLiveData,
        checkingEnoughFundsLiveData
    ) { (feeLoading: Boolean, fee: Fee, checkingFunds: Boolean) ->
        !feeLoading && fee.amount != null && !checkingFunds
    }

    val assetLiveData = currentAssetObservable
        .subscribeOn(Schedulers.io())
        .map(::mapAssetToAssetModel)
        .observeOn(AndroidSchedulers.mainThread())
        .asLiveData()

    fun nextClicked() {
        checkEnoughFunds()
    }

    fun amountChanged(newAmountRaw: String) {
        val newAmount = newAmountRaw.toBigDecimalOrNull() ?: return

        amountEventsSubject.onNext(newAmount)
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
        val networkType = assetLiveData.value?.token?.networkType ?: return

        externalAccountActions.showExternalActions(ExternalAccountActions.Payload(recipientAddress, networkType))
    }

    fun availableBalanceClicked() {
        val transferDraft = buildTransferDraft() ?: return

        _showBalanceDetailsEvent.value = Event(transferDraft)
    }

    fun transferRemovingAccountConfirmed() {
        openConfirmationScreen()
    }

    private fun observeFee(): Observable<Fee> {
        val debouncedAmountEvents = amountEventsSubject
            .subscribeOn(Schedulers.io())
            .debounce(500, TimeUnit.MILLISECONDS)
            .doOnNext { _feeLoadingLiveData.postValue(true) }

        return Observable.combineLatest(debouncedAmountEvents, currentAssetObservable, BiFunction<BigDecimal, Asset, Transfer> { amount, asset ->
            Transfer(recipientAddress, amount, asset.token)
        })
            .switchMapSingle { transfer ->
                interactor.getTransferFee(transfer)
                    .retry(RETRY_TIMES)
                    .doOnError {
                        _feeErrorLiveData.postValue(Event(RetryReason.LOAD_FEE))
                        DEFAULT_ERROR_HANDLER(it)
                    }
                    .onErrorReturn { createFallbackFee(transfer.token) }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { _feeLoadingLiveData.value = false }
    }

    private fun createFallbackFee(token: Asset.Token): Fee {
        return Fee(null, token)
    }

    private fun generateAddressModel(address: String): Single<AddressModel> {
        return interactor.getAddressId(address)
            .flatMap { addressIconGenerator.createAddressModel(address, it, AVATAR_SIZE_DP) }
    }

    private fun checkEnoughFunds() {
        val currentAmount = amountEventsSubject.value!!

        _checkingEnoughFundsLiveData.value = true

        disposables += currentAssetObservable.firstOrError()
            .subscribeOn(Schedulers.io())
            .map { Transfer(recipientAddress, currentAmount, it.token) }
            .flatMap(interactor::checkEnoughAmountForTransfer)
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { _checkingEnoughFundsLiveData.value = false }
            .subscribe({
                processHasEnoughFunds(it)
            }, {
                _feeErrorLiveData.value = Event(RetryReason.CHECK_ENOUGH_FUNDS)
            })
    }

    private fun processHasEnoughFunds(status: CheckFundsStatus) {
        when (status) {
            CheckFundsStatus.OK -> openConfirmationScreen()
            CheckFundsStatus.WILL_DESTROY_ACCOUNT -> _showAccountRemovalWarning.value = Event(Unit)
            CheckFundsStatus.NOT_ENOUGH_FUNDS -> showError(resourceManager.getString(R.string.choose_amount_error_too_big))
        }
    }

    private fun openConfirmationScreen() {
        val transferDraft = buildTransferDraft() ?: return

        router.openConfirmTransfer(transferDraft)
    }

    private fun buildTransferDraft(): TransferDraft? {
        val amount = amountEventsSubject.value ?: return null
        val fee = feeLiveData.value!!.amount ?: return null
        val asset = assetLiveData.value ?: return null

        return TransferDraft(amount, fee, asset.available, asset.total, asset.token, recipientAddress)
    }

    private fun retryLoadFee() {
        amountEventsSubject.onNext(amountEventsSubject.value!!)
    }
}
