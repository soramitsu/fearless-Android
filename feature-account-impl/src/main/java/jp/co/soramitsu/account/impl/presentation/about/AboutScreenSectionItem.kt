package jp.co.soramitsu.account.impl.presentation.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.about.model.AboutSectionModel
import jp.co.soramitsu.common.compose.component.CapsTitle2
import jp.co.soramitsu.common.compose.theme.black1

@Composable
fun AboutScreenSectionItem(item: AboutSectionModel) {
    CapsTitle2(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        color = black1,
        text = stringResource(id = item.titleResId)
    )
}
