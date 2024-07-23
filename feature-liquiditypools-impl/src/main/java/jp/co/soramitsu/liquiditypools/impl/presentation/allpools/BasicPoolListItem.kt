package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.icons.TokenIcon

data class BasicPoolListItemState(
    val ids: StringPair,
    val token1Icon: String,
    val token2Icon: String,
    val text1: String,
    val text2: String,
    val text3: String,
    val text4: String? = null,
)

@Composable
fun BasicPoolListItem(
    modifier: Modifier = Modifier,
    state: BasicPoolListItemState,
    onPoolClick: ((StringPair) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onPoolClick?.invoke(state.ids) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ConstraintLayout(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(start = 12.dp)
            ) {
                val (token1, token2) = createRefs()
                TokenIcon(
                    modifier = Modifier
                        .constrainAs(token1) {
                            top.linkTo(parent.top, 2.dp)
                            start.linkTo(parent.start)
                            bottom.linkTo(parent.bottom, 11.dp)
                        },
                    uri = state.token1Icon,
                    size = Size.ExtraSmall,
                )
                TokenIcon(
                    modifier = Modifier
                        .constrainAs(token2) {
                            top.linkTo(parent.top, 11.dp)
                            start.linkTo(token1.start, margin = 16.dp)
                            bottom.linkTo(parent.bottom, 2.dp)
                        },
                    uri = state.token2Icon,
                    size = Size.ExtraSmall,
                )
            }
        }
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f)
                .padding(start = 8.dp, end = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier.wrapContentHeight(),
                    color = white,
                    style = MaterialTheme.customTypography.header6,
                    text = state.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    modifier = Modifier
                        .wrapContentHeight()
                        .weight(1f),
                    color = colorAccentDark,
                    style = MaterialTheme.customTypography.header6,
                    text = "%s %s".format(state.text3, "APY"), //stringResource(id = R.string.polkaswap_apy)),
                    maxLines = 1,
                    textAlign = TextAlign.End
                )

            }
            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier.wrapContentHeight(),
                    color = white50,
                    style = MaterialTheme.customTypography.body2,
                    text = state.text4.orEmpty(),
                    maxLines = 1,
                )

                Text(
                    modifier = Modifier.wrapContentHeight(),
                    color = white50,
                    style = MaterialTheme.customTypography.body2,
                    text = state.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


@Preview
@Composable
private fun PreviewBasicPoolListItem() {
    Column {
        BasicPoolListItem(
            modifier = Modifier.background(transparent),
            state = BasicPoolListItemState(
                ids = "0" to "1",
                token1Icon = "DEFAULT_ICON_URI",
                token2Icon = "DEFAULT_ICON_URI",
                text1 = "XOR-VAL",
                text2 = "123.4M",
                text3 = "1234.3%",
                text4 = "Earn SWAP",
            )
        )
        BasicPoolListItem(
            modifier = Modifier.background(transparent),
            state = BasicPoolListItemState(
                ids = "0" to "1",
                token1Icon = "DEFAULT_ICON_URI",
                token2Icon = "DEFAULT_ICON_URI",
                text1 = "text1",
                text2 = "text2",
                text3 = "text3",
                text4 = "text4",
            )
        )
    }
}
