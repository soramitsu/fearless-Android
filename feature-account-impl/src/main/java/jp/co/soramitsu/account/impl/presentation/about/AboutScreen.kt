package jp.co.soramitsu.account.impl.presentation.about

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.soramitsu.account.impl.presentation.about.model.AboutItemListModel
import jp.co.soramitsu.account.impl.presentation.about.model.AboutItemModel
import jp.co.soramitsu.account.impl.presentation.about.model.AboutSectionModel
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.feature_account_impl.R

@Composable
fun AboutScreen(
    viewModel: AboutViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    CompositionLocalProvider(
        LocalIndication provides rememberRipple(color = colorAccent)
    ) {
        AboutScreenContent(
            items = state.items,
            backClick = viewModel::backButtonPressed
        )
    }
}

@Composable
fun AboutScreenContent(items: List<AboutItemListModel>, backClick: () -> Unit) {
    Box {
        AboutBackground()
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            item {
                MarginVertical(margin = 72.dp)
                Image(res = R.drawable.ic_fearless_logo)
                H2(
                    text = stringResource(id = R.string.about_title),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }
            items(items) {
                when (it) {
                    is AboutItemModel -> AboutScreenItem(it)
                    is AboutSectionModel -> AboutScreenSectionItem(it)
                }
            }
            item {
                MarginVertical(margin = 16.dp)
            }
        }
        Box(
            modifier = Modifier
                .padding(6.dp)
                .padding(top = 24.dp)
                .size(44.dp)
                .clickable {
                    backClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                res = R.drawable.ic_arrow_back_24dp
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAboutScreen() {
    val list = getList()

    AboutScreenContent(list) {}
}

private fun getList() = listOf(
    AboutSectionModel(R.string.fearless_wallet),
    AboutItemModel(
        iconResId = R.drawable.ic_info_primary_24,
        titleResId = R.string.about_website,
        text = BuildConfig.WEBSITE_URL
    ),
    AboutItemModel(
        iconResId = R.drawable.ic_about_wiki,
        titleResId = R.string.about_wiki,
        text = BuildConfig.WIKI_URL
    ),
    AboutItemModel(
        iconResId = R.drawable.ic_about_github,
        titleResId = R.string.about_github,
        text = "resourceManager.getString(R.string.about_version) versionName"
    ),
    AboutItemModel(
        iconResId = R.drawable.ic_terms_conditions_24,
        titleResId = R.string.about_terms
    ),
    AboutItemModel(
        iconResId = R.drawable.ic_terms_conditions_24,
        titleResId = R.string.about_privacy,
        showDivider = false
    ),
    AboutSectionModel(R.string.community),
    AboutItemModel(
        iconResId = R.drawable.ic_about_telegram,
        titleResId = R.string.about_telegram,
        text = BuildConfig.TELEGRAM_URL
    ),
    AboutItemModel(
        iconResId = R.drawable.ic_about_medium,
        titleResId = R.string.about_medium,
        text = BuildConfig.MEDIUM_URL,
        showDivider = false
    ),
    AboutSectionModel(R.string.social_media),
    AboutItemModel(
        iconResId = R.drawable.ic_about_instagram,
        titleResId = R.string.about_instagram,
        text = BuildConfig.INSTAGRAM_URL
    ),
    AboutItemModel(
        iconResId = R.drawable.ic_about_twitter,
        titleResId = R.string.about_twitter,
        text = BuildConfig.TWITTER_URL
    ),
    AboutItemModel(
        iconResId = R.drawable.ic_about_youtube,
        titleResId = R.string.about_youtube,
        text = BuildConfig.YOUTUBE_URL
    ),
    AboutItemModel(
        iconResId = R.drawable.ic_about_announcements,
        titleResId = R.string.about_announcement,
        text = BuildConfig.ANNOUNCEMENT_URL,
        showDivider = false
    ),
    AboutSectionModel(R.string.support_and_feedback),
    AboutItemModel(
        iconResId = R.drawable.ic_about_support,
        titleResId = R.string.about_support,
        text = BuildConfig.SUPPORT_URL
    ),
    AboutItemModel(
        iconResId = R.drawable.ic_about_email,
        titleResId = R.string.about_contact_us,
        text = BuildConfig.EMAIL,
        showDivider = false
    )
)
