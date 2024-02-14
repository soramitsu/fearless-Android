package jp.co.soramitsu.wallet.impl.presentation.balance.detail.claimreward

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AttentionMessage
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.feature_wallet_impl.R

data class ClaimRewardsViewState(
    val chainIconUrl: String?,
    val lockedInfoItem: TitleValueViewState? = null,
    val transferableInfoItem: TitleValueViewState? = null,
    val feeInfoItem: TitleValueViewState? = null,
    val tokenSymbol: String? = null,
    val buttonState: ButtonViewState,
    val isLoading: Boolean = false,
) {
    companion object {
        val default = ClaimRewardsViewState(
            "",
            buttonState = ButtonViewState("", false)
        )
    }

    val tableItems = listOf(
        lockedInfoItem,
        transferableInfoItem,
        feeInfoItem
    ).mapNotNull { it }
}

interface ClaimRewardsScreenInterface {
    fun onNextClick()
    fun onNavigationClick()
    fun onItemClick(code: Int)
}

@Composable
fun ClaimRewardsContent(
    state: ClaimRewardsViewState,
    callback: ClaimRewardsScreenInterface
) {
    BottomSheetScreen {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                ToolbarBottomSheet(
                    title = stringResource(id = R.string.pool_claim_reward),
                    onNavigationClick = callback::onNavigationClick
                )

                MarginVertical(margin = 24.dp)

                if (state.chainIconUrl.isNullOrEmpty()) {
                    GradientIcon(
                        iconRes = R.drawable.ic_fearless_logo,
                        color = colorAccentDark,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .shimmer()
                    )
                } else {
                    GradientIcon(
                        icon = state.chainIconUrl,
                        color = colorAccentDark,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                MarginVertical(margin = 8.dp)
                H1(
                    text = state.tokenSymbol.orEmpty(),
                    color = black2,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 24.dp)
                InfoTable(items = state.tableItems, onItemClick = callback::onItemClick)
                MarginVertical(margin = 12.dp)
                AttentionMessage(
                    attentionText = stringResource(id = R.string.vesting_claim_disclaimer_title),
                    message = stringResource(id = R.string.vesting_claim_disclaimer_text)
                )
                MarginVertical(margin = 12.dp)

                Spacer(modifier = Modifier.weight(1f))

                AccentButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    text = state.buttonState.text,
                    enabled = state.buttonState.enabled,
                    loading = state.isLoading,
                    onClick = callback::onNextClick
                )

                MarginVertical(margin = 12.dp)
            }
        }
    }
}

@Preview
@Composable
private fun ClaimRewardsPreview() {
    val state = ClaimRewardsViewState(
        chainIconUrl = "",
        tokenSymbol = "XOR",
        transferableInfoItem = TitleValueViewState(
            title = "Transferable",
            value = "3 KSM",
            additionalValue = "\$5,05"
        ),
        lockedInfoItem = TitleValueViewState(
            title = "Vested Locked",
            value = "3 KSM",
            additionalValue = "\$5,05"
        ),
        feeInfoItem = TitleValueViewState(
            title = "Fee",
            value = "3 KSM",
            additionalValue = "\$5,05"
        ),
        buttonState = ButtonViewState("Confirm", true)
    )

    val emptyCallback = object : ClaimRewardsScreenInterface {
        override fun onNavigationClick() {}
        override fun onNextClick() {}
        override fun onItemClick(code: Int) {}
    }

    FearlessTheme {
        ClaimRewardsContent(
            state = state,
            callback = emptyCallback
        )
    }
}
