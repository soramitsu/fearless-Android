package jp.co.soramitsu.account.impl.presentation.about

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.about.model.AboutItemModel
import jp.co.soramitsu.account.impl.presentation.about.model.AboutSectionModel
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val router: AccountRouter,
    context: Context,
    private val appLinksProvider: AppLinksProvider,
    resourceManager: ResourceManager
) : BaseViewModel(), Browserable {

    private val versionName = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } else {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    }

    val list = listOf(
        AboutSectionModel(R.string.fearless_wallet),
        AboutItemModel(
            iconResId = R.drawable.ic_info_primary_24,
            titleResId = R.string.about_website,
            text = removeScheme(BuildConfig.WEBSITE_URL),
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.WEBSITE_URL)
            }
        ),
        AboutItemModel(
            iconResId = R.drawable.ic_about_wiki,
            titleResId = R.string.about_wiki,
            text = removeScheme(BuildConfig.WIKI_URL),
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.WIKI_URL)
            }
        ),
        AboutItemModel(
            iconResId = R.drawable.ic_about_github,
            titleResId = R.string.about_github,
            text = "${resourceManager.getString(R.string.about_version)} $versionName",
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.GITHUB_URL)
            }
        ),
        AboutItemModel(
            iconResId = R.drawable.ic_terms_conditions_24,
            titleResId = R.string.about_terms,
            onClick = {
                openBrowserEvent.value = Event(appLinksProvider.termsUrl)
            }
        ),
        AboutItemModel(
            iconResId = R.drawable.ic_terms_conditions_24,
            titleResId = R.string.about_privacy,
            showDivider = false,
            onClick = {
                openBrowserEvent.value = Event(appLinksProvider.privacyUrl)
            }
        ),
        AboutSectionModel(R.string.community),
        AboutItemModel(
            iconResId = R.drawable.ic_about_telegram,
            titleResId = R.string.about_telegram,
            text = removeScheme(BuildConfig.TELEGRAM_URL),
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.TELEGRAM_URL)
            }
        ),
        AboutItemModel(
            iconResId = R.drawable.ic_about_medium,
            titleResId = R.string.about_medium,
            text = removeScheme(BuildConfig.MEDIUM_URL),
            showDivider = false,
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.MEDIUM_URL)
            }
        ),
        AboutSectionModel(R.string.social_media),
        AboutItemModel(
            iconResId = R.drawable.ic_about_instagram,
            titleResId = R.string.about_instagram,
            text = removeScheme(BuildConfig.INSTAGRAM_URL),
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.INSTAGRAM_URL)
            }
        ),
        AboutItemModel(
            iconResId = R.drawable.ic_about_twitter,
            titleResId = R.string.about_twitter,
            text = removeScheme(BuildConfig.TWITTER_URL),
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.TWITTER_URL)
            }
        ),
        AboutItemModel(
            iconResId = R.drawable.ic_about_youtube,
            titleResId = R.string.about_youtube,
            text = removeScheme(BuildConfig.YOUTUBE_URL),
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.YOUTUBE_URL)
            }
        ),
        AboutItemModel(
            iconResId = R.drawable.ic_about_announcements,
            titleResId = R.string.about_announcement,
            text = removeScheme(BuildConfig.ANNOUNCEMENT_URL),
            showDivider = false,
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.ANNOUNCEMENT_URL)
            }
        ),
        AboutSectionModel(R.string.support_and_feedback),
        AboutItemModel(
            iconResId = R.drawable.ic_about_support,
            titleResId = R.string.about_support,
            text = removeScheme(BuildConfig.SUPPORT_URL),
            onClick = {
                openBrowserEvent.value = Event(BuildConfig.SUPPORT_URL)
            }
        ),
        AboutItemModel(
            iconResId = R.drawable.ic_about_email,
            titleResId = R.string.about_contact_us,
            text = BuildConfig.EMAIL,
            showDivider = false,
            onClick = {
                _openSendEmailEvent.value = Event(BuildConfig.EMAIL)
            }
        )
    )

    val uiState: StateFlow<AboutState> = flowOf(AboutState(list)).stateIn(viewModelScope, SharingStarted.Eagerly, AboutState(list))

    private val _openSendEmailEvent = MutableLiveData<Event<String>>()
    val openSendEmailEvent: LiveData<Event<String>> = _openSendEmailEvent

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private fun removeScheme(url: String): String {
        return url.removePrefix("https://")
    }

    fun backButtonPressed() {
        router.backToProfileScreen()
    }
}
