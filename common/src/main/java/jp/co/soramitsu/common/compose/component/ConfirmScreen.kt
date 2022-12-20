package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark

data class ConfirmScreenViewState(
    val toolbarViewState: ToolbarViewState,
    val amount: String?,
    val tableItems: List<TitleValueViewState>,
    val assetIcon: Icon,
    @StringRes val titleRes: Int,
    @StringRes val additionalMessageRes: Int? = null,
    val isLoading: Boolean
) {
    sealed class Icon {
        class Remote(val url: String) : Icon()
        class Local(@DrawableRes val res: Int) : Icon()
    }
}

@Composable
fun ConfirmScreen(state: ConfirmScreenViewState, onNavigationClick: () -> Unit, onConfirm: () -> Unit) {
    BottomSheetScreen(Modifier.verticalScroll(rememberScrollState())) {
        FullScreenLoading(isLoading = state.isLoading) {
            Toolbar(state = state.toolbarViewState, onNavigationClick = onNavigationClick)
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            ) {
                MarginVertical(margin = 24.dp)
                GradientIcon(icon = state.assetIcon, color = colorAccentDark, modifier = Modifier.align(Alignment.CenterHorizontally))
                H2(
                    text = stringResource(id = state.titleRes),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = black2
                )
                MarginVertical(margin = 8.dp)
                state.amount?.let {
                    H1(text = it, modifier = Modifier.align(Alignment.CenterHorizontally))
                    MarginVertical(margin = 24.dp)
                }
                InfoTable(state.tableItems)
                MarginVertical(margin = 24.dp)
                state.additionalMessageRes?.let {
                    AdditionalInfo(message = stringResource(id = it))
                    MarginVertical(margin = 16.dp)
                }
                AccentButton(
                    text = stringResource(id = R.string.common_confirm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = onConfirm
                )
                MarginVertical(margin = 24.dp)
            }
        }
    }
}

@Composable
private fun AdditionalInfo(message: String) {
    Row {
        Image(
            res = R.drawable.ic_info_14,
            modifier = Modifier
                .padding(6.dp)
                .size(12.dp)
                .align(Alignment.Top),
            tint = black1
        )
        val text = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(stringResource(id = R.string.common_note))
            }
            withStyle(style = SpanStyle()) {
                append(message)
            }
        }
        B2(text = text, color = black1, modifier = Modifier.align(Alignment.Top))
    }
}

@Preview
@Composable
private fun ConfirmJoinPoolScreenPreview() {
    val state = ConfirmScreenViewState(
        toolbarViewState = ToolbarViewState("Confirm", R.drawable.ic_arrow_back_24dp),
        tableItems = listOf(
            TitleValueViewState("From", "Account for join", "0x3784348729384923849223423"),
            TitleValueViewState("Amount", "10KSM", "$30"),
            TitleValueViewState("Network Fee", "0.0051 KSM", "$0.32")
        ),
        amount = "10KSM",
        assetIcon = ConfirmScreenViewState.Icon.Remote("https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg"),
        titleRes = R.string.common_confirm,
        additionalMessageRes = R.string.pool_staking_unstake_alert,
        isLoading = false
    )
    FearlessTheme {
        ConfirmScreen(state, {}, {})
    }
}
