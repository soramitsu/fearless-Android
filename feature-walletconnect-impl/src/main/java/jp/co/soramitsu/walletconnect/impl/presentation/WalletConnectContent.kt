package jp.co.soramitsu.walletconnect.impl.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.theme.FearlessTheme

data class Web3WalletViewState(
    val url: String
) {
    companion object {
        val default = Web3WalletViewState("")
    }
}

interface WalletConnectScreenInterface {
    fun onClose()
}

@Composable
fun WalletConnectContent(state: Web3WalletViewState, callback: WalletConnectScreenInterface) {
    BottomSheetScreen {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            H5(
                modifier = Modifier.clickable(onClick = callback::onClose),
                text = state.url,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview
@Composable
private fun WalletConnectPreview() {
    val state = Web3WalletViewState(
        "wc:You can now back to your app and do that you're usually do"
    )

    val emptyCallback = object : WalletConnectScreenInterface {
        override fun onClose() {}
    }

    FearlessTheme {
        WalletConnectContent(
            state = state,
            callback = emptyCallback
        )
    }
}
