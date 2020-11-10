package jp.co.soramitsu.feature_account_impl.presentation.account.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.WithMnemonic
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.common.mapNetworkTypeToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportSource
import java.util.concurrent.TimeUnit

private const val UPDATE_NAME_INTERVAL_SECONDS = 1L

class AccountDetailsViewModel(
    private val accountInteractor: AccountInteractor,
    private val accountRouter: AccountRouter,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    val accountAddress: String
) : BaseViewModel() {
    private val accountNameChanges = BehaviorSubject.create<String>()

    val accountLiveData = getAccount(accountAddress).asLiveData()

    val networkModel = accountLiveData.map { mapNetworkTypeToNetworkModel(it.network.type) }

    private val _showExportSourceChooser = MutableLiveData<Event<List<ExportSource>>>()
    val showExportSourceChooser: LiveData<Event<List<ExportSource>>> = _showExportSourceChooser

    private val exportSourceTypesLiveData = buildExportSourceTypes().asLiveData()

    init {
        disposables += observeNameChanges()
    }

    fun nameChanged(name: String) {
        accountNameChanges.onNext(name)
    }

    fun backClicked() {
        accountRouter.back()
    }

    private fun getAccount(accountAddress: String): Single<Account> {
        return accountInteractor.getAccount(accountAddress)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun copyAddressClicked() {
        accountLiveData.value?.let {
            clipboardManager.addToClipboard(it.address)

            showMessage(resourceManager.getString(R.string.common_copied))
        }
    }

    fun exportClicked() {
        val sources = exportSourceTypesLiveData.value ?: return

        _showExportSourceChooser.value = Event(sources)
    }

    private fun observeNameChanges(): Disposable {
        return accountNameChanges
            .subscribeOn(Schedulers.io())
            .skipWhile(::nameNotChanged)
            .debounce(UPDATE_NAME_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .switchMapCompletable(::changeName)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
    }

    private fun changeName(newName: String): Completable {
        val account = accountLiveData.value!!

        return accountInteractor.updateAccountName(account, newName)
    }

    private fun nameNotChanged(name: String): Boolean {
        val account = accountLiveData.value

        return account == null || account.name == name
    }

    private fun buildExportSourceTypes(): Single<List<ExportSource>> {
        return accountInteractor.getSecuritySource(accountAddress).map {
            val sources = mutableListOf<ExportSource>()

            if (it is WithMnemonic) sources += ExportSource.Mnemonic
            if (it.seed != null) sources += ExportSource.Seed
            sources += ExportSource.Json

            sources
        }
    }

    fun exportTypeSelected(selected: ExportSource) {
        when (selected) {
            is ExportSource.Json -> accountRouter.openExportJsonPassword(accountAddress)
            is ExportSource.Seed -> accountRouter.openExportSeed(accountAddress)
            is ExportSource.Mnemonic -> accountRouter.openExportMnemonic(accountAddress)
        }
    }
}
