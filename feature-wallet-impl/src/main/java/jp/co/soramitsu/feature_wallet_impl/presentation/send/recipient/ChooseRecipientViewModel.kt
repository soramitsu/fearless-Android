package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.distinctUntilChanged
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api.PhishingWarningMixin
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api.PhishingWarningPresentation
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.warning.api.proceedOrShowPhishingWarning
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.model.ContactsHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime

private const val ICON_SIZE_IN_DP = 24

enum class State {
    WELCOME, EMPTY, CONTENT
}

private const val INITIAL_QUERY = ""
private const val DEBOUNCE_DURATION = 300L

class ChooseRecipientViewModel(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val qrBitmapDecoder: QrBitmapDecoder,
    private val payload: AssetPayload,
    private val phishingWarning: PhishingWarningMixin,
) : BaseViewModel(),
    PhishingWarningMixin by phishingWarning,
    PhishingWarningPresentation {

    private val searchEvents = MutableStateFlow(INITIAL_QUERY)

    private val isQueryEmptyLiveData = MutableLiveData<Boolean>()

    val searchResultLiveData = observeSearchResults().asLiveData()

    val screenStateLiveData = isQueryEmptyLiveData.combine(searchResultLiveData) { isQueryEmpty, searchResult ->
        determineState(isQueryEmpty, searchResult)
    }.distinctUntilChanged()

    private val _showChooserEvent = MutableLiveData<Event<Unit>>()
    val showChooserEvent: LiveData<Event<Unit>> = _showChooserEvent

    private val _decodeAddressResult = MutableLiveData<Event<String>>()
    val decodeAddressResult: LiveData<Event<String>> = _decodeAddressResult

    private val _declinePhishingAddress = MutableLiveData<Event<Unit>>()
    val declinePhishingAddress: LiveData<Event<Unit>> = _declinePhishingAddress

    fun backClicked() {
        router.back()
    }

    fun recipientSelected(address: String) {
        viewModelScope.launch {
            proceedOrShowPhishingWarning(address)
        }
    }

    override fun proceedAddress(address: String) {
        router.openChooseAmount(address)
    }

    override fun declinePhishingAddress() {
        _declinePhishingAddress.value = Event(Unit)
    }

    private fun determineState(queryEmpty: Boolean, searchResult: List<Any>): State {
        return when {
            queryEmpty && searchResult.isEmpty() -> State.WELCOME
            searchResult.isEmpty() -> State.EMPTY
            else -> State.CONTENT
        }
    }

    fun queryChanged(query: String) {
        viewModelScope.launch { searchEvents.emit(query) }
    }

    fun enterClicked() {
        val input = searchEvents.value

        viewModelScope.launch {
            val valid = interactor.validateSendAddress(payload.chainId, input)

            if (valid) recipientSelected(input)
        }
    }

    fun scanClicked() {
        _showChooserEvent.value = Event(Unit)
    }

    fun qrCodeScanned(content: String) {
        viewModelScope.launch {
            val result = interactor.getRecipientFromQrCodeContent(content)

            if (result.isSuccess) {
                _decodeAddressResult.value = Event(result.requireValue())
            } else {
                showError(resourceManager.getString(R.string.invoice_scan_error_no_info))
            }
        }
    }

    fun qrFileChosen(uri: Uri) {
        viewModelScope.launch {
            val result = qrBitmapDecoder.decodeQrCodeFromUri(uri)

            if (result.isSuccess) {
                qrCodeScanned(result.requireValue())
            } else {
                showError(resourceManager.getString(R.string.invoice_scan_error_no_info))
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun observeSearchResults(): Flow<List<Any>> {
        return searchEvents
            .onEach { isQueryEmptyLiveData.postValue(it.isEmpty()) }
            .mapLatest(this::formSearchResults)
    }

    private suspend fun formSearchResults(address: String): List<Any> = withContext(Dispatchers.Default) {
        val isValidAddress = interactor.validateSendAddress(address, payload.chainId)
        val searchResult = interactor.getRecipients(address, payload.chainId)

        val resultWithHeader = maybeAppendResultHeader(isValidAddress, address)
        val myAccountsWithHeader = generateAccountModelsWithHeader(R.string.search_header_my_accounts, searchResult.myAccounts)
        val contactsWithHeader = generateAddressModelsWithHeader(R.string.search_contacts, searchResult.contacts)

        val result = resultWithHeader + myAccountsWithHeader + contactsWithHeader

        result
    }

    private suspend fun maybeAppendResultHeader(validAddress: Boolean, address: String): List<Any> {
        if (!validAddress) return emptyList()

        return generateAddressModelsWithHeader(R.string.search_result_header, listOf(address))
    }

    private suspend fun generateAccountModelsWithHeader(@StringRes headerRes: Int, accounts: List<WalletAccount>): List<Any> {
        val models = accounts.map { generateAddressModel(it.address, it.name) }

        return maybeAppendHeader(headerRes, models)
    }

    private suspend fun generateAddressModelsWithHeader(@StringRes headerRes: Int, addresses: List<String>): List<Any> {
        val models = addresses.map { generateAddressModel(it) }

        return maybeAppendHeader(headerRes, models)
    }

    private fun maybeAppendHeader(@StringRes headerRes: Int, content: List<Any>): List<Any> {
        if (content.isEmpty()) return emptyList()

        return appendHeader(headerRes, content)
    }

    private fun appendHeader(@StringRes headerRes: Int, content: List<Any>): List<Any> {
        val header = getHeader(headerRes)

        return listOf(header) + content
    }

    private fun getHeader(@StringRes resId: Int) = ContactsHeader(resourceManager.getString(resId))

    private suspend fun generateAddressModel(address: String, accountName: String? = null): AddressModel {
        return addressIconGenerator.createAddressModel(address, ICON_SIZE_IN_DP, accountName)
    }
}
