package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BannerJoinSubstrateEvm(
    onClick: () -> Unit,
    onCloseClick: () -> Unit
) = BannerCreateOrImport(
    titleResId = R.string.banner_addwallet_regular_title,
    descriptionResId = R.string.banner_addwallet_regular_subtitle,
    backgroundResId = R.drawable.background_banner_substrate,
    onClick = onClick,
    onCloseClick = onCloseClick
)

@Composable
fun BannerJoinTon(
    onClick: () -> Unit,
    onCloseClick: () -> Unit
) = BannerCreateOrImport(
    titleResId = R.string.banner_addwallet_ton_title,
    backgroundResId = R.drawable.background_banner_ton,
    buttonResId = R.string.banner_addwallet_ton_button_title,
    onClick = onClick,
    onCloseClick = onCloseClick
)

@Composable
fun BannerCreateOrImport(
    @StringRes titleResId:  Int,
    @StringRes descriptionResId:  Int? = null,
    @StringRes buttonResId:  Int = R.string.banner_addwallet_regular_button_title,
    @DrawableRes backgroundResId:  Int,
    onClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(139.dp)
            .clip(RoundedCornerShape(15.dp))
            .paint(
                painter = painterResource(backgroundResId),
                contentScale = ContentScale.Crop
            )
            .background(white04)
    ) {
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
                    .wrapContentWidth()
                    .padding(end = 24.dp),
                text = stringResource(titleResId),
                style = MaterialTheme.customTypography.headline2,
                color = Color.White
            )
            descriptionResId?.let {
                MarginVertical(margin = 8.dp)
                Text(
                    maxLines = 2,
                    modifier = Modifier
                        .wrapContentWidth(),
                    text = stringResource(descriptionResId),
                    style = MaterialTheme.customTypography.paragraphXS,
                    color = Color.White
                )
            }

            MarginVertical(margin = 11.dp)

            ColoredButton(
                modifier = Modifier.defaultMinSize(minWidth = 102.dp),
                backgroundColor = Color.Unspecified,
                border = BorderStroke(1.dp, white24),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                onClick = onClick
            ) {
                Text(
                    modifier = Modifier.defaultMinSize(minWidth = 86.dp),
                    text = stringResource(buttonResId),
                    style = MaterialTheme.customTypography.headline2.copy(fontSize = TextUnit(12f, TextUnitType.Sp)),
                    color = Color.White,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}


@Preview
@Composable
private fun BannerJoinEvmPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BannerJoinSubstrateEvm(
            onClick = {},
            onCloseClick = {}
        )
        BannerJoinTon(
            onClick = {},
            onCloseClick = {}
       )
    }
}
