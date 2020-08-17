package jp.co.soramitsu.feature_account_impl.presentation.profile

import android.graphics.drawable.Drawable
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType

class ProfileViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter,
    private val iconGenerator: IconGenerator,
    private val clipboardManager: ClipboardManager
) : BaseViewModel() {

    companion object {
        private const val ICON_SIZE_IN_PX = 100
        private const val LABEL_ADDRESS = "label_address"
    }

    private val _accountNameLiveData = MutableLiveData<String>()
    val accountNameLiveData: LiveData<String> = _accountNameLiveData

    private val _accountAddressLiveData = MutableLiveData<String>()
    val accountAddressLiveData: LiveData<String> = _accountAddressLiveData

    private val _accountIconLiveData = MutableLiveData<Drawable>()
    val accountIconLiveData: LiveData<Drawable> = _accountIconLiveData

    private val _selectedNetworkLiveData = MutableLiveData<String>()
    val selectedNetworkLiveData: LiveData<String> = _selectedNetworkLiveData

    private val _selectedLanguageLiveData = MutableLiveData<String>()
    val selectedLanguageLiveData: LiveData<String> = _selectedLanguageLiveData

    init {
        disposables.add(
            interactor.getAddress()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _accountAddressLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getUsername()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _accountNameLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getAddressId()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _accountIconLiveData.value = iconGenerator.getSvgImage(it, ICON_SIZE_IN_PX)
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getNetworksWithSelected()
                .subscribeOn(Schedulers.io())
                .map { mapNetworkToSelectedNetworkModel(it.first, it.second) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _selectedNetworkLiveData.value = it.name
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getSelectedLanguage()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _selectedLanguageLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    private fun mapNetworkToSelectedNetworkModel(networks: List<Network>, selected: NetworkType): Network {
        return networks.first { it.networkType == selected }
    }

    fun addressCopyClicked() {
        _accountAddressLiveData.value?.let {
            clipboardManager.addToClipboard(LABEL_ADDRESS, it)
        }
    }
}