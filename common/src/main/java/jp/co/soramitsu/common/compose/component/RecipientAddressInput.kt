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

sealed interface RecipientAddressInput {
    val title: String

    data class Filled(
        override val title: String,
        val address: String,
        val image: Any
    ) : RecipientAddressInput

    data class Empty(
        override val title: String,
        val hint: String
    ) : RecipientAddressInput
}

@Composable
fun RecipientAddressInput(
    state: RecipientAddressInput,
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
                model = (state as? RecipientAddressInput.Filled)?.image
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
                    is RecipientAddressInput.Empty -> {
                        B1(text = state.hint, color = black1)
                    }

                    is RecipientAddressInput.Filled -> {
                        B1(
                            text = state.address,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = white50
                        )
                    }
                }
            }
            MarginHorizontal(margin = 8.dp)
            when (state) {
                is RecipientAddressInput.Empty -> {
                    Badge(
                        iconResId = R.drawable.ic_copy_16,
                        labelResId = R.string.chip_paste,
                        onClick = onPaste
                    )
                }

                is RecipientAddressInput.Filled -> {
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
    val filled = RecipientAddressInput.Filled(
        "Send to",
        "BlueBird",
        "0x23d2ef23...23d23f23"
    )
    val empty = RecipientAddressInput.Empty("Send to", "Public address")
    Column {
        RecipientAddressInput(filled, onClear = {}, onPaste = {})
        MarginVertical(margin = 6.dp)
        RecipientAddressInput(empty, onClear = {}, onPaste = {})
        MarginVertical(margin = 6.dp)
    }
}
