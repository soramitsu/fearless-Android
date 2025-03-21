package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BannerBackup(
    onBackupClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(139.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(white04)
            .clickable(onClick = onBackupClick)
    ) {
        HaloIconBackup(
            modifier = Modifier
                .scale(2.1f)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = -1.dp),
            iconRes = R.drawable.ic_fearless_bird,
            color = colorAccentDark,
            background = backgroundBlack
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                modifier = Modifier
                    .wrapContentWidth(),
                text = stringResource(R.string.common_backup_your_wallet),
                style = MaterialTheme.customTypography.headline2,
                color = Color.White
            )
            MarginVertical(margin = 8.dp)
            Text(
                maxLines = 1,
                modifier = Modifier
                    .wrapContentWidth()
                    .basicMarquee(),
                text = stringResource(R.string.banners_backup_description),
                style = MaterialTheme.customTypography.paragraphXS,
                color = Color.White
            )

            MarginVertical(margin = 11.dp)

            ColoredButton(
                modifier = Modifier.defaultMinSize(minWidth = 102.dp),
                backgroundColor = Color.Unspecified,
                border = BorderStroke(1.dp, white24),
                onClick = onBackupClick
            ) {
                Text(
                    modifier = Modifier.defaultMinSize(minWidth = 86.dp),
                    text = stringResource(R.string.backup_now),
                    style = MaterialTheme.customTypography.headline2.copy(fontSize = TextUnit(12f, TextUnitType.Sp)),
                    color = Color.White,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
fun HaloIconBackup(
    @DrawableRes iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val gradientBrush = Brush.radialGradient(
        colors = listOf(color, transparent)
    )

    val imageSize = 40.dp
    val haloPadding = 12.dp
    val haloWidth = 15.dp
    val haloSize = imageSize + haloPadding * 2 + haloWidth * 2
    Box(
        modifier
            .size(haloSize)
            .border(haloWidth, gradientBrush, CircleShape)
            .padding(haloWidth)
            .background(background, CircleShape)
            .padding(contentPadding)
    ) {
        Image(
            modifier = Modifier
                .size(imageSize)
                .align(Alignment.Center),
            res = iconRes,
            tint = color
        )
    }
}

@Preview(locale = "ru", widthDp = 300)
@Composable
private fun BannerBackupPreview() {
    BannerBackup(
        onBackupClick = {},
        onCloseClick = {}
    )
}
