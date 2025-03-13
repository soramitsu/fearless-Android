package jp.co.soramitsu.onboarding.impl.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_onboarding_impl.R
import kotlinx.coroutines.flow.StateFlow

@Stable
fun interface OnboardingSplashScreenClickListener {
    fun onStart()
}

@Suppress("FunctionName")
fun NavGraphBuilder.OnboardingSplashScreen() {
    composable(WelcomeEvent.Onboarding.SplashScreen.route) {
        OnboardingSplashScreenContent()
    }
}

@Composable
private fun OnboardingSplashScreenContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(R.drawable.drawable_background_image),
                contentScale = ContentScale.FillWidth
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                painter = painterResource(id = R.drawable.fearless_bird_glossy),
                contentDescription = null,
                contentScale = ContentScale.FillWidth
            )

            Image(
                modifier = Modifier.fillMaxWidth(),
                painter = painterResource(id = R.drawable.defi_wallet_title),
                contentDescription = null
            )
        }


//        if (isAccountSelected.not()) {
//            AccentButton(
//                text = stringResource(id = R.string.common_start),
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(48.dp)
//                    .padding(horizontal = 16.dp),
//                onClick = listener::onStart
//            )
//        }

        MarginVertical(margin = 16.dp)
    }
}

@Composable
@Preview
private fun OnboardingSplashScreenPreview() {
    FearlessAppTheme {
        OnboardingSplashScreenContent()
    }
}