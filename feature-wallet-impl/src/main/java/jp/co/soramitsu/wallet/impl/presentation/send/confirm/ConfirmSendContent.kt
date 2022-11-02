package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ShimmerB0
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.feature_wallet_impl.R

data class ConfirmSendViewState(
    val chainIconUrl: String?,
    val fromName: String?,
    val fromAddress: String?,
    val toName: String?,
    val toAddress: String,
    val amount: String,
    val amountFiat: String?,
    val fee: String,
    val feeFiat: String?,
    val tip: String?,
    val tipFiat: String?,
    val buttonState: ButtonViewState
) {
    companion object {
        val default = ConfirmSendViewState(
            "", "", "", "", "", "", "", "", "", "", "",
            buttonState = ButtonViewState("", false)
        )
    }
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
                    onNavigationClicked = { callback.onNavigationClick() }
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
                    text = state.amount,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 24.dp)
                BackgroundCorneredWithBorder(Modifier.fillMaxWidth()) {
                    Column {
                        val isSenderNameSpecified = !state.fromName.isNullOrEmpty()
                        SendConfirmItem(
                            title = "From",
                            contentLineOne = if (isSenderNameSpecified) state.fromName else state.fromAddress,
                            contentLineTwo = if (isSenderNameSpecified) state.fromAddress else null
                        )
                        val isRecipientNameSpecified = !state.toName.isNullOrEmpty()
                        SendConfirmItem(
                            title = "To",
                            contentLineOne = if (isRecipientNameSpecified) state.toName else state.toAddress,
                            contentLineTwo = if (isRecipientNameSpecified) state.toAddress else null,
                            modifier = Modifier.clickable {
                                callback.copyRecipientAddressClicked()
                            }
                        )
                        SendConfirmItem(Modifier, "Amount", state.amount, state.amountFiat)
                        if (!state.tip.isNullOrEmpty()) {
                            SendConfirmItem(Modifier, "Tip", state.tip, state.tipFiat)
                        }
                        SendConfirmItem(Modifier, "Network Fee", state.fee, state.feeFiat)
                    }
                }

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

@Composable
private fun SendConfirmItem(
    modifier: Modifier = Modifier,
    title: String,
    contentLineOne: String? = null,
    contentLineTwo: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        H5(
            text = title,
            color = black2,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        MarginHorizontal(margin = 12.dp)
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            if (contentLineOne.isNullOrEmpty() && contentLineTwo.isNullOrEmpty()) {
                ShimmerB0(Modifier.size(200.dp, 20.dp))
            } else {
                contentLineOne?.let { H5(text = it) }
                contentLineTwo?.let {
                    B1(
                        text = it,
                        color = black2,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
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
        fromName = "My Awesome Wallet",
        fromAddress = "EBN4KURhvkEBN4KURhvkEBN4KURhvkEBN4KURhvk",
        toName = "Better Friend Wallet",
        toAddress = "EBN4K...URhvk",
        amount = "100 KSM",
        amountFiat = "\$95485,05",
        fee = "3 KSM",
        feeFiat = "\$5,05",
        tip = "3 KSM",
        tipFiat = "\$5,05",
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
