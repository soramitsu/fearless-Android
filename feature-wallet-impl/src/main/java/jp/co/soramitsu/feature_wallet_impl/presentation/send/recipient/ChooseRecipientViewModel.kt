package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.distinctUntilChanged
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.common.utils.zipSimilar
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.model.ContactsHeader
import java.util.concurrent.TimeUnit

private const val ICON_SIZE_IN_DP = 24

enum class State {
    WELCOME, EMPTY, CONTENT
}

private const val INITIAL_QUERY = ""

class ChooseRecipientViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator
) : BaseViewModel() {

    private val searchEventSubject = BehaviorSubject.createDefault(INITIAL_QUERY)

    private val isQueryEmptyLiveData = MutableLiveData<Boolean>()

    val searchResultLiveData = observeSearchResults().asLiveData()

    val screenStateLiveData = isQueryEmptyLiveData.combine(searchResultLiveData) { isQueryEmpty, searchResult ->
        determineState(isQueryEmpty, searchResult)
    }.distinctUntilChanged()

    private val _showChooserEvent = MutableLiveData<Event<Unit>>()
    val showChooserEvent: LiveData<Event<Unit>> = _showChooserEvent

    private val _cameraPermissionGrantedEvent = MutableLiveData<Event<Unit>>()
    val cameraPermissionGrantedEvent: LiveData<Event<Unit>> = _cameraPermissionGrantedEvent

    private val _decodeAddressResult = MutableLiveData<Event<String>>()
    val decodeAddressResult: LiveData<Event<String>> = _decodeAddressResult

    fun backClicked() {
        router.back()
    }

    fun recipientSelected(address: String) {
        router.openChooseAmount(address)
    }

    private fun determineState(queryEmpty: Boolean, searchResult: List<Any>): State {
        return when {
            queryEmpty && searchResult.isEmpty() -> State.WELCOME
            searchResult.isEmpty() -> State.EMPTY
            else -> State.CONTENT
        }
    }

    fun queryChanged(query: String) {
        searchEventSubject.onNext(query)
    }

    fun enterClicked() {
        val value = searchEventSubject.value ?: return

        disposables += interactor.validateSendAddress(value)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { valid -> if (valid) recipientSelected(value) }
    }

    fun scanClicked() {
        _showChooserEvent.value = Event(Unit)
    }

    fun observePermissionRequest(permissionsObservable: Observable<Boolean>) {
        disposables += permissionsObservable
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (it) _cameraPermissionGrantedEvent.value = Event(Unit)
            }, DEFAULT_ERROR_HANDLER)
    }

    fun qrCodeScanned(content: String) {
        disposables += interactor.getRecipientFromQrCodeContent(content)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _decodeAddressResult.value = Event(it)
            }, {
                showError(resourceManager.getString(R.string.invoice_scan_error_no_info))
            })
    }

    fun observeQrCodeDecoding(qrDecodeObservable: Single<String>) {
        disposables += qrDecodeObservable
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                qrCodeScanned(it)
            }, {
                showError(resourceManager.getString(R.string.invoice_scan_error_no_info))
            })
    }

    private fun observeSearchResults(): Observable<List<Any>> {
        return searchEventSubject
            .observeOn(Schedulers.io())
            .throttleLatest(300, TimeUnit.MILLISECONDS)
            .doOnNext { isQueryEmptyLiveData.postValue(it.isEmpty()) }
            .flatMapSingle(this::formSearchResults)
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun formSearchResults(address: String): Single<List<Any>> {
        return interactor.validateSendAddress(address).flatMap { isValidAddress ->
            interactor.getRecipients(address).flatMap { searchResult ->
                val resultWithHeader = maybeAppendResultHeader(isValidAddress, address)
                val myAccountsWithHeader = generateModelsWithHeader(R.string.search_header_my_accounts, searchResult.myAccounts)
                val contactsWithHeader = generateModelsWithHeader(R.string.search_contacts, searchResult.contacts)

                val result = resultWithHeader + myAccountsWithHeader + contactsWithHeader

                result.zipSimilar()
            }
        }
    }

    private fun maybeAppendResultHeader(validAddress: Boolean, address: String): List<Single<out Any>> {
        if (!validAddress) return emptyList()

        return generateModelsWithHeader(R.string.search_result_header, listOf(address))
    }

    private fun generateModelsWithHeader(@StringRes headerRes: Int, addresses: List<String>): List<Single<out Any>> {
        val models = addresses.map(this::generateModel)

        return maybeAppendHeader(headerRes, models)
    }

    private fun maybeAppendHeader(@StringRes headerRes: Int, content: List<Single<out Any>>): List<Single<out Any>> {
        if (content.isEmpty()) return emptyList()

        return appendHeader(headerRes, content)
    }

    private fun appendHeader(@StringRes headerRes: Int, content: List<Single<out Any>>): List<Single<out Any>> {
        val header = getHeader(headerRes)

        return listOf(header) + content
    }

    private fun getHeader(@StringRes resId: Int): Single<Any> = Single.just(
        ContactsHeader(resourceManager.getString(resId))
    )

    private fun generateModel(address: String): Single<AddressModel> {
        return addressIconGenerator.createAddressModel(address, ICON_SIZE_IN_DP)
    }
}
