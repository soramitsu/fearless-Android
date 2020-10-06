package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount

import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.AddressModel
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

// TODO use dp
private const val ICON_SIZE_IN_PX = 70

private const val RETRY_TIMES = 3L

class ChooseAmountViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val iconGenerator: IconGenerator,
    private val clipboardManager: ClipboardManager,
    private val recipientAddress: String
) : BaseViewModel() {

    val recipientModelLiveData = generateAddressModel(recipientAddress).asLiveData()

    private val amountEventsSubject = BehaviorSubject.createDefault(BigDecimal.ZERO)

    private val currentAssetObservable = interactor.observeCurrentAsset()

    val feeLiveData = observeFee().asLiveData()

    private val _feeLoadingLiveData = MutableLiveData<Boolean>()
    val feeLoadingLiveData = _feeLoadingLiveData

    private val _feeErrorLiveData = MutableLiveData<Event<Unit>>()
    val feeErrorLiveData = _feeErrorLiveData

    val continueEnabledLiveData = feeLoadingLiveData.combine(feeLiveData) { loading, fee ->
        !loading && fee.amount != null
    }

    val assetLiveData = currentAssetObservable
        .subscribeOn(Schedulers.io())
        .map(::mapAssetToAssetModel)
        .observeOn(AndroidSchedulers.mainThread())
        .asLiveData()

    fun amountChanged(newAmountRaw: String) {
        val newAmount = newAmountRaw.toBigDecimalOrNull() ?: return

        amountEventsSubject.onNext(newAmount)
    }

    fun backClicked() {
        router.back()
    }

    fun retry() {
        amountEventsSubject.onNext(amountEventsSubject.value!!)
    }

    fun copyRecipientAddressClicked() {
        recipientModelLiveData.value?.let {
            clipboardManager.addToClipboard(it.address)

            showMessage(resourceManager.getString(R.string.common_copied))
        }
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
                    .doOnError { _feeErrorLiveData.postValue(Event(Unit)) }
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
            .map { iconGenerator.getSvgImage(it, ICON_SIZE_IN_PX) }
            .map { AddressModel(address, it) }
    }
}
