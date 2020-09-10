package jp.co.soramitsu.feature_account_impl.presentation.profile

import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.LiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

private const val ICON_SIZE_IN_PX = 100
private const val ADDRESS_CHARACTERS_TRUNCATE = 6

class ProfileViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val iconGenerator: IconGenerator,
    private val clipboardManager: ClipboardManager
) : BaseViewModel() {

    companion object {
        private const val LABEL_ADDRESS = "label_address"
    }

    val account: LiveData<Account> = interactor.getSelectedAccount().asMutableLiveData()
    val shortenAddress: LiveData<String> = account.map(::shortenAddress)
    val accountIconLiveData: LiveData<PictureDrawable> = generateIcon().asMutableLiveData()

    val selectedNetworkLiveData: LiveData<String> =
        interactor.getSelectedNetworkName().asMutableLiveData()

    val selectedLanguageLiveData: LiveData<String> =
        interactor.getSelectedLanguage().asMutableLiveData()

    fun addressCopyClicked() {
        account.value?.let {
            clipboardManager.addToClipboard(LABEL_ADDRESS, it.address)
        }
    }

    fun accountViewClicked() {
        // TODO: 8/26/20 go to account managment 
    }

    private fun shortenAddress(account: Account): String {
        val address = account.address

        return "${address.take(ADDRESS_CHARACTERS_TRUNCATE)}...${address.takeLast(
            ADDRESS_CHARACTERS_TRUNCATE
        )}"
    }

    private fun generateIcon(): Single<PictureDrawable> {
        return interactor.getAddressId()
            .subscribeOn(Schedulers.io())
            .map { iconGenerator.getSvgImage(it, ICON_SIZE_IN_PX) }
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun aboutClicked() {
        router.openAboutScreen()
    }
}