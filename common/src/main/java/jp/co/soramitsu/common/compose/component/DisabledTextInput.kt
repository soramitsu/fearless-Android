package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.withNoFontPadding

@Composable
fun DisabledTextInput(
    hint: String,
    value: String,
    @DrawableRes endIcon: Int? = null,
    endIconClick: (() -> Unit)? = null
) {
    val isFocused = remember { mutableStateOf(false) }

    BackgroundCorneredWithBorder(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        borderColor = when (isFocused.value) {
            true -> colorAccentDark
            else -> white24
        }
    ) {
        val contentAlpha = 0.4f
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
                    .alpha(contentAlpha)
            ) {
                H5(text = hint.withNoFontPadding(), color = black2)
                B1(text = value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MarginHorizontal(8.dp)

            if (endIcon != null) {
                val clickModifier =
                    endIconClick?.let { Modifier.clickable(onClick = endIconClick) } ?: Modifier
                Image(
                    res = endIcon,
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
fun DisabledTextInputPreview() {
    FearlessAppTheme {
        DisabledTextInput(
            "Hash",
            "0xjewhfbg4yfrheuvbfwyu4rhbv24uyrgh248urhfg",
            endIcon = R.drawable.ic_more_vertical
        )
    }
}