package jp.co.soramitsu.onboarding.impl.welcome

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_onboarding_impl.R

interface SelectEcosystemScreenCallbacks {
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
        SelectEcosystemScreenContent(listener)
    }
}

@Composable
private fun SelectEcosystemScreenContent(
    callbacks: SelectEcosystemScreenCallbacks
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(R.drawable.drawable_background_image),
                contentScale = ContentScale.FillWidth
            )
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier.fillMaxWidth(),
                painter = painterResource(id = R.drawable.drawable_fearless_logo),
                contentDescription = null,
                contentScale = ContentScale.FillWidth
            )
        }
        EcosystemCard(
            stringResource(R.string.onboarding_banner_regular_ecosystem_title),
            stringResource(R.string.onboarding_banner_regular_ecosystem_button_title),
            R.drawable.background_banner_substrate,
            onClick = callbacks::substrateEvmClick
        )
        MarginVertical(12.dp)
        EcosystemCard(
            stringResource(R.string.onboarding_banner_ton_ecosystem_title),
            stringResource(R.string.onboarding_banner_ton_ecosystem_button_title),
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
            .padding(24.dp)
            .clickableWithNoIndication { onClick() }
    ) {
        H2(text = text)
        MarginVertical(11.dp)
        BackgroundCorneredWithBorder(
            modifier = Modifier
                .clickable(onClick = onClick),
            borderColor = white64,
            backgroundColor = Color.Unspecified
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                text = buttonText,
                style = MaterialTheme.customTypography.header4
            )
        }

    }
}

@Composable
fun TermsAndConditions(termsClicked: () -> Unit, privacyClicked: () -> Unit) {
    Text(
        style = MaterialTheme.customTypography.body1,
        color = Color.White,
        text = stringResource(id = R.string.onboarding_terms_and_conditions_prefix)
    )
    Row {
        Text(
            modifier = Modifier.clickable(onClick = termsClicked),
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
            modifier = Modifier.clickable(onClick = privacyClicked),
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
            override fun privacyClicked() = Unit
            override fun termsClicked() = Unit
            override fun substrateEvmClick() = Unit
            override fun tonClick() = Unit
        })
    }
}