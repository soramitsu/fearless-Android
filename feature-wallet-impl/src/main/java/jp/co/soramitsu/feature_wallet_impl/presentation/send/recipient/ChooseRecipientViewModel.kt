package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.distinctUntilChanged
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.model.ContactModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.model.ContactsHeader
import java.util.concurrent.TimeUnit

// TODO use dp
private const val ICON_SIZE_IN_PX = 70

enum class State {
    WELCOME, EMPTY, CONTENT
}

class ChooseRecipientViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val iconGenerator: IconGenerator
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
        showMessage("Selected: $address")
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
            .map { address ->
                val isValidAddress = interactor.validateSendAddress(address).blockingGet()
                val searchResults = interactor.getContacts(address).blockingGet()

                val models = searchResults.map(this::generateModel)
                val contactsWithHeader = appendContactsHeader(models)

                val result = if (isValidAddress) {
                    val searchHeader = ContactsHeader(resourceManager.getString(R.string.search_result_header))

                    val model = generateModel(address)

                    listOf(searchHeader, model) + contactsWithHeader
                } else {
                    contactsWithHeader
                }

                isQueryEmptyLiveData.postValue(address.isEmpty())

                result
            }
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun generateModel(address: String): ContactModel {
        val addressId = interactor.getAddressId(address).blockingGet()

        val icon = iconGenerator.getSvgImage(addressId, ICON_SIZE_IN_PX)

        return ContactModel(address, icon)
    }

    private fun appendContactsHeader(content: List<Any>): List<Any> {
        if (content.isEmpty()) return emptyList()

        val header = ContactsHeader(resourceManager.getString(R.string.search_contacts))

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
