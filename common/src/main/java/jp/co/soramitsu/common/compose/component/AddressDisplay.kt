package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.withNoFontPadding


data class AddressDisplayState(
    val title: String,
    val input: String,
    val image: Any,
    @DrawableRes val endIcon: Int? = null
)

@Composable
fun AddressDisplay(state: AddressDisplayState, endIconClick: (() -> Unit)? = null) {
    BackgroundCorneredWithBorder(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        borderColor = white24
    ) {
        val contentAlpha = 0.4f
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            AsyncImage(
                model = state.image,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.CenterVertically)
                    .alpha(contentAlpha)
            )
            MarginHorizontal(margin = 8.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
                    .alpha(contentAlpha)
            ) {
                H5(text = state.title.withNoFontPadding(), color = black2)
                B1(text = state.input, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MarginHorizontal(8.dp)

            if (state.endIcon != null) {
                val clickModifier = endIconClick?.let { Modifier.clickable(onClick = endIconClick) } ?: Modifier
                Image(
                    res = state.endIcon,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .wrapContentSize()
                        .then(clickModifier)
                )
            }
        }
    }
}

@Composable
@Preview
fun AddressDisplayPreview(){
    FearlessAppTheme {
        val state = AddressDisplayState("From", "0xksjdnbfuwyt4g5fuy24guyf247ryfg2374yrgf73yrgf", image = R.drawable.ic_wallet, endIcon = R.drawable.ic_more_vertical)
        AddressDisplay(state)
    }
}