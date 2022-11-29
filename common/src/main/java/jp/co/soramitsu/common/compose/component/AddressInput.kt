package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.withNoFontPadding

data class AddressInputState(
    val title: String,
    val input: String,
    val image: Any
)

@Composable
fun AddressInput(
    state: AddressInputState,
    onInput: (String) -> Unit = {},
    onInputClear: () -> Unit = {}
) {
    val isFocused = remember { mutableStateOf(false) }

    BackgroundCorneredWithBorder(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        borderColor = when (isFocused.value) {
            true -> colorAccentDark
            else -> white24
        }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Icon(
                painter = rememberAsyncImagePainter(model = state.image),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
            ) {
                H5(text = state.title.withNoFontPadding(), color = black2)
                InputWithHint(
                    modifier = Modifier.onFocusChanged {
                        isFocused.value = it.isFocused
                    },
                    state = state.input,
                    cursorBrush = SolidColor(colorAccentDark),
                    onInput = onInput,
                    Hint = {
                        Row {
                            MarginHorizontal(margin = 6.dp)
                            B1(text = "Public address", color = black1)
                        }
                    }
                )
            }
            if (state.input.isNotEmpty()) {
                Image(
                    res = R.drawable.ic_close_16_circle,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .wrapContentSize()
                        .clickable {
                            onInput("")
                            onInputClear()
                        }
                )
            }
        }
    }
}

@Preview
@Composable
private fun AccountInputPreview() {
    val state = AddressInputState(
        title = "Send to",
        input = "0xsjkdflsdgueroirgfosdifsd;fgoksd;fg;sd845tg849",
        image = painterResource(id = R.drawable.ic_address_placeholder)
    )
    FearlessTheme {
        AddressInput(state)
    }
}
