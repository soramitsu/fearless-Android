package jp.co.soramitsu.soracard.impl.presentation.get

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.lang.Integer.max
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.TransparentButton
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.feature_soracard_impl.R
import jp.co.soramitsu.oauth.base.extension.testTagAsId
import jp.co.soramitsu.soracard.impl.presentation.SoraCardItem
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.oauth.R as SoraCardR

data class GetSoraCardState(
    val xorRatioAvailable: Boolean? = null
)

interface GetSoraCardScreenInterface {
    fun onSignUp()
    fun onLogIn()
    fun onGetMoreXor()
    fun onSeeBlacklist()
    fun onNavigationClick()
}

@Composable
fun GetSoraCardScreenWithToolbar(
    state: GetSoraCardState,
    scrollState: ScrollState,
    callbacks: GetSoraCardScreenInterface
) {
    val toolbarViewState = ToolbarViewState(
        title = stringResource(id = R.string.profile_soracard_title),
        navigationIcon = R.drawable.ic_arrow_left_24
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(color = Color.Black)
    ) {
        Toolbar(
            modifier = Modifier.height(62.dp),
            state = toolbarViewState,
            onNavigationClick = callbacks::onNavigationClick
        )
        MarginVertical(margin = 8.dp)
        GetSoraCardScreen(
            state = state,
            scrollState = scrollState,
            callbacks = callbacks
        )
    }
}

@Composable
fun GetSoraCardScreen(
    state: GetSoraCardState,
    scrollState: ScrollState,
    callbacks: GetSoraCardScreenInterface
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Dimens.x2)
            .padding(bottom = Dimens.x5)
    ) {
        ContentCard(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color(0xFF1D1D1F))
                    .padding(16.dp)
            ) {
                SoraCardItem(
                    state = null,
                    onClose = null,
                    onClick = {}
                )

                MarginVertical(margin = 16.dp)
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    text = stringResource(R.string.sora_card_title),
                    style = MaterialTheme.customTypography.headline2,
                    color = Color.White
                )

                MarginVertical(margin = 16.dp)
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    text = stringResource(SoraCardR.string.details_description),
                    style = MaterialTheme.customTypography.paragraphM,
                    color = Color.White
                )

                MarginVertical(margin = 16.dp)
                AnnualFee()

                MarginVertical(margin = 16.dp)
                FreeCardIssuance()

                MarginVertical(margin = 16.dp)
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.x1),
                    text = stringResource(SoraCardR.string.unsupported_countries_disclaimer),
                    style = MaterialTheme.customTypography.paragraphXS.copy(
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                )
                Text(
                    modifier = Modifier
                        .wrapContentWidth()
                        .align(Alignment.CenterHorizontally)
                        .testTagAsId("SoraCardResidents")
                        .padding(horizontal = Dimens.x1)
                        .clickable(onClick = callbacks::onSeeBlacklist),
                    text = stringResource(SoraCardR.string.unsupported_countries_link),
                    style = MaterialTheme.customTypography.paragraphXS.copy(
                        textAlign = TextAlign.Center,
                        textDecoration = TextDecoration.Underline,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.customColors.accentPrimary,
                )

                val buttonsEnabled = state.xorRatioAvailable == true
                MarginVertical(margin = 16.dp)
                AccentButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(48.dp),
                    onClick = callbacks::onSignUp,
                    enabled = buttonsEnabled,
                    text = "Sign up for SORA Card"
                )

                MarginVertical(margin = 8.dp)
                TransparentButton(
                    modifier = Modifier
                        .testTag("AlreadyHaveCard")
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(48.dp),
                    text = stringResource(SoraCardR.string.details_already_have_card),
                    enabled = buttonsEnabled,
                    onClick = callbacks::onLogIn
                )

                MarginVertical(margin = 8.dp)
            }
        }
    }
}

@Composable
private fun AnnualFee() {
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 12.dp,
        backgroundColor = backgroundBlack
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(
                    top = Dimens.x2, bottom = Dimens.x2, start = Dimens.x3, end = Dimens.x3,
                ),
            text = AnnotatedString(
                text = stringResource(R.string.sora_card_annual_service_fee),
                spanStyles = listOf(
                    AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp), 0, 3)
                )
            ),
            style = MaterialTheme.customTypography.textL,
            color = Color.White
        )
    }
}

@Composable
private fun FreeCardIssuance() {
    val cardIssuancePriceText = stringResource(SoraCardR.string.card_issuance_screen_free_card_title)
    ContentCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 12.dp,
        backgroundColor = backgroundBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x2, horizontal = Dimens.x3)
        ) {
            Text(
                text = AnnotatedString(
                    text = cardIssuancePriceText,
                    spanStyles = listOf(
                        AnnotatedString.Range(
                            item = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                            start = 0,
                            end = max(4, cardIssuancePriceText.split(" ").getOrNull(0)?.length ?: 0)
                        )
                    )
                ),
                style = MaterialTheme.customTypography.textL,
                color = Color.White
            )
        }
    }
}

@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MaterialTheme.borderRadius.s,
    backgroundColor: Color = MaterialTheme.customColors.bgSurface,
    content: @Composable () -> Unit
) {
    androidx.compose.material.Card(
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                ambientColor = MaterialTheme.customColors.elevation,
                spotColor = MaterialTheme.customColors.elevation,
                shape = RoundedCornerShape(cornerRadius)
            ),
        shape = RoundedCornerShape(cornerRadius),
        elevation = 0.dp,
        backgroundColor = backgroundColor
    ) {
        content()
    }
}

@Preview
@Composable
private fun PreviewGetSoraCardScreen() {
    val empty = object : GetSoraCardScreenInterface {
        override fun onSignUp() {}
        override fun onLogIn() {}
        override fun onGetMoreXor() {}
        override fun onSeeBlacklist() {}
        override fun onNavigationClick() {}
    }
    FearlessAppTheme(darkTheme = true) {
        GetSoraCardScreenWithToolbar(
            state = GetSoraCardState(),
            scrollState = rememberScrollState(),
            callbacks = empty
        )
    }
}
