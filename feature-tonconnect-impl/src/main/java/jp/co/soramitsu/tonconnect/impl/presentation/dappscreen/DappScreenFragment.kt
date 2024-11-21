package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.jp.soramitsu.tonconnect.model.BridgeEvent
import co.jp.soramitsu.tonconnect.model.BridgeMethod
import co.jp.soramitsu.tonconnect.model.ConnectRequest
import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.SignRequestEntity
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.CancellationException
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ProgressDialog
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

@AndroidEntryPoint
class DappScreenFragment : BaseComposeBottomSheetDialogFragment<DappScreenViewModel>() {

    companion object {
        const val PAYLOAD_DAPP_KEY = "payload_dapp_key"

        fun getBundle(dapp: DappModel) = bundleOf(PAYLOAD_DAPP_KEY to dapp)
    }

    override val viewModel: DappScreenViewModel by viewModels()

    private lateinit var webView: BridgeWebView
    private var isLoading = false

    private val webViewCallback = object : WebViewFixed.Callback() {
        override fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean {
            val refererUri = request.requestHeaders?.get("Referer")?.toUri()
//            val url = request.url.normalizeTONSites()
            val scheme = request.url.scheme ?: ""
            if (scheme == "https") {
                return false
            }
//            if (rootViewModel.processDeepLink(url, false, refererUri)) {
//                return true
//            }
//            navigation?.openURL(url.toString())
            return true
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
                            webView = BridgeWebView(context)
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
        println("!!! DappScreenFragment webViewSetup appVersionName = $appVersionName")

        val tonContractMaxMessages = 4 /* for wallet v3 and v4 */

        webView.jsBridge = DAppBridge(
            deviceInfo = JsonBuilder.device(tonContractMaxMessages, appVersionName).toString(),
            send = ::send,
            connect = ::tonconnect,
            restoreConnection = { JSONObject() }, //viewModel::restoreConnection,
            disconnect = {
//                                            viewModel.disconnect()
            },
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

    private suspend fun send(array: JSONArray): JSONObject {
        println("!!! DAppScreen send array = $array")
        val messages = BridgeEvent.Message.parse(array)
        if (messages.size == 1) {
            val message = messages.first()
            val id = message.id
            if (message.method != BridgeMethod.SEND_TRANSACTION) {
                return JsonBuilder.responseError(id, BridgeError.METHOD_NOT_SUPPORTED)
            }
            val signRequests = message.params.map { SignRequestEntity(it) }
            if (signRequests.size != 1) {
                return JsonBuilder.responseError(id, BridgeError.BAD_REQUEST)
            }
            val signRequest = signRequests.first()
            return try {

                val boc = "SendTransactionScreen.run(requireContext(), wallet, signRequest)"
                viewModel.openTonSignRequest(message.method.title, signRequest)

//                JsonBuilder.responseSendTransaction(id, boc)
                JsonBuilder.responseError(id, BridgeError.UNKNOWN)

            } catch (e: CancellationException) {
                JsonBuilder.responseError(id, BridgeError.USER_DECLINED_TRANSACTION)
            } catch (e: BridgeError.Exception) {
                JsonBuilder.responseError(id, e.error)
            } catch (e: Throwable) {
                JsonBuilder.responseError(id, BridgeError.UNKNOWN)
            }
        } else {
            return JsonBuilder.responseError(0, BridgeError.BAD_REQUEST)
        }
    }

    private suspend fun tonconnect(
        version: Int,
        request: ConnectRequest
    ): JSONObject {
        println("!!! tonconnect, request = $request")
        if (version != 2) {
            return JsonBuilder.connectEventError(BridgeError.BAD_REQUEST)
        }

        println("!!! tonconnect, openTonConnectScreen webView.url = ${webView.url}")
        val result = viewModel.openTonConnectScreenForResult(request.manifestUrl, request.proofPayload)

        println("!!! tonconnect, TonConnectScreen result $result")

        return result
//        val activity = requireContext().activity ?: return JsonBuilder.connectEventError(BridgeError.BAD_REQUEST)
//        return tonConnectManager.launchConnectFlow(
//            activity = activity,
//            tonConnect = TonConnect.fromJsInject(request, webView.url?.toUri()),
//            wallet = wallet
//        )
    }

}
