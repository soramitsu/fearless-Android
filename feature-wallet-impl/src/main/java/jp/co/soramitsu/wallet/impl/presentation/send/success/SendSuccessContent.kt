package jp.co.soramitsu.wallet.impl.presentation.send.success

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlurColor
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.send.success.SendSuccessViewState.Companion.CODE_HASH_CLICK

data class SendSuccessViewState(
    val tableItems: List<TitleValueViewState>
) {
    companion object {
        const val CODE_HASH_CLICK = 2
        val default = SendSuccessViewState(emptyList())
    }
}

interface SendSuccessScreenInterface {
    fun onClose()
    fun onHashClick()
}

@Composable
fun SendSuccessContent(
    state: SendSuccessViewState,
    callback: SendSuccessScreenInterface
) {
    BottomSheetScreen {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                IconButton(
                    onClick = callback::onClose,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(CircleShape)
                        .background(backgroundBlurColor)
                        .size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        tint = white,
                        contentDescription = null
                    )
                }
                MarginVertical(margin = 20.dp)
                GradientIcon(
                    iconRes = R.drawable.ic_fearless_logo,
                    color = colorAccentDark,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 16.dp)
                H2(
                    text = stringResource(id = R.string.all_done),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MarginVertical(margin = 8.dp)
                B0(
                    text = stringResource(id = R.string.send_success_message),
                    color = black2,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    textAlign = TextAlign.Center
                )
                MarginVertical(margin = 24.dp)
                InfoTable(
                    items = state.tableItems,
                    onItemClick = {
                        when (it) {
                            CODE_HASH_CLICK -> callback.onHashClick()
                        }
                    }
                )
                MarginVertical(margin = 12.dp)
            }
        }
    }
}

@Preview
@Composable
private fun SendSuccessPreview() {
    val state = SendSuccessViewState(
        listOf(
            TitleValueViewState(
                title = "Hash",
                value = "EBN4KURhvkEBN4KURhvkEBN4KURhvkEBN4KURhvk",
                clickState = TitleValueViewState.ClickState(R.drawable.ic_arrow_top_right_white_16, 1)
            ),
            TitleValueViewState(
                title = "Result",
                value = "Success"
            )
        )
    )

    val emptyCallback = object : SendSuccessScreenInterface {
        override fun onClose() {}
        override fun onHashClick() {}
    }

    FearlessTheme {
        SendSuccessContent(
            state = state,
            callback = emptyCallback
        )
    }
}
