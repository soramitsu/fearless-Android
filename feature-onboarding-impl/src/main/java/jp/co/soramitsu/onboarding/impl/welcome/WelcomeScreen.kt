package jp.co.soramitsu.onboarding.impl.welcome

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.GoogleButton
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.IconButton
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TransparentBorderedButton
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.feature_onboarding_impl.R
import kotlinx.coroutines.flow.StateFlow

data class WelcomeState(
    val isLoading: Boolean = false,
    val isBackVisible: Boolean = false,
    val preinstalledFeatureEnabled: Boolean = false
)

interface WelcomeScreenInterface {
    fun backClicked()

    fun importAccountClicked(accountType: AccountType?)
    fun createAccountClicked(accountType: AccountType?)
    fun googleSigninClicked()
    fun getPreInstalledWalletClicked()
    fun privacyClicked()
    fun termsClicked()
}

@Suppress("FunctionName")
fun NavGraphBuilder.WelcomeScreen(
    welcomeStateFlow: StateFlow<WelcomeState>,
    isGoogleAvailable: Boolean,
    callbacks: WelcomeScreenInterface
) {
    composable(
        WelcomeEvent.Onboarding.WelcomeScreen.route,
        arguments = listOf(
            navArgument("accountType") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        val state by welcomeStateFlow.collectAsState()
        val accountType = it.arguments?.getString("accountType")?.takeIf { value -> value.isNotEmpty() }?.let { stringValue ->
            AccountType.valueOf(stringValue)
        }

        FullScreenLoading(state.isLoading) {
            WelcomeScreenContent(
                state = state,
                isGoogleAvailable = isGoogleAvailable,
                accountType = accountType,
                callbacks = callbacks
            )
        }
    }
}

@Composable
private fun WelcomeScreenContent(
    state: WelcomeState,
    isGoogleAvailable: Boolean,
    accountType: AccountType?,
    callbacks: WelcomeScreenInterface
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                    contentDescription = stringResource(R.string.ux_back),
                    onClick = callbacks::backClicked
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
            Image(res = R.drawable.drawable_fearless_logo)
        }
        AccentButton(
            text = stringResource(id = R.string.username_setup_title_2_0),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            onClick = { callbacks.createAccountClicked(accountType) }
        )
        MarginVertical(margin = 8.dp)
        GrayButton(
            text = stringResource(id = R.string.onboarding_restore_wallet),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            onClick = { callbacks.importAccountClicked(accountType) }
        )

        if (isGoogleAvailable && accountType == AccountType.SubstrateOrEvm) {
            MarginVertical(margin = 8.dp)
            GoogleButton(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                onClick = callbacks::googleSigninClicked
            )
        }
        if (state.preinstalledFeatureEnabled && accountType == AccountType.SubstrateOrEvm) {
            MarginVertical(margin = 8.dp)
            TransparentBorderedButton(
                iconRes = R.drawable.ic_common_receive,
                text = stringResource(R.string.onboarding_preinstalled_wallet_button_text),
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                onClick = callbacks::getPreInstalledWalletClicked
            )
        }
        MarginVertical(margin = 68.dp)

        Text(
            style = MaterialTheme.customTypography.body1,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
            text = stringResource(id = R.string.onboarding_terms_and_conditions_prefix)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = callbacks::termsClicked).padding(vertical = 8.dp),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = callbacks::privacyClicked).padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.customTypography.body1,
                color = colorAccentDark,
                text = stringResource(id = R.string.onboarding_privacy_policy)
            )
        }
        MarginVertical(margin = 40.dp)
    }
}

@Composable
@Preview
private fun WelcomeScreenPreview() {
    FearlessAppTheme {
        WelcomeScreenContent(
            state = WelcomeState(isLoading = true, isBackVisible = true),
            isGoogleAvailable = true,
            accountType = AccountType.SubstrateOrEvm,
            callbacks = object : WelcomeScreenInterface {
                override fun backClicked() {}
                override fun importAccountClicked(accountType: AccountType?) {}
                override fun createAccountClicked(accountType: AccountType?) {}
                override fun googleSigninClicked() {}
                override fun getPreInstalledWalletClicked() {}
                override fun privacyClicked() {}
                override fun termsClicked() {}
            }
        )
    }
}
