package jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.MarginVertical

@Composable
fun TransactionSettingsContent(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        MarginVertical(margin = 2.dp)
        Grip(Modifier.align(Alignment.CenterHorizontally))
        MarginVertical(margin = 8.dp)
    }
}

@Preview
@Composable
fun TransactionSettingsContentPreview() {
    TransactionSettingsContent()
}
