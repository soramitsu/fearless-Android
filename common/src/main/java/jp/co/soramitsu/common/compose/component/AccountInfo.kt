package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white

data class AccountInfoViewState(
    val address: String,
    val accountName: String,
    val image: Any,
    val caption: String
)

@Composable
fun AccountInfo(state: AccountInfoViewState) {
    BackgroundCorneredWithBorder(
        modifier = Modifier
            .fillMaxWidth()
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
                    .align(CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)
            Column(
                modifier = Modifier
                    .align(CenterVertically)
                    .weight(1f)
            ) {
                H5(text = state.caption, color = black2)
                B1(text = state.accountName, color = white)
            }
        }
    }
}

@Preview
@Composable
private fun AccountInfoPreview() {
    val state = AccountInfoViewState(
        address = "0xsjkdflsdgueroirgfosdifsd;fgoksd;fg;sd845tg849",
        accountName = "My account",
        image = painterResource(id = R.drawable.ic_wallet),
        caption = "Join pool from"
    )
    FearlessTheme {
        AccountInfo(state)
    }
}
