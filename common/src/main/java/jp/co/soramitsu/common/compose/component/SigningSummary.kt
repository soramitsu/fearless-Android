package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.bold
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white

/** One decoded fact about a request to sign, such as its recipient or amount. */
data class SigningDetailViewState(
    val title: String,
    val value: String,
    val isWarning: Boolean = false
)

/** A group of details, such as one message of a TON transaction. */
data class SigningSummarySection(
    val title: String?,
    val rows: List<SigningDetailViewState>
)

/** What a request asks the user to sign, decoded for the approval screens. */
data class SigningSummaryViewState(
    /** One line for the request screen, such as "Token approval · Unlimited USDC". */
    val headline: String,
    val sections: List<SigningSummarySection>,
    val warnings: List<String>,
    /** The request could not be decoded in full, so its raw data is offered as a fallback. */
    val showsRawData: Boolean
)

/**
 * The decoded request. Values wrap instead of being cut, so an address is always shown in full:
 * look-alike addresses differ in the characters an ellipsis would hide.
 */
@Composable
fun SigningSummary(state: SigningSummaryViewState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.warnings.forEach { warning ->
            WarningInfo(state = WarningInfoState(message = warning, extras = emptyList()))
        }
        state.sections.forEach { section ->
            section.title?.let { title ->
                H5(
                    text = title,
                    color = black2,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            BackgroundCorneredWithBorder(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    section.rows.forEach { SigningDetail(it) }
                }
            }
        }
    }
}

@Composable
private fun SigningDetail(state: SigningDetailViewState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("signing_detail_${state.title}")
    ) {
        H5(text = state.title, color = black2)
        Text(
            text = state.value,
            color = if (state.isWarning) warningOrange else white,
            style = MaterialTheme.customTypography.header5.bold()
        )
    }
}

@Preview
@Composable
private fun SigningSummaryPreview() {
    FearlessTheme {
        SigningSummary(
            SigningSummaryViewState(
                headline = "Token approval · Unlimited USDC",
                sections = listOf(
                    SigningSummarySection(
                        title = null,
                        rows = listOf(
                            SigningDetailViewState("Action", "Token approval"),
                            SigningDetailViewState("Token", "USDC · 0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"),
                            SigningDetailViewState("Spender", "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45"),
                            SigningDetailViewState("Amount", "Unlimited", isWarning = true)
                        )
                    )
                ),
                warnings = listOf("The spender can take all of this token from your wallet until you revoke the approval."),
                showsRawData = false
            )
        )
    }
}
