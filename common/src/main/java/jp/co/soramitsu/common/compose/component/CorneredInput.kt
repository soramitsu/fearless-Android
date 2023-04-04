package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
    backgroundColor: Color = white04,
    borderColor: Color = white08,
    state: String?,
    hintLabel: String? = null,
    onInput: (String) -> Unit
) {
    BackgroundCorneredWithBorder(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = backgroundColor,
        borderColor = borderColor
    ) {
        TextFieldHint(
            state = state,
            onInput = onInput,
            Hint = { SearchHint(hintLabel) }
        )
    }
}

@Composable
private fun SearchHint(text: String?) {
    Row {
        MarginHorizontal(margin = 12.dp)
        Image(
            res = R.drawable.ic_search,
            modifier = Modifier.size(24.dp),
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
    Column() {
        CorneredInput(state = "", onInput = {})
        CorneredInput(state = "AAAAAA", onInput = {})
    }
}
