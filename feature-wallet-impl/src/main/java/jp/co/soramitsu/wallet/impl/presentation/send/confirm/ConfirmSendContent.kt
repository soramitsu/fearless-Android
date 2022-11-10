package jp.co.soramitsu.wallet.impl.presentation.send.confirm

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.feature_wallet_impl.R

data class ConfirmSendViewState(
    val chainIconUrl: String?,
    val fromInfoItem: TitleValueViewState? = null,
    val toInfoItem: TitleValueViewState? = null,
    val amountInfoItem: TitleValueViewState? = null,
    val tipInfoItem: TitleValueViewState? = null,
    val feeInfoItem: TitleValueViewState? = null,
    val buttonState: ButtonViewState,
    val isLoading: Boolean = false
) {
    companion object {
        val default = ConfirmSendViewState(
            "",
            buttonState = ButtonViewState("", false)
        )
    }

    val tableItems = listOf(
        fromInfoItem,
        toInfoItem,
        amountInfoItem,
        tipInfoItem,
        feeInfoItem
    ).mapNotNull { it }
}

interface ConfirmSendScreenInterface {
    fun copyRecipientAddressClicked()
    fun onNextClick()
    fun onNavigationClick()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConfirmSendContent(
    state: ConfirmSendViewState,
    callback: ConfirmSendScreenInterface
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    FullScreenLoading(isLoading = state.isLoading) {
        BottomSheetScreen {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    ToolbarBottomSheet(
                        title = stringResource(id = R.string.preview),
                        onNavigationClicked = callback::onNavigationClick
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
                    MarginVertical(margin = 16.dp)
                    H2(
                        text = "Sending",
                        color = black2,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    MarginVertical(margin = 8.dp)
                    H1(
                        text = state.amountInfoItem?.value.orEmpty(),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    MarginVertical(margin = 24.dp)
                    InfoTable(items = state.tableItems)
                    Spacer(modifier = Modifier.weight(1f))
                    MarginVertical(margin = 12.dp)

                    AccentButton(
                        state = state.buttonState,
                        onClick = {
                            keyboardController?.hide()
                            callback.onNextClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )

                    MarginVertical(margin = 12.dp)
                }
            }
        }
    }
}

@Preview
@Composable
private fun ConfirmSendPreview() {
    val state = ConfirmSendViewState(
        chainIconUrl = "",
        fromInfoItem = TitleValueViewState(
            title = "From",
            value = "My Awesome Wallet",
            additionalValue = "EBN4KURhvkEBN4KURhvkEBN4KURhvkEBN4KURhvk"
        ),
        toInfoItem = TitleValueViewState(
            title = "To",
            value = "EBN4KURhvkEBN4KURhvkEBN4KURhvkEBN4KURhvk"
        ),
        amountInfoItem = TitleValueViewState(
            title = "Amount",
            value = "100 KSM",
            additionalValue = "EBN4KURhvkEBN4KURhvkEBN4KURhvkEBN4KURhvk"
        ),
        tipInfoItem = null,
//        tipInfoItem = TitleValueViewState(
//            title = "Tip",
//            value = "2 KSM",
//            additionalValue = "\$3,35"
//        ),
        feeInfoItem = TitleValueViewState(
            title = "Fee",
            value = "3 KSM",
            additionalValue = "\$5,05"
        ),
        buttonState = ButtonViewState("Continue", true)
    )

    val emptyCallback = object : ConfirmSendScreenInterface {
        override fun onNavigationClick() {}
        override fun copyRecipientAddressClicked() {}
        override fun onNextClick() {}
    }

    FearlessTheme {
        ConfirmSendContent(
            state = state,
            callback = emptyCallback
        )
    }
}
