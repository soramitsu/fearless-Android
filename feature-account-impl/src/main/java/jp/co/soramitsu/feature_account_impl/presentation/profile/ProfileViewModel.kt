package jp.co.soramitsu.feature_account_impl.presentation.profile

import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.LiveData
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

private const val ICON_SIZE_IN_PX = 100

class ProfileViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val iconGenerator: IconGenerator,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val selectedAccountObservable = interactor.observeSelectedAccount()

    val selectedAccount: LiveData<Account> = selectedAccountObservable.subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .asLiveData()

    val accountIconLiveData: LiveData<PictureDrawable> =
        observeIcon(selectedAccountObservable).asMutableLiveData()

    val selectedLanguageLiveData: LiveData<String> =
        interactor.getSelectedLanguage().asMutableLiveData()

    fun addressCopyClicked() {
        selectedAccount.value?.let {
            clipboardManager.addToClipboard(it.address)

            showMessage(resourceManager.getString(R.string.common_copied))
        }
    }

    private fun observeIcon(accountObservable: Observable<Account>): Observable<PictureDrawable> {
        return accountObservable
            .map { interactor.getAddressId(it).blockingGet() }
            .subscribeOn(Schedulers.io())
            .map { iconGenerator.getSvgImage(it, ICON_SIZE_IN_PX) }
            .observeOn(AndroidSchedulers.mainThread())
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
}