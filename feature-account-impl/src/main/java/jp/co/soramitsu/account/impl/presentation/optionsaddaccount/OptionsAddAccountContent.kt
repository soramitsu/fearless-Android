package jp.co.soramitsu.account.impl.presentation.optionsaddaccount

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextButton
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.feature_account_api.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class OptionsAddAccountScreenViewState(
    val metaId: Long,
    val chainId: ChainId,
    val chainName: String,
    val markedAsNotNeed: Boolean,
    val assetId: String,
    val priceId: String?
)

@Composable
fun OptionsAddAccountContent(
    state: OptionsAddAccountScreenViewState,
    onCreate: (chainId: ChainId, metaId: Long) -> Unit,
    onImport: (chainId: ChainId, metaId: Long) -> Unit,
    onNoNeed: (chainId: ChainId, metaId: Long, assetId: String, priceId: String?) -> Unit
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            H3(text = stringResource(id = R.string.recovery_source_type))
            MarginVertical(margin = 28.dp)
            GrayButton(
                text = stringResource(id = R.string.create_new_account),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                onCreate(state.chainId, state.metaId)
            }
            MarginVertical(margin = 12.dp)
            GrayButton(
                text = stringResource(id = R.string.already_have_account),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                onImport(state.chainId, state.metaId)
            }
            if (!state.markedAsNotNeed) {
                MarginVertical(margin = 12.dp)
                TextButton(
                    text = stringResource(id = R.string.i_dont_need_account),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = customButtonColors(grayButtonBackground, colorAccentDark)
                ) {
                    onNoNeed(state.chainId, state.metaId, state.assetId, state.priceId)
                }
            }
            MarginVertical(margin = 12.dp)
        }
    }
}

@Preview
@Composable
private fun OptionsAddAccountScreenPreview() {
    FearlessTheme {
        OptionsAddAccountContent(
            state = OptionsAddAccountScreenViewState(
                metaId = 1,
                chainId = "",
                chainName = "Kusama",
                markedAsNotNeed = false,
                assetId = "",
                priceId = null
            ),
            onCreate = { t, t2 -> },
            onImport = { t, t2 -> },
            onNoNeed = { t, t2, t3, t4 -> }
        )
    }
}
