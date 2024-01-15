package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.clickableSingle
import jp.co.soramitsu.common.utils.withNoFontPadding

data class SelectorState(
    val title: String,
    val subTitle: String?,
    val iconUrl: String?,
    val actionIcon: Int? = R.drawable.ic_arrow_down,
    val clickable: Boolean = true,
    val enabled: Boolean = true,
    val subTitleIcon: Int? = null,
    @DrawableRes val iconOverrideResId: Int? = null
) {
    companion object {
        val default = SelectorState("Network", null, null)
    }
}

@Composable
fun SelectorWithBorder(
    state: SelectorState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = black05,
    onClick: () -> Unit = {}
) {
    val shape = FearlessCorneredShape()
    BackgroundCorneredWithBorder(
        backgroundColor = backgroundColor,
        borderColor = white24,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickableSingle(enabled = state.clickable, onClick = onClick)
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (state.iconOverrideResId != null) {
                Image(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterVertically),
                    res = state.iconOverrideResId,
                    contentDescription = state.title
                )
                MarginHorizontal(8.dp)
            } else if (state.iconUrl != null) {
                AsyncImage(
                    model = getImageRequest(LocalContext.current, state.iconUrl),
                    contentDescription = state.title,
                    alpha = if (state.enabled) 1f else 0.5f,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterVertically)
                )
                MarginHorizontal(8.dp)
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
            ) {
                H5(
                    text = state.title.withNoFontPadding(),
                    color = if (state.enabled) black2 else black3
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.subTitleIcon?.let {
                        Image(
                            modifier = Modifier.padding(top = 2.dp),
                            res = state.subTitleIcon
                        )
                        MarginHorizontal(margin = 4.dp)
                    }

                    state.subTitle?.let {
                        B1EllipsizeMiddle(
                            text = state.subTitle,
                            color = if (state.enabled) Color.White else black2
                        )
                    }
                }
            }

            MarginHorizontal(margin = 4.dp)
            state.actionIcon?.let {
                Image(res = state.actionIcon, modifier = Modifier.align(Alignment.CenterVertically))
            }
            MarginHorizontal(margin = 4.dp)
        }
    }
}

@Composable
@Preview
private fun SelectorWithBorderPreview() {
    val state = SelectorState(
        title = "Kusama",
        iconUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        subTitle = "300 KSM"
    )
    FearlessTheme {
        Column(Modifier.widthIn(max = 200.dp)) {
            SelectorWithBorder(
                state = state
            )
            SelectorWithBorder(
                state = state.copy(iconOverrideResId = R.drawable.ic_wallet)
            )
            SelectorWithBorder(
                state = state.copy(iconUrl = null, subTitleIcon = R.drawable.ic_alert_16)
            )
            SelectorWithBorder(
                state = state.copy(subTitle = null)
            )
            SelectorWithBorder(
                state = state.copy(actionIcon = null)
            )
            SelectorWithBorder(
                state = state.copy(actionIcon = R.drawable.ic_dots_horizontal_24)
            )
            SelectorWithBorder(
                state = state.copy(subTitle = "Kusama long string", actionIcon = R.drawable.ic_dots_horizontal_24)
            )
        }
    }
}
