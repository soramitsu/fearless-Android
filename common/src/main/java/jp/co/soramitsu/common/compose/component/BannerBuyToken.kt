package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.colorFromHex
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.clickableWithNoIndication

@Composable
fun BannerBuyToken(
    symbol: String,
    color: String?,
    imageUrl: String,
    enabled: Boolean = true,
    onBuyTokenClick: (String) -> Unit,
    onBuyTokenCloseClick: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(139.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(white04)
            .then(if (enabled) Modifier.clickableWithNoIndication(onClick = { onBuyTokenClick(symbol) }) else Modifier)
    ) {
        HaloIconToken(
            modifier = Modifier
                .scale(2.1f)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-1).dp),
            iconUrl = imageUrl,
            color = color?.colorFromHex() ?: colorAccentDark,
        )

        Image(
            res = R.drawable.ic_close_16_white_circle,
            modifier = Modifier
                .padding(8.dp)
                .clickable(onClick = { onBuyTokenCloseClick(symbol) })
                .align(Alignment.TopEnd)
                .size(32.dp),
        )
        Text(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .wrapContentWidth(),
            text = stringResource(R.string.banners_buy_or_sell_token_format, symbol.uppercase()),
            style = MaterialTheme.customTypography.header3,
            color = Color.White
        )

        ColoredButton(
            modifier = Modifier
                .defaultMinSize(minWidth = 102.dp)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .align(Alignment.BottomStart),
            backgroundColor = color?.colorFromHex() ?: Color.Unspecified,
            border = BorderStroke(1.dp, white24),
            onClick = { onBuyTokenClick(symbol) },
            enabled = enabled,
        ) {
            Text(
                modifier = Modifier.defaultMinSize(minWidth = 86.dp),
                text = stringResource(R.string.banners_buy_token_format, symbol.uppercase()),
                style = MaterialTheme.customTypography.header3.copy(
                    fontSize = TextUnit(
                        12f,
                        TextUnitType.Sp,
                    )
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun HaloIconToken(
    iconUrl: String?,
    color: Color,
    modifier: Modifier = Modifier,
    background: Color = transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val gradientBrush = Brush.radialGradient(
        colors = listOf(color, transparent)
    )

    val imageSize = 72.dp
    val haloPadding = 0.dp
    val haloWidth = 15.dp
    val haloSize = imageSize + haloPadding * 2 + haloWidth
    Box(
        modifier
            .size(haloSize)
            .border(haloWidth, gradientBrush, CircleShape)
            .padding(contentPadding)
    ) {
        if (iconUrl.isNullOrBlank()) {
            Image(
                res = R.drawable.ic_sora_logo,
                modifier = Modifier
                    .size(imageSize)
                    .align(Alignment.Center),
                tint = color
            )
        } else {
            AsyncImage(
                model = getImageRequest(LocalContext.current, iconUrl),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(imageSize),
            )
        }
    }
}

@Preview
@Composable
private fun BannerBuyTokenPreview() {
    BannerBuyToken(
        symbol = "ETH",
        color = "#627EEA",
        enabled = true,
        imageUrl = "",
        onBuyTokenClick = {},
        onBuyTokenCloseClick = {},
    )
}
