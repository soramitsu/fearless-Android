package jp.co.soramitsu.wallet.impl.presentation.cross_chain.wallet_type

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R

interface SelectWalletTypeScreenInterface {
    fun onNavigationClick()
    fun onMyWalletClick()
    fun onExternalWalletClick()
}

@Composable
fun SelectWalletTypeContent(
    callback: SelectWalletTypeScreenInterface
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            ToolbarBottomSheet(
                title = stringResource(id = R.string.cross_chain_wallet_title),
                onNavigationClick = callback::onNavigationClick
            )

            MarginVertical(margin = 20.dp)
            GrayButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.cross_chain_wallet_my)
            ) {
                callback.onMyWalletClick()
            }
            MarginVertical(margin = 12.dp)
            GrayButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.cross_chain_wallet_external)
            ) {
                callback.onExternalWalletClick()
            }
            MarginVertical(margin = 12.dp)
        }
    }
}
