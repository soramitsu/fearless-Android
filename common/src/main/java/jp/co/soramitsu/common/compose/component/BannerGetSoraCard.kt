package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.common.compose.theme.white24

@Composable
fun BannerGetSoraCard(
    onViewDetails: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(139.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(white04)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_get_sora_card_banner),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd),
            tint = Color.Unspecified,
        )
        Image(
            res = R.drawable.ic_close_16_white_circle,
            modifier = Modifier
                .padding(8.dp)
                .clickable(onClick = onClose)
                .align(Alignment.TopEnd)
                .size(32.dp),
        )
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                modifier = Modifier
                    .wrapContentWidth(),
                text = stringResource(jp.co.soramitsu.oauth.R.string.status_not_started),
                style = MaterialTheme.customTypography.header3,
                color = Color.White
            )
            MarginVertical(margin = 8.dp)
            Text(
                maxLines = 2,
                modifier = Modifier
                    .wrapContentWidth(),
                text = stringResource(jp.co.soramitsu.oauth.R.string.get_iban_bank_account),
                style = MaterialTheme.customTypography.body1,
                color = Color.White
            )

            MarginVertical(margin = 11.dp)
            ColoredButton(
                modifier = Modifier.defaultMinSize(minWidth = 102.dp),
                backgroundColor = Color.Unspecified,
                border = BorderStroke(1.dp, white24),
                onClick = onViewDetails,
                enabled = true,
            ) {
                Text(
                    modifier = Modifier.defaultMinSize(minWidth = 86.dp),
                    text = stringResource(jp.co.soramitsu.oauth.R.string.view_details),
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
}

@Preview
@Composable
private fun BannerPreview() {
    BannerGetSoraCard(
        onViewDetails = {},
        onClose = {},
    )
}
