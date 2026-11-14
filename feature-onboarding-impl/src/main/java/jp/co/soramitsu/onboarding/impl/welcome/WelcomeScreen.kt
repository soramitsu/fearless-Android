package jp.co.soramitsu.onboarding.impl.welcome

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import jp.co.soramitsu.common.compose.theme.fw.FwBrandTagline
import jp.co.soramitsu.common.compose.theme.fw.FwLockupHero
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
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.IconButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TransparentBorderedButton
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.fwPageBackground
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
    fun savedEndpointsClicked()
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
    if (FwRestyle.isEnabled(FwSurface.Welcome)) {
        CrowWelcomeContent(state, isGoogleAvailable, accountType, callbacks)
    } else {
        LegacyWelcomeContent(state, isGoogleAvailable, accountType, callbacks)
    }
}

// Share of the page height the crow keeps above the lockup, as on the design's Welcome board (448 of 844).
private const val CROW_SHARE = 0.53f

// Welcome as on the brand's Welcome board: the animated crow full-bleed behind the top of the page, the
// stacked lockup, tagline and actions over its dark lower part. The crow is a button that plays its
// call. The dark scrim scrolls with the content, so the lockup never sits over the feathers.
@Composable
private fun CrowWelcomeContent(
    state: WelcomeState,
    isGoogleAvailable: Boolean,
    accountType: AccountType?,
    callbacks: WelcomeScreenInterface
) {
    val c = LocalFwColors.current
    val crow = rememberWelcomeCrowState()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(c.brandInk)) {
        val pageHeight = maxHeight
        val crowHeight = pageHeight * CROW_SHARE
        val crowHeightPx = with(density) { crowHeight.toPx() }
        // Recomposes only when the crow scrolls off or back, not on every scrolled pixel.
        val crowOnScreen by remember(crowHeightPx) { derivedStateOf { scroll.value < crowHeightPx } }
        WelcomeCrowBackdrop(crow, onScreen = crowOnScreen, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().height(crowHeight)) {
                val callLabel = stringResource(R.string.fw_welcome_crow_call)
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = crow::call
                        )
                        .semantics { contentDescription = callLabel }
                        .testTag("welcome_crow")
                )
                if (state.isBackVisible) {
                    IconButton(
                        painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                        contentDescription = stringResource(R.string.ux_back),
                        onClick = callbacks::backClicked,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = pageHeight - crowHeight)
                    .drawBehind { welcomeScrim(c.brandInk) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FwLockupHero(Modifier.padding(horizontal = 16.dp))
                    MarginVertical(margin = 12.dp)
                    FwBrandTagline()
                    MarginVertical(margin = 32.dp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WelcomeActions(state, isGoogleAvailable, accountType, callbacks)
                }
            }
        }
    }
}

// The ink scrim under the lockup and actions: it starts 38 dp above them, reaches 82 % after 75 dp and
// full ink after 188 dp, the gradient of the design's Welcome board.
private fun DrawScope.welcomeScrim(ink: Color) {
    val lead = 38.dp.toPx()
    val solid = 188.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            0f to ink.copy(alpha = 0f),
            (lead + 75.dp.toPx()) / (lead + solid) to ink.copy(alpha = 0.82f),
            1f to ink,
            startY = -lead,
            endY = solid
        ),
        topLeft = Offset(0f, -lead),
        size = Size(size.width, size.height + lead)
    )
}

@Composable
private fun LegacyWelcomeContent(
    state: WelcomeState,
    isGoogleAvailable: Boolean,
    accountType: AccountType?,
    callbacks: WelcomeScreenInterface
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fwPageBackground()
            .verticalScroll(rememberScrollState()),
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

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FwLockupHero()
            MarginVertical(margin = 16.dp)
            H2(text = stringResource(R.string.fearless_wallet), textAlign = TextAlign.Center)
        }
        WelcomeActions(state, isGoogleAvailable, accountType, callbacks)
    }
}

@Composable
private fun WelcomeActions(
    state: WelcomeState,
    isGoogleAvailable: Boolean,
    accountType: AccountType?,
    callbacks: WelcomeScreenInterface
) {
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
    MarginVertical(margin = 16.dp)
    GrayButton(
        text = stringResource(R.string.saved_endpoints_title),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 48.dp),
        onClick = callbacks::savedEndpointsClicked
    )
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
            color = if (FwRestyle.paletteEnabled) LocalFwColors.current.textLink else colorAccentDark,
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
            color = if (FwRestyle.paletteEnabled) LocalFwColors.current.textLink else colorAccentDark,
            text = stringResource(id = R.string.onboarding_privacy_policy)
        )
    }
    MarginVertical(margin = 40.dp)
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
                override fun savedEndpointsClicked() {}
                override fun googleSigninClicked() {}
                override fun getPreInstalledWalletClicked() {}
                override fun privacyClicked() {}
                override fun termsClicked() {}
            }
        )
    }
}
