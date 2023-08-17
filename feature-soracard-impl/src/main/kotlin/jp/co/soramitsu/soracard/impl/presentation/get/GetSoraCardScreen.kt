package jp.co.soramitsu.soracard.impl.presentation.get

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import jp.co.soramitsu.common.compose.theme.errorRed
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_soracard_impl.R
import jp.co.soramitsu.soracard.api.presentation.models.SoraCardInfo
import jp.co.soramitsu.ui_core.component.text.HtmlText
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.ui_core.theme.elevation
import jp.co.soramitsu.oauth.R as SoraCardR

data class GetSoraCardState(
    val xorBalance: BigDecimal = BigDecimal.ZERO,
    val enoughXor: Boolean = false,
    val percent: BigDecimal = BigDecimal.ZERO,
    val needInXor: BigDecimal = BigDecimal.ZERO,
    val needInEur: BigDecimal = BigDecimal.ZERO,
    val xorRatioUnavailable: Boolean = false,
    val soraCardInfo: SoraCardInfo? = null
)

interface GetSoraCardScreenInterface {
    fun onEnableCard()
    fun onGetMoreXor()
    fun onSeeBlacklist(url: String)
    fun onAlreadyHaveCard()
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
        Card(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 12.dp,
            elevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color(0xFF1D1D1F))
                    .padding(16.dp)
            ) {
                Image(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    painter = painterResource(R.drawable.ic_sora_card),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth
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
                FreeCardIssuance(state)

                MarginVertical(margin = 16.dp)
                BlacklistedCountries(onSeeListClicked = callbacks::onSeeBlacklist)

                MarginVertical(margin = 16.dp)
                if (state.enoughXor) {
                    AccentButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(48.dp),
                        onClick = callbacks::onEnableCard,
                        text = stringResource(R.string.common_continue)
                    )
                } else {
                    AccentButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("GetMoreXor")
                            .padding(horizontal = 8.dp)
                            .height(48.dp),
                        onClick = callbacks::onGetMoreXor,
                        text = stringResource(SoraCardR.string.details_get_more_xor)
                    )
                }

                MarginVertical(margin = 8.dp)
                TransparentButton(
                    modifier = Modifier
                        .testTag("AlreadyHaveCard")
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(48.dp),
                    text = stringResource(SoraCardR.string.details_already_have_card),
                    onClick = callbacks::onAlreadyHaveCard
                )

                MarginVertical(margin = 8.dp)
            }
        }
    }
}

@Composable
private fun BlacklistedCountries(
    onSeeListClicked: (String) -> Unit
) {
    HtmlText(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        text = stringResource(R.string.sora_card_blacklisted_countires_warning),
        style = MaterialTheme.customTypography.paragraphXS.copy(
            textAlign = TextAlign.Center,
            color = Color.White,
            fontSize = 12.sp
        ),
        onUrlClick = onSeeListClicked
    )
}

@Composable
private fun AnnualFee() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 12.dp,
        backgroundColor = backgroundBlack,
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = Dimens.x2, horizontal = Dimens.x3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_check_small),
                contentDescription = null
            )
            MarginHorizontal(margin = 4.dp)
            Text(
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
}

@Composable
private fun FreeCardIssuance(
    state: GetSoraCardState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        cornerRadius = 12.dp,
        backgroundColor = backgroundBlack,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.x2, horizontal = Dimens.x3)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = Dimens.x2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = if (state.enoughXor) R.drawable.ic_check_rounded else R.drawable.ic_cross_24),
                    contentDescription = null,
                    tint = if (state.enoughXor) {
                        Color.Unspecified
                    } else {
                        errorRed
                    }
                )
                MarginHorizontal(margin = 4.dp)
                val cardIssuancePriceText = stringResource(SoraCardR.string.card_issuance_screen_free_card_title)
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
            Text(
                modifier = Modifier
                    .fillMaxSize(),
                text = stringResource(SoraCardR.string.card_issuance_screen_free_card_description, "100"),
                style = MaterialTheme.customTypography.paragraphM,
                color = Color.White
            )

            BalanceIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.x3, bottom = Dimens.x1),
                percent = state.percent.toFloat(),
                label = when {
                    state.xorRatioUnavailable -> {
                        stringResource(R.string.common_error_general_title)
                    }
                    state.enoughXor -> {
                        stringResource(SoraCardR.string.details_enough_xor_desription)
                    }
                    else -> {
                        stringResource(
                            SoraCardR.string.details_need_xor_desription,
                            state.needInXor.formatCryptoDetail(),
                            state.needInEur.formatFiat()
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    elevation: Dp = MaterialTheme.elevation.l,
    cornerRadius: Dp = MaterialTheme.borderRadius.xl,
    backgroundColor: Color = MaterialTheme.customColors.bgSurface,
    content: @Composable () -> Unit
) {
    androidx.compose.material.Card(
        modifier = modifier.shadow(
            elevation = elevation,
            ambientColor = MaterialTheme.customColors.elevation,
            spotColor = MaterialTheme.customColors.elevation,
            shape = RoundedCornerShape(cornerRadius)
        ),
        shape = RoundedCornerShape(cornerRadius),
        backgroundColor = backgroundColor
    ) {
        content()
    }
}

@Preview
@Composable
private fun PreviewGetSoraCardScreen() {
    val empty = object : GetSoraCardScreenInterface {
        override fun onEnableCard() {}
        override fun onGetMoreXor() {}
        override fun onSeeBlacklist(url: String) {}
        override fun onAlreadyHaveCard() {}
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
