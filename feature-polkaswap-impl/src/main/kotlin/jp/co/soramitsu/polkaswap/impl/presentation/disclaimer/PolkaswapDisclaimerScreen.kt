package jp.co.soramitsu.polkaswap.impl.presentation.disclaimer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.feature_polkaswap_impl.R

data class PolkaswapDisclaimerViewState(
    val polkaswapMaintained: TextWithHighlights,
    val userResponsibilityTitle: String,
    val userResponsibilities: List<String>,
    val disclaimerReminder: TextWithHighlights,
    val hasReadChecked: Boolean
)

interface DisclaimerScreenInterface {
    fun onLinkClick(url: String)
    fun onContinueClick()
    fun onHasReadChecked()
}

@Composable
fun PolkaswapDisclaimerScreen(state: PolkaswapDisclaimerViewState, callbacks: DisclaimerScreenInterface) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ToolbarBottomSheet(
                title = stringResource(
                    id = R.string.common_disclaimer
                ),
                onNavigationClick = {}
            )
            MarginVertical(margin = 8.dp)
            TextWithLinks(state.polkaswapMaintained, onLinkClick = callbacks::onLinkClick)

            MarginHorizontal(margin = 12.dp)
            B1(text = state.userResponsibilityTitle, color = white50, modifier = Modifier.align(Alignment.Start))
            MarginVertical(margin = 8.dp)
            state.userResponsibilities.forEachIndexed { index, text ->
                Row {
                    B1(text = "${index + 1}. ", color = white50)
                    B1(text = text, color = white50)
                }
                MarginVertical(margin = 8.dp)
            }

            MarginVertical(margin = 12.dp)
            Row {
                Box(
                    modifier = Modifier
                        .height(72.dp)
                        .width(1.dp)
                        .background(colorAccentDark)
                )
                MarginHorizontal(margin = 16.dp)
                TextWithLinks(state.disclaimerReminder, onLinkClick = callbacks::onLinkClick)
            }

            Spacer(modifier = Modifier.weight(0.5f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val switchText = buildAnnotatedString {
                    withStyle(SpanStyle(color = warningOrange)) {
                        append(stringResource(id = R.string.common_warning).uppercase() + ": ")
                    }
                    append(stringResource(id = R.string.polkaswap_info_switch_text))
                }
                H5(text = switchText, overflow = TextOverflow.Ellipsis, color = black2, modifier = Modifier.weight(1f))
                MarginHorizontal(margin = 8.dp)
                val trackColor = if (state.hasReadChecked) colorAccent else black3
                Switch(
                    colors = switchColors,
                    checked = state.hasReadChecked,
                    onCheckedChange = { callbacks.onHasReadChecked() },
                    modifier = Modifier
                        .background(color = trackColor, shape = RoundedCornerShape(20.dp))
                        .padding(3.dp)
                        .height(20.dp)
                        .width(35.dp)
                        .align(Alignment.CenterVertically)
                )
            }

            MarginVertical(16.dp)
            AccentButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                text = stringResource(id = R.string.common_continue),
                enabled = state.hasReadChecked,
                onClick = callbacks::onContinueClick
            )
            MarginVertical(margin = 32.dp)
        }
    }
}

val switchColors = object : SwitchColors {
    @Composable
    override fun thumbColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(white)
    }

    @Composable
    override fun trackColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(transparent)
    }
}

data class TextWithHighlights(
    val text: String,
    val delimiter: String,
    val schema: List<HighlightedTextParameters>
) {
    data class HighlightedTextParameters(val color: Color, val link: String)
}

@Composable
fun TextWithLinks(state: TextWithHighlights, onLinkClick: (String) -> Unit) {
    val splittedText = state.text.split("%%")
    val annotatedString = AnnotatedString.Builder().apply {
        var isHighlight = false
        var configIndex = 0
        splittedText.forEach { part ->
            isHighlight = if (isHighlight) {
                val config = state.schema[configIndex]
                withStyle(SpanStyle(color = config.color)) {
                    pushStringAnnotation(configIndex.toString(), config.link)
                    append(part)
                    configIndex++
                }
                false
            } else {
                withStyle(SpanStyle(color = white50)) {
                    append(part)
                }
                true
            }
        }
    }.toAnnotatedString()

    ClickableText(text = annotatedString, style = MaterialTheme.customTypography.body1, onClick = {
        val tag = annotatedString.getStringAnnotations(it, it).firstOrNull()?.tag?.toIntOrNull()
        if (tag != null) {
            state.schema.getOrNull(tag)?.let { config -> onLinkClick(config.link) }
        }
    })
}

@Composable
@Preview
private fun PolkaswapDisclaimerScreenPreview() {
    val polkaswapMaintained = TextWithHighlights(
        stringResource(
            id = R.string.polkaswap_info_text_1
        ),
        "%%",
        listOf(
            TextWithHighlights.HighlightedTextParameters(colorAccent, "polkaswap FAQ"),
            TextWithHighlights.HighlightedTextParameters(colorAccent, "memorandum"),
            TextWithHighlights.HighlightedTextParameters(colorAccent, "privacy policy")
        )
    )
    val disclaimerReminder = TextWithHighlights(
        stringResource(
            id = R.string.polkaswap_info_text_6
        ),
        "%%",
        listOf(
            TextWithHighlights.HighlightedTextParameters(colorAccent, "polkaswap FAQ"),
            TextWithHighlights.HighlightedTextParameters(colorAccent, "memorandum"),
            TextWithHighlights.HighlightedTextParameters(colorAccent, "privacy policy")
        )
    )
    val userResponsibilityTitle = stringResource(id = R.string.polkaswap_info_text_2)
    val userResponsibilityItems = listOf(
        stringResource(id = R.string.polkaswap_info_text_3),
        stringResource(id = R.string.polkaswap_info_text_4),
        stringResource(id = R.string.polkaswap_info_text_5)
    )
    val viewState = PolkaswapDisclaimerViewState(
        polkaswapMaintained = polkaswapMaintained,
        userResponsibilityTitle,
        userResponsibilityItems,
        disclaimerReminder,
        false
    )
    FearlessTheme {
        PolkaswapDisclaimerScreen(
            viewState,
            object : DisclaimerScreenInterface {
                override fun onLinkClick(url: String) = Unit
                override fun onContinueClick() = Unit
                override fun onHasReadChecked() = Unit
            }
        )
    }
}
