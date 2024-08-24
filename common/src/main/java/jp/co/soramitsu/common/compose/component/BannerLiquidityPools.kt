package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.black
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.colorFromHex
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white40
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BannerLiquidityPools(
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
                        "#FF3B7B".colorFromHex()?.let { 0.05f to it },
                        "#AB18B8".colorFromHex()?.copy(alpha = 0.4F)?.let { 0.65f to it }
                    ).toTypedArray(),
                    start = Offset(0f, Float.POSITIVE_INFINITY),
                    end = Offset(Float.POSITIVE_INFINITY, 0f)
                )
            )
    ) {
        HaloIconBannerPools(
            modifier = Modifier
                .scale(2.1f)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-1).dp),
            iconRes = R.drawable.ic_polkaswap_logo,
            color = colorAccentDark,
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
            Text(
                modifier = Modifier
                    .wrapContentWidth(),
                text = stringResource(R.string.banners_liquidity_pools_title),
                style = MaterialTheme.customTypography.headline2,
                color = Color.White
            )
            MarginVertical(margin = 8.dp)
            Text(
                maxLines = 2,
                modifier = Modifier
                    .wrapContentWidth(),
                text = stringResource(R.string.lp_banner_text),
                style = MaterialTheme.customTypography.paragraphXS.copy(fontSize = 12.sp),
                color = Color.White
            )

            MarginVertical(margin = 11.dp)

            ColoredButton(
                modifier = Modifier.defaultMinSize(minWidth = 102.dp),
                backgroundColor = Color.Unspecified,
                border = BorderStroke(1.dp, white40),
                onClick = onShowMoreClick
            ) {
                Text(
                    text = stringResource(R.string.common_show_details),
                    style = MaterialTheme.customTypography.headline2.copy(fontSize = 12.sp),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun HaloIconBannerPools(
    @DrawableRes iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val gradientBrush = Brush.radialGradient(
        colors = listOf(black, transparent)
    )

    val haloPadding = 16.dp
    val haloWidth = 14.dp
    val imageSize = 35.dp
    val haloSize = imageSize + haloPadding * 2 + haloWidth * 2
    Box(
        modifier
            .size(haloSize)
            .border(haloWidth, gradientBrush, CircleShape)
            .padding(haloWidth)
            .background(colorAccentDark.copy(alpha = 0.06f), CircleShape)
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

@Preview
@Composable
private fun BannerLiquidityPoolsPreview() {
    BannerLiquidityPools(
        onShowMoreClick = {},
        onCloseClick = {}
    )
}
