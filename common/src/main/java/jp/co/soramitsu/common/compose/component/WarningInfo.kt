package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.fw.FwExpandChevron
import jp.co.soramitsu.common.compose.theme.fw.FwNotice
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.common.utils.withNoFontPadding

data class WarningInfoState(
    val message: String,
    val extras: List<Pair<String, String>>,
    val isExpanded: Boolean = false,
    val color: Color = warningOrange
)

@Composable
fun WarningInfo(
    state: WarningInfoState,
    onClick: () -> Unit = {}
) {
    if (FwRestyle.isEnabled(FwSurface.Signing)) {
        SunlitWarningInfo(state, onClick)
        return
    }
    BackgroundCorneredWithBorder(
        borderColor = state.color
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .clickableWithNoIndication(onClick)
                .testTag("warning_layout")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_alert_24),
                    tint = state.color,
                    contentDescription = null,
                    modifier = Modifier
                        .testTag("warning_icon")
                        .align(Alignment.CenterVertically)
                )
                MarginHorizontal(margin = 14.dp)
                H5(
                    text = stringResource(id = R.string.common_warning).withNoFontPadding(),
                    color = state.color
                )
                if (state.extras.isNotEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_dawn),
                        tint = Color.White,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .rotate(if (state.isExpanded) 180f else 0f)
                    )
                }
            }
            MarginVertical(margin = 12.dp)
            B1(
                text = state.message,
                modifier = Modifier.testTag("warning_message")
            )
            MarginVertical(margin = 12.dp)

            if (state.extras.isNotEmpty() && state.isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.extras.forEach {
                        Row(
                            modifier = Modifier.testTag("warning_extra_${it.first}")
                        ) {
                            H6(text = "${it.first}:", color = black2)
                            MarginHorizontal(margin = 4.dp)
                            B2(text = it.second)
                        }
                    }
                }
            }
        }
    }
}

/** The same still notice as every other warning; a warning with details opens them under its chevron. */
@Composable
private fun SunlitWarningInfo(state: WarningInfoState, onClick: () -> Unit) {
    val c = LocalFwColors.current
    FwNotice(
        message = state.message,
        title = stringResource(id = R.string.common_warning),
        modifier = Modifier.clickableWithNoIndication(onClick).testTag("warning_layout"),
        action = if (state.extras.isEmpty()) {
            null
        } else {
            {
                FwExpandChevron(expanded = state.isExpanded, modifier = Modifier.align(Alignment.End))
                if (state.isExpanded) {
                    state.extras.forEach {
                        Row(Modifier.padding(top = 6.dp).testTag("warning_extra_${it.first}")) {
                            H6(text = "${it.first}:", color = c.textSecondary)
                            MarginHorizontal(margin = 4.dp)
                            B2(text = it.second)
                        }
                    }
                }
            }
        }
    )
}

@Composable
@Preview
private fun WarningInfoPreview() {
    val state = WarningInfoState(
        message = "This address has been flagged due to evidence of a scam. We strongly recommend that you don\'t send DOT to this account.",
        extras = listOf(
            "Name" to "Scam address name",
            "Reason" to "Scam",
            "Additional" to "Phishing"
        ),
        isExpanded = true
    )
    WarningInfo(state = state)
}
