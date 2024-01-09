package jp.co.soramitsu.app.root.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ProgressDialog
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme

class WebViewerFragment : BottomSheetDialogFragment() {

    companion object {
        const val TITLE_TAG = "title"
        const val URL_TAG = "url"

        fun getBundle(title: String, url: String) = bundleOf(TITLE_TAG to title, URL_TAG to url)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomBottomSheetDialogTheme)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setupBottomSheet()
        val title = requireNotNull(requireArguments().getString(TITLE_TAG))
        val url = requireNotNull(requireArguments().getString(URL_TAG))
        val onBackClick = { dismiss() }

        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    val webViewState = rememberWebViewState(url = url)
                    BottomSheetScreen {
                        Toolbar(state = ToolbarViewState(title, R.drawable.ic_close), onNavigationClick = onBackClick)
                        MarginVertical(margin = 8.dp)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            WebView(
                                state = webViewState,
                                onCreated = {
                                    it.settings.javaScriptEnabled = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )

                            if (webViewState.isLoading) {
                                ProgressDialog()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupBottomSheet() {
        dialog?.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            setupBehavior(bottomSheetDialog.behavior)
        }
    }

    private fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
        behavior.isHideable = false
    }
}
