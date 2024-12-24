package jp.co.soramitsu.account.impl.presentation.options_ecosystem_accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.feature_account_api.R

data class OptionsEcosystemAccountsScreenViewState(
    val metaId: Long,
    val type: ImportAccountType
)

@Composable
fun OptionsEcosystemAccountsContent(
    state: OptionsEcosystemAccountsScreenViewState,
    onBackupEcosystemAccountsClicked: () -> Unit,
    onEcosystemAccountsClicked: () -> Unit,
    onBackClicked: () -> Unit
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    tint = white,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .clickable { onBackClicked() }
                )
                H3(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    text = stringResource(id = R.string.ecosystem_options_title),
                    textAlign = TextAlign.Center
                )
            }
            MarginVertical(margin = 28.dp)
            GrayButton(
                text = stringResource(id = R.string.ecosystem_options_backup_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onBackupEcosystemAccountsClicked
            )
            MarginVertical(margin = 12.dp)
            GrayButton(
                text = stringResource(id = R.string.ecosystem_options_details_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onEcosystemAccountsClicked
            )
            MarginVertical(margin = 12.dp)
        }
    }
}

@Preview
@Composable
private fun OptionsEcosystemAccountsScreenPreview() {
    FearlessAppTheme {
        OptionsEcosystemAccountsContent(
            state = OptionsEcosystemAccountsScreenViewState(
                metaId = 1,
                type = ImportAccountType.Ethereum
            ),
            onBackupEcosystemAccountsClicked = { },
            onEcosystemAccountsClicked = { },
            onBackClicked = {}
        )
    }
}
