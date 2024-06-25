package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.colorFromHex
import jp.co.soramitsu.common.compose.theme.demeterYellow
import jp.co.soramitsu.common.utils.withNoFontPadding
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BannerDemeter(
    onShowMoreClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(139.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                brush = Brush.linearGradient(
                    colorStops = listOfNotNull(
                        "#46a487".colorFromHex()?.let { 0.05f to it },
                        "#46a487".colorFromHex()?.copy(alpha = 0.5F)?.let { 0.65f to it }
                    ).toTypedArray(),
                    start = Offset(0f, Float.POSITIVE_INFINITY),
                    end = Offset(Float.POSITIVE_INFINITY, 0f)
                )
            )
    ) {
        Image(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 8.dp, bottom = 16.dp),
            painter = painterResource(id = R.drawable.demeter_banner_image),
            contentDescription = "",
            contentScale = ContentScale.FillHeight
        )
        NavigationIconButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            navigationIconResId = R.drawable.ic_cross_32,
            onNavigationClick = onCloseClick
        )

        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            val title = stringResource(R.string.banners_demeter_title)
            val titleWords = title.split(" ")
            val titleFirstWord = titleWords.firstOrNull()
            val titleRemainWords = titleFirstWord?.let { title.removePrefix(it) }
            val styledText = buildAnnotatedString {
                withStyle(style = SpanStyle()) {
                    append(titleFirstWord)
                }
                withStyle(style = SpanStyle(color = demeterYellow)) {
                    append(titleRemainWords)
                }
            }.withNoFontPadding()

            Text(
                modifier = Modifier
                    .wrapContentWidth(),
                text = styledText,
                style = MaterialTheme.customTypography.headline2,
                color = Color.White
            )
            MarginVertical(margin = 8.dp)
            Text(
                maxLines = 2,
                modifier = Modifier
                    .wrapContentWidth(),
                text = stringResource(R.string.banners_demeter_description),
                style = MaterialTheme.customTypography.paragraphXS.copy(fontSize = 12.sp),
                color = Color.White
            )

            MarginVertical(margin = 11.dp)

            ColoredButton(
                modifier = Modifier.defaultMinSize(minWidth = 102.dp),
                backgroundColor = demeterYellow,
                onClick = onShowMoreClick
            ) {
                Text(
                    text = stringResource(R.string.common_show_more),
                    style = MaterialTheme.customTypography.headline2.copy(fontSize = 12.sp),
                    color = Color.White,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Preview
@Composable
private fun BannerDemeterPreview() {
    BannerDemeter(
        onShowMoreClick = {},
        onCloseClick = {}
    )
}
