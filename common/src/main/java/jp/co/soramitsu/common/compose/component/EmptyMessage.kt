package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.gray2

@Composable
fun EmptyMessage(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_alert,
    @StringRes title: Int = R.string.common_search_assets_alert_title,
    @StringRes message: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        Image(res = icon)
        H3(text = stringResource(id = title))
        B0(
            text = stringResource(id = message),
            color = gray2
        )
    }
}
