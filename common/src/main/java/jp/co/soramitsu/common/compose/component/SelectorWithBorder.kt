package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.withNoFontPadding

data class SelectorState(
    val title: String,
    val subTitle: String?,
    val iconUrl: String?,
    val actionIcon: Int? = R.drawable.ic_arrow_down
) {
    companion object {
        val default = SelectorState("Network", null, null)
    }
}

@Composable
fun SelectorWithBorder(
    state: SelectorState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    BackgroundCorneredWithBorder(
        backgroundColor = black05,
        borderColor = white24,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            state.iconUrl?.let {
                AsyncImage(
                    model = getImageRequest(LocalContext.current, state.iconUrl),
                    contentDescription = state.title,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterVertically)
                )
                MarginHorizontal(8.dp)
            }
            Column(
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                H5(
                    text = state.title.withNoFontPadding(),
                    color = black2
                )

                state.subTitle?.let {
                    B1(text = state.subTitle)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
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
                state = state.copy(iconUrl = null)
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
        }
    }
}
