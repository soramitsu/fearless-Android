package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.utils.withNoFontPadding

@Composable
fun AttentionMessage(
    attentionText: String = stringResource(id = R.string.common_warning),
    message: String,
    imageResId: Int = R.drawable.ic_alert_16
) {
    val styledText = buildAnnotatedString {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = warningOrange)) {
            append("${attentionText.uppercase()}:")
        }
        withStyle(style = SpanStyle()) {
            append(" ")
            append(message)
        }
    }.withNoFontPadding()

    Row {
        Image(res = imageResId)
        MarginHorizontal(margin = 8.dp)
        Text(
            style = MaterialTheme.customTypography.body2,
            text = styledText,
            color = white50
        )
    }
}