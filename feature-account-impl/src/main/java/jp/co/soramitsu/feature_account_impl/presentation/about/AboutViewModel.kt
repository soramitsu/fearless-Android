package jp.co.soramitsu.feature_account_impl.presentation.about

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

class AboutViewModel(
    private val router: AccountRouter,
    private val context: Context,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _websiteLiveData = MutableLiveData<String>()
    val websiteLiveData: LiveData<String> = _websiteLiveData

    private val _versionLiveData = MutableLiveData<String>()
    val versionLiveData: LiveData<String> = _versionLiveData

    private val _telegramLiveData = MutableLiveData<String>()
    val telegramLiveData: LiveData<String> = _telegramLiveData

    private val _emailLiveData = MutableLiveData<String>()
    val emailLiveData: LiveData<String> = _emailLiveData

    private val _openSendEmailEvent = MutableLiveData<Event<String>>()
    val openSendEmailEvent: LiveData<Event<String>> = _openSendEmailEvent

    private val _showBrowserLiveData = MutableLiveData<Event<String>>()
    val showBrowserLiveData: LiveData<Event<String>> = _showBrowserLiveData

    init {
        _websiteLiveData.value = BuildConfig.WEBSITE_URL

        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        _versionLiveData.value = "${resourceManager.getString(R.string.about_version)} $versionName"

        _telegramLiveData.value = BuildConfig.TELEGRAM_URL
        _emailLiveData.value = BuildConfig.EMAIL
    }

    fun backButtonPressed() {
        // todo  back to profile
    }

    fun websiteClicked() {
        _showBrowserLiveData.value = Event(BuildConfig.WEBSITE_URL)
    }

    fun githubClicked() {
        _showBrowserLiveData.value = Event(BuildConfig.GITHUB_URL)
    }

    fun telegramClicked() {
        _showBrowserLiveData.value = Event(BuildConfig.TELEGRAM_URL)
    }

    fun emailClicked() {
        _openSendEmailEvent.value = Event(BuildConfig.EMAIL)
    }

    fun termsClicked() {
        router.openTermsScreen()
    }

    fun privacyClicked() {
        router.openPrivacyScreen()
    }
}