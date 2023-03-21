package jp.co.soramitsu.soracard.impl.presentation.buycrypto

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import jp.co.soramitsu.common.compose.component.ProgressDialog

@Composable
fun BuyCryptoScreen(
    state: BuyCryptoState,
    onPageFinished: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            onPageFinished()
                        }
                    }

                    settings.javaScriptEnabled = true
                }
            },
            update = {
                it.loadData(state.script, "text/html", "base64")
            }
        )

        if (state.loading) {
            ProgressDialog()
        }
    }
}
