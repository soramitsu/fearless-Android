package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.ImportRemoteWalletState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views.CompactWalletItemViewState
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.gray2

data class WalletImportedState(
    val wallet: BackupAccountMeta?
) : ImportRemoteWalletState

interface WalletImportedCallback {

    fun onBackClick()

    fun onContinueClick()

    fun onImportMore()
}

@Composable
internal fun WalletImportedScreen(
//    activity: Activity,
    state: WalletImportedState,
    callback: WalletImportedCallback,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.import_remote_wallet_title_imported),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )
        MarginVertical(margin = 24.dp)

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            MarginVertical(margin = 16.dp)
            WalletItem(
                state = CompactWalletItemViewState(title = state.wallet?.name.orEmpty()),
                onSelected = {}
            )
            MarginVertical(margin = 16.dp)
            B0(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.import_remote_wallet_success_imported),
                textAlign = TextAlign.Center,
                color = gray2
            )
            MarginVertical(margin = 16.dp)
        }

        Spacer(modifier = Modifier.weight(1f))
        AccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            text = stringResource(R.string.common_continue),
            enabled = true,
            onClick = callback::onContinueClick
        )
        MarginVertical(8.dp)
        GrayButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            text = stringResource(R.string.import_remote_wallet_btn_import_more),
            onClick = callback::onImportMore
        )
        MarginVertical(12.dp)
    }
}


@Preview
@Composable
private fun PreviewWalletImportedScreen() {
    FearlessAppTheme {
        WalletImportedScreen(
            state = WalletImportedState(
                wallet = BackupAccountMeta("Name", "address")
            ),
            callback = object : WalletImportedCallback {
                override fun onBackClick() {}
                override fun onContinueClick() {}
                override fun onImportMore() {}
            }
        )
    }
}
