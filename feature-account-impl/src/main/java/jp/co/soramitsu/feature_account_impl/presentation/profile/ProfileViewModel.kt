package jp.co.soramitsu.feature_account_impl.presentation.profile

import androidx.lifecycle.LiveData
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.account.external.actions.ExternalAccountActions
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.language.mapper.mapLanguageToLanguageModel
import jp.co.soramitsu.feature_account_impl.presentation.language.model.LanguageModel

private const val AVATAR_SIZE_DP = 32

class ProfileViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation
) : BaseViewModel(), ExternalAccountActions by externalAccountActions {

    private val selectedAccountObservable = interactor.observeSelectedAccount()

    val selectedAccountLiveData: LiveData<Account> = selectedAccountObservable.subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .asLiveData()

    val accountIconLiveData: LiveData<AddressModel> =
        observeIcon(selectedAccountObservable).asMutableLiveData()

    val selectedLanguageLiveData: LiveData<LanguageModel> =
        getSelectedLanguage().asMutableLiveData()

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
        val account = selectedAccountLiveData.value ?: return

        externalAccountActions.showExternalActions(ExternalAccountActions.Payload(account.address, account.network.type))
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
}