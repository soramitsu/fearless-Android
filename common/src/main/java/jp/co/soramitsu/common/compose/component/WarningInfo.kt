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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.black2
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
    BackgroundCorneredWithBorder(
        borderColor = state.color
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .clickableWithNoIndication {
                    onClick()
                }
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
                    text = "Warning".withNoFontPadding(),
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.extras) {
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
