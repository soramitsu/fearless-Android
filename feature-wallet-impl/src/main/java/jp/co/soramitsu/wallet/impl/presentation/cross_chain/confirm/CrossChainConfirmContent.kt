package jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.DoubleGradientIcon
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.feature_wallet_impl.R

data class CrossChainConfirmViewState(
    val originChainIcon: GradientIconData?,
    val destinationChainIcon: GradientIconData?,
    val toInfoItem: TitleValueViewState? = null,
    val originNetworkItem: TitleValueViewState? = null,
    val destinationNetworkItem: TitleValueViewState? = null,
    val amountInfoItem: TitleValueViewState? = null,
    val tipInfoItem: TitleValueViewState? = null,
    val originFeeInfoItem: TitleValueViewState? = null,
    val destinationFeeInfoItem: TitleValueViewState? = null,
    val buttonState: ButtonViewState,
    val isLoading: Boolean = false
) {
    companion object {
        const val CODE_WARNING_CLICK = 3

        val default = CrossChainConfirmViewState(
            originChainIcon = null,
            destinationChainIcon = null,
            buttonState = ButtonViewState("", false)
        )
    }

    val tableItems = listOf(
        toInfoItem,
        originNetworkItem,
        destinationNetworkItem,
        tipInfoItem,
        originFeeInfoItem,
        destinationFeeInfoItem
    ).mapNotNull { it }
}

interface CrossChainConfirmScreenInterface {
    fun copyRecipientAddressClicked()
    fun onNextClick()
    fun onNavigationClick()
    fun onItemClick(code: Int)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CrossChainConfirmContent(
    state: CrossChainConfirmViewState,
    callback: CrossChainConfirmScreenInterface
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    FullScreenLoading(
        isLoading = state.isLoading,
        contentAlignment = Alignment.BottomStart
    ) {
        BottomSheetScreen {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    ToolbarBottomSheet(
                        title = stringResource(id = R.string.common_preview),
                        onNavigationClick = callback::onNavigationClick
                    )

                    MarginVertical(margin = 24.dp)

                    if (state.originChainIcon != null && state.destinationChainIcon != null) {
                        DoubleGradientIcon(
                            leftImage = provideGradientIconState(state.originChainIcon),
                            rightImage = provideGradientIconState(state.destinationChainIcon)
                        )
                    }
                    MarginVertical(margin = 16.dp)
                    H2(
                        text = stringResource(id = R.string.sending),
                        color = black2,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    MarginVertical(margin = 4.dp)
                    H1(
                        text = state.amountInfoItem?.value.orEmpty(),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    MarginVertical(margin = 24.dp)
                    InfoTable(items = state.tableItems, onItemClick = callback::onItemClick)
                    MarginVertical(margin = 12.dp)
                }

                Spacer(modifier = Modifier.weight(1f))

                Column {
                    AccentButton(
                        state = state.buttonState,
                        onClick = {
                            keyboardController?.hide()
                            callback.onNextClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(48.dp)
                    )

                    MarginVertical(margin = 12.dp)
                }
            }
        }
    }
}

data class GradientIconData(
    val url: String?,
    val color: String?
)

private fun provideGradientIconState(gradientIconData: GradientIconData): GradientIconState {
    return if (gradientIconData.url == null) {
        GradientIconState.Local(
            res = R.drawable.ic_fearless_logo
        )
    } else {
        GradientIconState.Remote(
            url = gradientIconData.url,
            color = gradientIconData.color
        )
    }
}
