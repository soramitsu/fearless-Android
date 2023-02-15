package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography

@Composable
fun SwapHeader(
    fromTokenImage: GradientIconState,
    toTokenImage: GradientIconState,
    fromTokenAmount: String,
    toTokenAmount: String
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GradientIcon(
                icon = fromTokenImage,
                color = colorAccentDark,
                background = backgroundBlack,
                modifier = Modifier
                    .offset(x = 25.dp)
                    .zIndex(1f),
                contentPadding = PaddingValues(10.dp)
            )
            GradientIcon(
                icon = toTokenImage,
                color = colorAccentDark,
                background = backgroundBlack,
                modifier = Modifier
                    .offset(x = (-25).dp)
                    .zIndex(0f),
                contentPadding = PaddingValues(10.dp)
            )
        }

        Text(
            modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
            text = stringResource(R.string.common_action_swap),
            style = MaterialTheme.customTypography.header2,
            color = MaterialTheme.customColors.white50
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = fromTokenAmount,
                style = MaterialTheme.customTypography.header3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )

            MarginHorizontal(margin = 16.dp)

            Icon(
                painter = painterResource(R.drawable.ic_arrow_right_24),
                contentDescription = null,
                tint = MaterialTheme.customColors.white
            )

            MarginHorizontal(margin = 16.dp)

            Text(
                modifier = Modifier.weight(1f),
                text = toTokenAmount,
                style = MaterialTheme.customTypography.header3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
@Preview
private fun SwapHeaderPreview() {
    FearlessTheme {
        Column(Modifier.fillMaxWidth()) {
            SwapHeader(
                fromTokenImage = GradientIconState.Remote("", "ffffff"),
                toTokenImage = GradientIconState.Remote("", "ffffff"),
                fromTokenAmount = "15 XTSUD",
                toTokenAmount = "665 BTC"
            )
        }
    }
}
