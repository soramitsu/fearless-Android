package jp.co.soramitsu.soracard.impl.presentation.details

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_soracard_impl.R
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardDetailsCallback
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardDetailsScreen
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardDetailsScreenState
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardIBANCardState
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardMainSoraContentCardState
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardRecentActivitiesCardState
import jp.co.soramitsu.oauth.uiscreens.clientsui.soracarddetails.SoraCardSettingsCardState

@Composable
internal fun SoraCardDetailsScreenInternal(
    scrollState: ScrollState,
    state: SoraCardDetailsScreenState,
    callback: SoraCardDetailsCallback,
    onBack: () -> Unit,
) {
    val toolbarViewState = ToolbarViewState(
        title = "Card details",
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
            onNavigationClick = onBack,
        )
        MarginVertical(margin = 8.dp)
        SoraCardDetailsScreen(
            scrollState = scrollState,
            soraCardDetailsScreenState = state,
            callback = callback,
        )
    }
}

@Composable
@Preview(showBackground = true)
private fun PreviewSoraCardDetailsScreenInternal() {
    FearlessAppTheme {
        SoraCardDetailsScreenInternal(
            rememberScrollState(),
            state = SoraCardDetailsScreenState(
                soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
                    balance = "123",
                    phone = "+987",
                    actionsEnabled = true,
                    soraCardMenuActions = emptyList(),
                    canStartGatehubFlow = true,
                ),
                soraCardReferralBannerCardState = true,
                soraCardRecentActivitiesCardState = SoraCardRecentActivitiesCardState(
                    data = emptyList(),
                ),
                soraCardIBANCardState = SoraCardIBANCardState(
                    iban = "IBAN iowhvljvbnl",
                    closed = false,
                ),
                soraCardSettingsCard = SoraCardSettingsCardState(
                    soraCardSettingsOptions = emptyList(),
                    phone = "+567",
                ),
                logoutDialog = false,
                fiatWalletDialog = false,
            ),
            onBack = {},
            callback = object : SoraCardDetailsCallback {
                override fun onCloseReferralBannerClick() {
                    TODO("Not yet implemented")
                }

                override fun onExchangeXorClick() {
                    TODO("Not yet implemented")
                }

                override fun onIbanCardClick() {
                    TODO("Not yet implemented")
                }

                override fun onIbanCardShareClick() {
                    TODO("Not yet implemented")
                }

                override fun onRecentActivityClick(position: Int) {
                    TODO("Not yet implemented")
                }

                override fun onReferralBannerClick() {
                    TODO("Not yet implemented")
                }

                override fun onSettingsOptionClick(position: Int) {
                    TODO("Not yet implemented")
                }

                override fun onShowMoreRecentActivitiesClick() {
                    TODO("Not yet implemented")
                }
            },
        )
    }
}
