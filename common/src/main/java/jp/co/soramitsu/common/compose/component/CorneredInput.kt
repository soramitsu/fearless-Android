package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.common.compose.theme.white08

@Composable
fun CorneredInput(
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier.height(32.dp),
    backgroundColor: Color = white04,
    borderColor: Color = white08,
    state: String?,
    hintLabel: String? = null,
    onInput: (String) -> Unit
) {
    BackgroundCorneredWithBorder(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = backgroundColor,
        borderColor = borderColor
    ) {
        TextFieldHint(
            modifier = textModifier,
            state = state,
            onInput = onInput,
            Hint = { SearchHint(hintLabel) }
        )
        if (!state.isNullOrEmpty()) {
            Image(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(8.dp)
                    .wrapContentSize()
                    .clickable { onInput("") },
                res = R.drawable.ic_close_16_white_circle
            )
        }
    }
}

@Composable
private fun SearchHint(text: String?) {
    Row {
        MarginHorizontal(margin = 12.dp)
        Image(
            res = R.drawable.ic_search,
            modifier = Modifier
                .size(15.dp)
                .align(CenterVertically),
            tint = black2
        )
        MarginHorizontal(margin = 8.dp)
        text?.let {
            B1(
                text = text,
                color = black2
            )
        }
    }
}

@Preview
@Composable
private fun PreviewCorneredInput() {
    Column {
        CorneredInput(state = "", onInput = {})
        MarginVertical(margin = 4.dp)
        CorneredInput(modifier = Modifier.padding(horizontal = 8.dp), state = "", onInput = {}, hintLabel = "Hint text")
        MarginVertical(margin = 4.dp)
        CorneredInput(textModifier = Modifier.height(48.dp), state = "AAAAAA", onInput = {})
    }
}
