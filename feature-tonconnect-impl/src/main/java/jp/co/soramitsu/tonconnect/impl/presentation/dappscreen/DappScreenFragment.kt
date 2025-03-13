package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ProgressDialog
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.JsonBuilder
import okhttp3.Response

@AndroidEntryPoint
class DappScreenFragment : BaseComposeBottomSheetDialogFragment<DappScreenViewModel>() {

    override val viewModel: DappScreenViewModel by viewModels()

    @Suppress("LateinitUsage")
    private lateinit var webView: BridgeWebView
    private var isLoading = false

    private val webViewCallback = object : WebViewFixed.Callback() {
        override fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme ?: ""
            return scheme != "https"
        }

        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            isLoading = true
        }

        override fun onPageFinished(url: String) {
            super.onPageFinished(url)
            isLoading = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomBottomSheetDialogTheme)
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsStateWithLifecycle()

        FearlessAppTheme {
            BottomSheetScreen {
                Toolbar(
                    state = ToolbarViewState(state.name.orEmpty(), R.drawable.ic_close),
                    onNavigationClick = ::back
                )
                MarginVertical(margin = 8.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { context ->
                            webView = BridgeWebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                            webViewSetup(webView, state)
                            webView
                        },
                        update = {}
                    )

                    if (isLoading) {
                        ProgressDialog()
                    }
                }
            }
        }
    }

    private fun webViewSetup(webView: BridgeWebView, state: DappModel) {
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

        webView.addCallback(webViewCallback)

        val appVersionName = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName

        @Suppress("MagicNumber")
        val tonContractMaxMessages = 4 // for wallet v3 and v4

        webView.jsBridge = DAppBridge(
            deviceInfo = JsonBuilder.device(tonContractMaxMessages, appVersionName).toString(),
            send = viewModel::send,
            connect = viewModel::connect,
            restoreConnection = { viewModel.restoreConnection(webView.url) },
            disconnect = viewModel::disconnect,
            tonapiFetch = { _, _ -> Response.Builder().build() }, // api::tonapiFetch,
        )

        state.url?.let {
            webView.loadUrl(it)
        }
    }

    private fun back() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            dismiss()
        }
    }

    companion object {
        const val PAYLOAD_DAPP_KEY = "payload_dapp_key"

        fun getBundle(dapp: DappModel) = bundleOf(PAYLOAD_DAPP_KEY to dapp)
    }
}
