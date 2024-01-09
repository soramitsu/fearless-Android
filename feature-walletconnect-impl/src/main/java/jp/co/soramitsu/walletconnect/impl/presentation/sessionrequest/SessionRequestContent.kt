package jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.InfoItem
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.utils.withNoFontPadding

data class SessionRequestViewState(
    val connectionUrl: String?,
    val message: InfoItemViewState,
    val wallet: WalletItemViewState
) {

    companion object {
        val default = SessionRequestViewState(
            message = InfoItemViewState.default,
            wallet = WalletItemViewState(
                id = 0,
                title = "",
                walletIcon = R.drawable.ic_wallet,
                isSelected = false
            ),
            connectionUrl = null
        )
    }
}

interface SessionRequestScreenInterface {
    fun onClose()
    fun onPreviewClick()
}

@Composable
fun SessionRequestContent(state: SessionRequestViewState, callback: SessionRequestScreenInterface) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .nestedScroll(rememberNestedScrollInteropConnection())
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(id = R.string.connection_sign_this_message_question),
                    style = MaterialTheme.customTypography.header4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Icon(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = callback::onClose),
                    painter = painterResource(id = R.drawable.ic_close),
                    tint = white,
                    contentDescription = null
                )
            }

            MarginVertical(margin = 16.dp)

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GradientIcon(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    iconRes = R.drawable.ic_fearless_logo,
                    color = colorAccentDark
                )

                state.connectionUrl?.let {
                    Text(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        text = state.connectionUrl,
                        style = MaterialTheme.customTypography.header3,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }

                WalletItem(
                    state = state.wallet,
                    onSelected = {}
                )

                InfoItem(
                    state = state.message
                )

                val warningText = stringResource(id = R.string.common_warning_capitalized_with_dots)
                val remainingText = stringResource(id = R.string.connection_sign_message_warning)
                val styledText = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = warningOrange)) {
                        append(warningText)
                    }
                    withStyle(style = SpanStyle()) {
                        append(" ")
                        append(remainingText)
                    }
                }.withNoFontPadding()

                Row {
                    Image(res = R.drawable.ic_alert_16)
                    MarginHorizontal(margin = 8.dp)
                    Text(
                        style = MaterialTheme.customTypography.body2,
                        text = styledText,
                        color = white50
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .wrapContentHeight()
            ) {
                MarginVertical(margin = 12.dp)
                AccentButton(
                    text = stringResource(id = R.string.common_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = callback::onPreviewClick
                )

                MarginVertical(margin = 16.dp)
            }
        }
    }
}

@Preview
@Composable
private fun SessionRequestPreview() {
    val state = SessionRequestViewState(
        message = InfoItemViewState(
            title = "Message",
            singleLine = true,
            subtitle = "{some_data_from_dapp_data_from_dapp_data_from_dapp_data_from_dapp_data_from_dapp_data_from_dapp}"
        ),
        wallet = WalletItemViewState(
            id = 1,
            title = "Wallet",
            walletIcon = R.drawable.ic_wallet,
            isSelected = false
        ),
        connectionUrl = "some_url_to_dapp"
    )

    val emptyCallback = object : SessionRequestScreenInterface {
        override fun onClose() {}
        override fun onPreviewClick() {}
    }

    FearlessTheme {
        SessionRequestContent(
            state = state,
            callback = emptyCallback
        )
    }
}
