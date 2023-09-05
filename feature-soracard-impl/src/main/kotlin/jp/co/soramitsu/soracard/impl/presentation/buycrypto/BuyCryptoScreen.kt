package jp.co.soramitsu.soracard.impl.presentation.buycrypto

import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.ProgressDialog
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.feature_soracard_impl.R
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.oauth.R as SoraCardR

@Composable
fun BuyCryptoScreen(
    state: BuyCryptoState,
    onPageFinished: () -> Unit,
    onReceivedError: (error: WebResourceResponse?) -> Unit,
    onAlertCloseClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            onPageFinished()
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            onReceivedError(errorResponse)
                        }
                    }

                    settings.javaScriptEnabled = true
                    setBackgroundColor(white04.toArgb())
                }
            },
            update = {
                it.loadData(state.script, "text/html", "base64")
            }
        )

        if (state.showAlert) {
            PaymentWidgetUnavailableAlert(onAlertCloseClick)
        }

        if (state.loading) {
            ProgressDialog()
        }
    }
}

@Composable
private fun PaymentWidgetUnavailableAlert(onCloseClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = Dimens.x2)
    ) {
        Image(
            modifier = Modifier
                .align(Alignment.CenterHorizontally),
            res = R.drawable.ic_error_80,
            contentDescription = null
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.x3),
            style = MaterialTheme.customTypography.headline1.copy(textAlign = TextAlign.Center),
            text = stringResource(id = SoraCardR.string.payment_widget_unavailable_message),
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.x3),
            style = MaterialTheme.customTypography.paragraphM.copy(textAlign = TextAlign.Center),
            text = stringResource(id = SoraCardR.string.payment_widget_unavailable_description),
        )
        FilledButton(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = Dimens.x7),
            size = Size.Large,
            order = Order.SECONDARY,
            text = stringResource(id = SoraCardR.string.payment_widget_unavailable_confirm),
            onClick = onCloseClick
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewAlert() {
    FearlessAppTheme {
        PaymentWidgetUnavailableAlert {}
    }
}
