package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.alertYellow

@OptIn(ExperimentalTextApi::class)
@Composable
fun NetworkIssuesBadge(onClick: () -> Unit) {
    val textWithWeight600 = buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append(stringResource(id = R.string.network_issue_stub))
        }
    }

    @Suppress("DEPRECATION")
    val textWoFontPadding = buildAnnotatedString {
        withStyle(
            style = ParagraphStyle(
                platformStyle = PlatformParagraphStyle(false)
            )
        ) {
            append(text = textWithWeight600)
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(100))
                .background(color = Color.White.copy(alpha = 0.08f))
                .clickable { onClick() }
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            B2(text = textWoFontPadding)
            MarginHorizontal(margin = 4.dp)
            Icon(
                painter = painterResource(id = R.drawable.ic_alert_16),
                tint = alertYellow,
                contentDescription = null
            )
        }
    }
}


@Composable
@Preview
private fun NetworkIssuesBadgePreview() {
    Box(Modifier.background(Color.Black)) {
        NetworkIssuesBadge {}
    }
}
