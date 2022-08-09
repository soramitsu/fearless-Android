package jp.co.soramitsu.feature_account_impl.presentation.about

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val router: AccountRouter,
    context: Context,
    private val appLinksProvider: AppLinksProvider,
    resourceManager: ResourceManager
) : BaseViewModel(), Browserable {

    private val _uiState = mutableStateOf<LoadingState<AboutState>>(LoadingState.Loading())
    val uiState: State<LoadingState<AboutState>>
        get() = _uiState

    private val _websiteLiveData = MutableLiveData<String>()
    val websiteLiveData: LiveData<String> = _websiteLiveData

    private val _twitterLiveData = MutableLiveData<String>()
    val twitterLiveData: LiveData<String> = _twitterLiveData

    private val _youtubeLiveData = MutableLiveData<String>()
    val youtubeLiveData: LiveData<String> = _youtubeLiveData

    private val _instagramLiveData = MutableLiveData<String>()
    val instagramLiveData: LiveData<String> = _instagramLiveData

    private val _mediumLiveData = MutableLiveData<String>()
    val mediumLiveData: LiveData<String> = _mediumLiveData

    private val _versionLiveData = MutableLiveData<String>()
    val versionLiveData: LiveData<String> = _versionLiveData

    private val _wikiLiveData = MutableLiveData<String>()
    val wikiLiveData: LiveData<String> = _wikiLiveData

    private val _telegramLiveData = MutableLiveData<String>()
    val telegramLiveData: LiveData<String> = _telegramLiveData

    private val _announcementLiveData = MutableLiveData<String>()
    val announcementLiveData: LiveData<String> = _announcementLiveData

    private val _supportLiveData = MutableLiveData<String>()
    val supportLiveData: LiveData<String> = _supportLiveData

    private val _emailLiveData = MutableLiveData<String>()
    val emailLiveData: LiveData<String> = _emailLiveData

    private val _openSendEmailEvent = MutableLiveData<Event<String>>()
    val openSendEmailEvent: LiveData<Event<String>> = _openSendEmailEvent

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    init {
        _websiteLiveData.value = removeScheme(BuildConfig.WEBSITE_URL)
        _twitterLiveData.value = removeScheme(BuildConfig.TWITTER_URL)
        _youtubeLiveData.value = removeScheme(BuildConfig.YOUTUBE_URL)
        _instagramLiveData.value = removeScheme(BuildConfig.INSTAGRAM_URL)
        _mediumLiveData.value = removeScheme(BuildConfig.MEDIUM_URL)

        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        _versionLiveData.value = "${resourceManager.getString(R.string.about_version)} $versionName"

        _wikiLiveData.value = removeScheme(BuildConfig.WIKI_URL)
        _telegramLiveData.value = removeScheme(BuildConfig.TELEGRAM_URL)
        _announcementLiveData.value = removeScheme(BuildConfig.ANNOUNCEMENT_URL)
        _supportLiveData.value = removeScheme(BuildConfig.SUPPORT_URL)
        _emailLiveData.value = BuildConfig.EMAIL
    }

    private fun removeScheme(url: String): String {
        return url.removePrefix("https://")
    }

    fun backButtonPressed() {
        router.backToProfileScreen()
    }

    fun websiteClicked() {
        openBrowserEvent.value = Event(BuildConfig.WEBSITE_URL)
    }

    fun twitterClicked() {
        openBrowserEvent.value = Event(BuildConfig.TWITTER_URL)
    }

    fun youtubeClicked() {
        openBrowserEvent.value = Event(BuildConfig.YOUTUBE_URL)
    }

    fun instagramClicked() {
        openBrowserEvent.value = Event(BuildConfig.INSTAGRAM_URL)
    }

    fun mediumClicked() {
        openBrowserEvent.value = Event(BuildConfig.MEDIUM_URL)
    }

    fun githubClicked() {
        openBrowserEvent.value = Event(BuildConfig.GITHUB_URL)
    }

    fun wikiClicked() {
        openBrowserEvent.value = Event(BuildConfig.WIKI_URL)
    }

    fun telegramClicked() {
        openBrowserEvent.value = Event(BuildConfig.TELEGRAM_URL)
    }

    fun announcementClicked() {
        openBrowserEvent.value = Event(BuildConfig.ANNOUNCEMENT_URL)
    }

    fun supportClicked() {
        openBrowserEvent.value = Event(BuildConfig.SUPPORT_URL)
    }

    fun emailClicked() {
        _openSendEmailEvent.value = Event(BuildConfig.EMAIL)
    }

    fun termsClicked() {
        openBrowserEvent.value = Event(appLinksProvider.termsUrl)
    }

    fun privacyClicked() {
        openBrowserEvent.value = Event(appLinksProvider.privacyUrl)
    }
}
