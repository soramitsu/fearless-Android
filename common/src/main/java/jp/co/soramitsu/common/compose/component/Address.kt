package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white

@Composable
fun Address(
    address: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier.background(
            color = MaterialTheme.customColors.white08,
            shape = RoundedCornerShape(100.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable(onClick = onClick)
        ) {
            val showAddressParts = address.take(7) + "..." + address.takeLast(5)
            Text(
                text = showAddressParts,
                style = MaterialTheme.customTypography.body2,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .testTag("address")
                    .wrapContentWidth()
                    .align(CenterVertically)
            )
            MarginHorizontal(margin = 4.dp)
            Icon(
                painter = painterResource(id = R.drawable.ic_copy_16),
                tint = white,
                contentDescription = null,
                modifier = Modifier
                    .testTag("address_copy_icon")
                    .align(CenterVertically)
            )
        }
    }
}

@Preview
@Composable
private fun AddressPreview() {
    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            Address(
                address = "0x32141235qwegtf24315reqwerfasdgqwert243rfasdvgergsdf",
                onClick = {}
            )
        }
    }
}
