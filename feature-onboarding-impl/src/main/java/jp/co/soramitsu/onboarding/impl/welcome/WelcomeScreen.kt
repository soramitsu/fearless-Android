package jp.co.soramitsu.onboarding.impl.welcome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.GoogleButton
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.IconButton
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.utils.createSpannable
import jp.co.soramitsu.feature_onboarding_impl.R

data class WelcomeState(
    val isBackVisible: Boolean = false
)

interface WelcomeScreenInterface {
    fun backClicked()

    fun importAccountClicked()
    fun createAccountClicked()
    fun googleSigninClicked()
    fun privacyClicked()
    fun termsClicked()
}

@Composable
fun WelcomeScreen(
    state: WelcomeState,
    callbacks: WelcomeScreenInterface
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(R.drawable.drawable_background_image),
                contentScale = ContentScale.FillWidth
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isBackVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(
                    painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                    onClick = callbacks::backClicked
                )
            }
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Image(res = R.drawable.drawable_fearless_logo)
        }
        AccentButton(
            text = stringResource(id = R.string.username_setup_title_2_0),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(48.dp),
            onClick = callbacks::createAccountClicked
        )
        MarginVertical(margin = 8.dp)
        GrayButton(
            text = stringResource(id = R.string.onboarding_restore_wallet),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(48.dp),
            onClick = callbacks::importAccountClicked
        )
        MarginVertical(margin = 8.dp)
        GoogleButton(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            onClick = callbacks::googleSigninClicked
        )
        MarginVertical(margin = 68.dp)

        val terms = stringResource(id = R.string.onboarding_terms_and_conditions_1)
        val privacy = stringResource(id = R.string.onboarding_terms_and_conditions_2)
        Text(
            style = MaterialTheme.customTypography.body1,
            color = Color.White,
            text = createSpannable(stringResource(id = R.string.onboarding_terms_and_conditions)) {
                clickable(terms) {
                    callbacks.termsClicked()
                }

                clickable(privacy) {
                    callbacks.privacyClicked()
                }
            }.toString()
        )
        MarginVertical(margin = 40.dp)
    }
}

@Composable
@Preview
private fun WelcomeScreenPreview() {
    FearlessAppTheme {
        WelcomeScreen(
            state = WelcomeState(isBackVisible = true),
            callbacks = object : WelcomeScreenInterface {
                override fun backClicked() {}
                override fun importAccountClicked() {}
                override fun createAccountClicked() {}
                override fun googleSigninClicked() {}
                override fun privacyClicked() {}
                override fun termsClicked() {}
            }
        )
    }
}
