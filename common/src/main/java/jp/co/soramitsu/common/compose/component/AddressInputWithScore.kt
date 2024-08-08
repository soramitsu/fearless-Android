package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.utils.withNoFontPadding

sealed interface AddressInputWithScore {
    val title: String

    data class Filled(
        override val title: String,
        val address: String,
        val image: Any,
        val score: Int?
    ) : AddressInputWithScore

    data class Empty(
        override val title: String,
        val hint: String
    ) : AddressInputWithScore
}

@Composable
fun AddressInputWithScore(
    state: AddressInputWithScore,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {

    BackgroundCorneredWithBorder(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        borderColor = white24
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = (state as? AddressInputWithScore.Filled)?.image
                    ?: R.drawable.ic_address_placeholder,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            MarginHorizontal(margin = 8.dp)
            Column(modifier = Modifier.weight(1f)) {
                H5(
                    text = state.title.withNoFontPadding(),
                    color = black2
                )
                when (state) {
                    is AddressInputWithScore.Empty -> {
                        B1(text = state.hint, color = black1)
                    }

                    is AddressInputWithScore.Filled -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            B1(
                                text = state.address,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                                color = white50
                            )
                            MarginHorizontal(margin = 8.dp)
                            state.score?.let { ScoreStar(score = it) }
                        }

                    }
                }
            }
            MarginHorizontal(margin = 8.dp)
            when (state) {
                is AddressInputWithScore.Empty -> {
                    Badge(
                        iconResId = R.drawable.ic_copy_16,
                        labelResId = R.string.chip_paste,
                        onClick = onPaste
                    )
                }

                is AddressInputWithScore.Filled -> {
                    Image(
                        res = R.drawable.ic_close_16_circle,
                        modifier = Modifier
                            .wrapContentSize()
                            .clickable(onClick = onClear)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AccountInputPreview() {
    val filled = AddressInputWithScore.Filled(
        "Send to",
        "BlueBird",
        "0x23d2ef23...23d23f23",
        100
    )
    val empty = AddressInputWithScore.Empty("Send to", "Public address")
    Column {
        AddressInputWithScore(filled, onClear = {}, onPaste = {})
        MarginVertical(margin = 6.dp)
        AddressInputWithScore(empty, onClear = {}, onPaste = {})
        MarginVertical(margin = 6.dp)
    }
}
