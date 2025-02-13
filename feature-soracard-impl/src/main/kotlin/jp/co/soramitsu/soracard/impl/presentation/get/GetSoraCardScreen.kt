package jp.co.soramitsu.soracard.impl.presentation.get

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_soracard_impl.R
import jp.co.soramitsu.oauth.uiscreens.clientsui.GetSoraCardScreen
import jp.co.soramitsu.oauth.uiscreens.clientsui.GetSoraCardState
import jp.co.soramitsu.oauth.uiscreens.clientsui.UiStyle
import jp.co.soramitsu.oauth.uiscreens.clientsui.localCompositionUiStyle
import jp.co.soramitsu.oauth.uiscreens.theme.AuthSdkTheme

interface GetSoraCardScreenInterface {
    fun onSeeBlacklist()
    fun onLogIn()
    fun onSignUp()
    fun onBack()
}

@Composable
fun GetSoraCardScreenWithToolbar(
    state: GetSoraCardState,
    scrollState: ScrollState,
    callbacks: GetSoraCardScreenInterface
) {
    val toolbarViewState = ToolbarViewState(
        title = stringResource(id = R.string.profile_soracard_title),
        navigationIcon = R.drawable.ic_arrow_left_24,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(color = Color.Black)
    ) {
        Toolbar(
            modifier = Modifier.height(62.dp),
            state = toolbarViewState,
            onNavigationClick = callbacks::onBack,
        )
        MarginVertical(margin = 8.dp)
        GetSoraCardScreenInternal(
            state = state,
            scrollState = scrollState,
            callbacks = callbacks,
        )
    }
}

@Composable
private fun GetSoraCardScreenInternal(
    state: GetSoraCardState,
    scrollState: ScrollState,
    callbacks: GetSoraCardScreenInterface,
) {
    CompositionLocalProvider(
        localCompositionUiStyle provides UiStyle.FW
    ) {
        AuthSdkTheme(darkTheme = true) {
            GetSoraCardScreen(
                scrollState = scrollState,
                state = state,
                onBlackList = callbacks::onSeeBlacklist,
                onSignUp = callbacks::onSignUp,
                onLogIn = callbacks::onLogIn,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewGetSoraCardScreen() {
    val empty = object : GetSoraCardScreenInterface {
        override fun onLogIn() {}
        override fun onBack() {}
        override fun onSignUp() {}
        override fun onSeeBlacklist() {}
    }
    FearlessAppTheme(darkTheme = true) {
        GetSoraCardScreenWithToolbar(
            state = GetSoraCardState(
                true,
                true,
                "20",
            ),
            scrollState = rememberScrollState(),
            callbacks = empty
        )
    }
}
