package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount

import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
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
import java.util.concurrent.TimeUnit

// TODO use dp
private const val ICON_SIZE_IN_PX = 70

class ChooseAmountViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val iconGenerator: IconGenerator,
    private val clipboardManager: ClipboardManager,
    private val recipientAddress: String
) : BaseViewModel() {
    val recipientModelLiveData = generateAddressModel(recipientAddress).asLiveData()

    val feeLiveData = observeFee().asLiveData()

    private val _feeLoadingLiveData = MutableLiveData(false)
    val feeLoadingLiveData = _feeLoadingLiveData

    private val amountEventsSubject = BehaviorSubject.createDefault(BigDecimal.ZERO)

    private val currentAssetObservable = interactor.observeCurrentAsset()

    val assetLiveData = currentAssetObservable
        .map(::mapAssetToAssetModel)
        .asLiveData()

    fun amountChanged(newAmountRaw: String) {
        val newAmount = newAmountRaw.toBigDecimalOrNull() ?: return

        amountEventsSubject.onNext(newAmount)
    }

    fun backClicked() {
        router.back()
    }

    fun copyRecipientAddressClicked() {
        recipientModelLiveData.value?.let {
            clipboardManager.addToClipboard(it.address)

            showMessage(resourceManager.getString(R.string.common_copied))
        }
    }

    private fun observeFee(): Observable<Fee> {
        val debouncedAmountEvents = amountEventsSubject
            .debounce(1, TimeUnit.SECONDS)
            .doOnNext { _feeLoadingLiveData.value = true }

        return Observable.combineLatest(debouncedAmountEvents, currentAssetObservable, BiFunction<BigDecimal, Asset, Transfer> { amount, asset ->
            Transfer(recipientAddress, amount, asset.token)
        })
            .flatMapSingle(interactor::getTransferFee)
            .doOnNext { _feeLoadingLiveData.value = false }
    }

    private fun generateAddressModel(address: String): Single<AddressModel> {
        return interactor.getAddressId(address)
            .map { iconGenerator.getSvgImage(it, ICON_SIZE_IN_PX) }
            .map { AddressModel(address, it) }
    }
}
