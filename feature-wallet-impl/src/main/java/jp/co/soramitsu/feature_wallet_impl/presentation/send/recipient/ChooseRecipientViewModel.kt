package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
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

class ChooseRecipientViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator
) : BaseViewModel() {
    private val searchEventSubject = BehaviorSubject.create<String>()

    private val isQueryEmptyLiveData = MutableLiveData<Boolean>()

    val searchResultLiveData = observeSearchResults().asLiveData()

    val screenStateLiveData = isQueryEmptyLiveData.combine(searchResultLiveData) { isQueryEmpty, searchResult ->
        determineState(isQueryEmpty, searchResult)
    }.distinctUntilChanged()

    fun backClicked() {
        router.back()
    }

    fun recipientSelected(address: String) {
        router.openChooseAmount(address)
    }

    private fun determineState(queryEmpty: Boolean, searchResult: List<Any>): State {
        return when {
            queryEmpty -> State.WELCOME
            searchResult.isEmpty() -> State.EMPTY
            else -> State.CONTENT
        }
    }

    private fun observeSearchResults(): Observable<List<Any>> {
        return searchEventSubject
            .subscribeOn(Schedulers.io())
            .debounce(300, TimeUnit.MILLISECONDS)
            .flatMapSingle { address ->
                interactor.validateSendAddress(address).flatMap { isValidAddress ->
                    interactor.getContacts(address).flatMap { searchResults ->
                        val models = searchResults.map(this::generateModel)
                        val contactsWithHeader = maybeAppendContactsHeader(models)

                        val result = if (isValidAddress) {
                            val searchHeader = getHeader(R.string.search_result_header)

                            val model = generateModel(address)

                            listOf(searchHeader, model) + contactsWithHeader
                        } else {
                            contactsWithHeader
                        }

                        isQueryEmptyLiveData.postValue(address.isEmpty())

                        result.zipSimilar()
                    }
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun getHeader(@StringRes resId: Int): Single<Any> = Single.just(
        ContactsHeader(resourceManager.getString(resId))
    )

    private fun generateModel(address: String): Single<Any> {
        return interactor.getAddressId(address).flatMap { addressId ->
            addressIconGenerator.createAddressIcon(address, addressId, ICON_SIZE_IN_DP)
        }
    }

    private fun maybeAppendContactsHeader(content: List<Single<Any>>): List<Single<Any>> {
        if (content.isEmpty()) return emptyList()

        val header = getHeader(R.string.search_contacts)

        return listOf(header) + content
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
}
