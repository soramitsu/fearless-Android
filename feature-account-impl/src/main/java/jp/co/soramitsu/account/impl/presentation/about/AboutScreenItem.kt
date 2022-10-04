package jp.co.soramitsu.account.impl.presentation.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.about.model.AboutItemModel
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.feature_account_impl.R

@Composable
fun AboutScreenItem(item: AboutItemModel) {
    Column(
        Modifier.clickable {
            item.onClick()
        }
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(res = item.iconResId)
            MarginHorizontal(margin = 12.dp)
            Column {
                B1(text = stringResource(id = item.titleResId), maxLines = 1)
                item.text?.let { B2(text = it, maxLines = 1) }
            }
            Spacer(Modifier.weight(1f))
            Image(res = R.drawable.ic_chevron_right_rounded_24)
        }
        if (item.showDivider) {
            Divider(
                color = black3,
                modifier = Modifier
                    .height(1.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )
        }
    }
}
