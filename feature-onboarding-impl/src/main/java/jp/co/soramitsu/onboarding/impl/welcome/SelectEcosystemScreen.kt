package jp.co.soramitsu.onboarding.impl.welcome

import androidx.annotation.DrawableRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.B1
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.feature_onboarding_impl.R

interface SelectEcosystemScreenCallbacks {
    fun backClicked()
    fun privacyClicked()
    fun termsClicked()
    fun substrateEvmClick()
    fun tonClick()
}

@Suppress("FunctionName")
fun NavGraphBuilder.SelectEcosystemScreen(
    listener: WelcomeViewModel
) {
    composable(WelcomeEvent.Onboarding.SelectEcosystemScreen.route) {
        val state by listener.state.collectAsState()
        FullScreenLoading(state.isLoading) {
            if (!state.isLoading) SelectEcosystemScreenContent(listener)
        }
    }
}

@Composable
private fun SelectEcosystemScreenContent(
    callbacks: SelectEcosystemScreenCallbacks
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .paint(
                painter = painterResource(R.drawable.drawable_background_image),
                contentScale = ContentScale.FillWidth
            )
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Toolbar(
            state = ToolbarViewState(stringResource(R.string.ux_choose_network), R.drawable.ic_arrow_back_24dp),
            onNavigationClick = callbacks::backClicked
        )
        MarginVertical(24.dp)
        H2(text = stringResource(R.string.ux_network_selection_title))
        MarginVertical(12.dp)
        B1(text = stringResource(R.string.ux_network_selection_description))
        MarginVertical(24.dp)
        EcosystemCard(
            stringResource(R.string.ux_networks_ethereum_polkadot),
            stringResource(R.string.ux_assets_ethereum_polkadot),
            R.drawable.background_banner_substrate,
            onClick = callbacks::substrateEvmClick
        )
        MarginVertical(12.dp)
        EcosystemCard(
            stringResource(R.string.ux_network_ton),
            stringResource(R.string.ux_asset_ton),
            R.drawable.background_banner_ton,
            onClick = callbacks::tonClick
        )
        MarginVertical(24.dp)
        TermsAndConditions(callbacks::termsClicked, callbacks::privacyClicked)
        MarginVertical(16.dp)
    }
}

@Composable
fun EcosystemCard(text: String, buttonText: String, @DrawableRes banner: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .paint(
                painter = painterResource(banner),
                contentScale = ContentScale.FillWidth
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(24.dp)
    ) {
        H3(text = text)
        MarginVertical(11.dp)
        BackgroundCorneredWithBorder(
            borderColor = white64,
            backgroundColor = Color.Unspecified
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                text = buttonText,
                style = MaterialTheme.customTypography.header6
            )
        }

    }
}

@Composable
fun TermsAndConditions(termsClicked: () -> Unit, privacyClicked: () -> Unit) {
    Text(
        style = MaterialTheme.customTypography.body1,
        color = Color.White,
        text = stringResource(id = R.string.onboarding_terms_and_conditions_prefix),
        textAlign = TextAlign.Center
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = termsClicked).padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.customTypography.body1,
            color = colorAccentDark,
            text = stringResource(id = R.string.onboarding_terms_and_conditions_2)
        )
        Text(
            modifier = Modifier.padding(horizontal = 3.dp),
            style = MaterialTheme.customTypography.body1,
            color = Color.White,
            text = stringResource(id = R.string.common_and)
        )
        Text(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = privacyClicked).padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.customTypography.body1,
            color = colorAccentDark,
            text = stringResource(id = R.string.onboarding_privacy_policy)
        )
    }
}

@Preview
@Composable
fun SelectEcosystemScreenPreview() {
    FearlessAppTheme {
        SelectEcosystemScreenContent(object : SelectEcosystemScreenCallbacks {
            override fun backClicked() = Unit
            override fun privacyClicked() = Unit
            override fun termsClicked() = Unit
            override fun substrateEvmClick() = Unit
            override fun tonClick() = Unit
        })
    }
}