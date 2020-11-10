package jp.co.soramitsu.feature_account_impl.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.language.mapper.mapLanguageToLanguageModel
import jp.co.soramitsu.feature_account_impl.presentation.language.model.LanguageModel

private const val AVATAR_SIZE_DP = 32

class ProfileViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val appLinksProvider: AppLinksProvider
) : BaseViewModel() {

    private val selectedAccountObservable = interactor.observeSelectedAccount()

    private val _showAccountActionsEvent = MutableLiveData<Event<Unit>>()
    val showAccountActionsEvent: LiveData<Event<Unit>> = _showAccountActionsEvent

    private val _openBrowserEvent = MutableLiveData<Event<String>>()
    val openBrowserEvent: LiveData<Event<String>> = _openBrowserEvent

    val selectedAccountLiveData: LiveData<Account> = selectedAccountObservable.subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .asLiveData()

    val accountIconLiveData: LiveData<AddressModel> =
        observeIcon(selectedAccountObservable).asMutableLiveData()

    val selectedLanguageLiveData: LiveData<LanguageModel> =
        getSelectedLanguage().asMutableLiveData()

    fun addressCopyClicked(address: String) {
        clipboardManager.addToClipboard(address)

        showMessage(resourceManager.getString(R.string.common_copied))
    }

    fun viewExternalClicked(externalAnalyzer: ExternalAnalyzer, address: String) {
        val account = selectedAccountLiveData.value!!

        val link = appLinksProvider.getExternalAddressUrl(externalAnalyzer, address, account.network.type)

        _openBrowserEvent.value = Event(link)
    }

    private fun observeIcon(accountObservable: Observable<Account>): Observable<AddressModel> {
        return accountObservable
            .subscribeOn(Schedulers.io())
            .flatMapSingle { account ->
                interactor.getAddressId(account).flatMap { accountId ->
                    addressIconGenerator.createAddressModel(account.address, accountId, AVATAR_SIZE_DP)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun getSelectedLanguage(): Single<LanguageModel> {
        return interactor.getSelectedLanguage()
            .map(::mapLanguageToLanguageModel)
    }

    fun aboutClicked() {
        router.openAboutScreen()
    }

    fun accountsClicked() {
        router.openAccounts()
    }

    fun networksClicked() {
        router.openNodes()
    }

    fun languagesClicked() {
        router.openLanguages()
    }

    fun accountActionsClicked() {
        _showAccountActionsEvent.value = Event(Unit)
    }
}