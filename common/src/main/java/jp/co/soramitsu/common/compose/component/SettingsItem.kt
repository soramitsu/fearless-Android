package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.utils.clickableSingle

@Composable
fun SettingsItem(
    icon: Painter,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clickableSingle(
                indication = LocalIndication.current
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(24.dp),
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.customColors.colorAccent
        )
        B1(
            modifier = Modifier
                .weight(1f)
                .padding(
                    vertical = 14.dp,
                    horizontal = 12.dp
                ),
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(24.dp),
            painter = painterResource(R.drawable.ic_arrow_right_24),
            contentDescription = null,
            tint = MaterialTheme.customColors.white
        )
    }
}

@Preview
@Composable
fun SettingsItemPreview() {
    SettingsItem(
        icon = painterResource(R.drawable.ic_settings_wallets),
        text = "Item"
    )
}
