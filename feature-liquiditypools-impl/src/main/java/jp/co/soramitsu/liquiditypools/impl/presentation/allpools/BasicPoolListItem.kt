package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.SubcomposeAsyncImage
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.models.retrieveString
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.ui_core.component.button.properties.Size

data class BasicPoolListItemState(
    val ids: StringPair,
    val token1Icon: String,
    val token2Icon: String,
    val text1: String,
    val text2: String,
    val text2Color: Color = white50,
    val apy: LoadingState<String>?,
    val text4: TextModel? = null,
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
                SubcomposeAsyncImage(
                    modifier = Modifier
                        .constrainAs(token1) {
                            top.linkTo(parent.top, 2.dp)
                            start.linkTo(parent.start)
                            bottom.linkTo(parent.bottom, 11.dp)
                        }
                        .size(size = 32.dp),
                    model = getImageRequest(LocalContext.current, state.token1Icon),
                    contentDescription = null,
                    loading = { Shimmer(Modifier.size(Size.ExtraSmall)) }
                )
                SubcomposeAsyncImage(
                    modifier = Modifier
                        .constrainAs(token2) {
                            top.linkTo(parent.top, 11.dp)
                            start.linkTo(token1.start, margin = 16.dp)
                            bottom.linkTo(parent.bottom, 2.dp)
                        }
                        .size(size = 32.dp),
                    model = getImageRequest(LocalContext.current, state.token2Icon),
                    contentDescription = null,
                    loading = { Shimmer(Modifier.size(Size.ExtraSmall)) },
                    error = {
                        it.result.throwable.message?.let { it1 -> Log.d("&&&", it1) }
                        Icon(painterResource(id = R.drawable.ic_token_default), null, modifier = Modifier.size(size = 32.dp))
                    }
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
                modifier = Modifier.wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight(),
                    color = white,
                    style = MaterialTheme.customTypography.header6,
                    text = state.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when (state.apy) {
                    is LoadingState.Loaded -> Text(
                        modifier = Modifier
                            .wrapContentHeight(),
                        color = colorAccentDark,
                        style = MaterialTheme.customTypography.header6,
                        text = "%s %s".format(
                            state.apy.data,
                            stringResource(id = R.string.staking_only_apy)
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )

                    is LoadingState.Loading -> Shimmer(
                        Modifier
                            .height(12.dp)
                            .width(100.dp)
                    )
                    null -> Unit
                }
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
                    text = state.text4?.retrieveString().orEmpty(),
                    maxLines = 1,
                )

                Text(
                    modifier = Modifier
                        .wrapContentHeight()
                        .padding(start = 6.dp),
                    color = state.text2Color,
                    style = MaterialTheme.customTypography.body2,
                    text = state.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun BasicPoolShimmerItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ConstraintLayout(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(start = 12.dp)
            ) {
                val (token1, token2) = createRefs()
                Shimmer(
                    Modifier
                        .size(Size.ExtraSmall)
                        .constrainAs(token1) {
                            top.linkTo(parent.top, 2.dp)
                            start.linkTo(parent.start)
                            bottom.linkTo(parent.bottom, 11.dp)
                        }
                )

                Shimmer(
                    Modifier
                        .size(Size.ExtraSmall)
                        .constrainAs(token2) {
                            top.linkTo(parent.top, 11.dp)
                            start.linkTo(token1.start, margin = 16.dp)
                            bottom.linkTo(parent.bottom, 2.dp)
                        }
                )
            }
        }
        Column(
            modifier = Modifier
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
                Shimmer(
                    Modifier
                        .height(12.dp)
                        .width(70.dp)
                )
                Shimmer(
                    Modifier
                        .height(12.dp)
                        .width(100.dp)
                )
            }
            MarginVertical(margin = 6.dp)
            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Shimmer(
                    Modifier
                        .height(12.dp)
                        .width(70.dp)
                )
                Shimmer(
                    Modifier
                        .height(12.dp)
                        .width(90.dp)
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
                apy = LoadingState.Loaded("1234.3%"),
                text4 = TextModel.SimpleString("Earn SWAP"),
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
                apy = LoadingState.Loaded("text3"),
                text4 = TextModel.SimpleString("text4"),
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
                apy = LoadingState.Loading(),
                text4 = TextModel.SimpleString("text4"),
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
                apy = null,
                text4 = TextModel.SimpleString("text4"),
            )
        )
        BasicPoolShimmerItem()
    }
}
