package jp.co.soramitsu.common.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2

class InfoDialog(
    private val title: String,
    private val message: String
) : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "errorDialogTag"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.CustomBottomSheetDialogTheme)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setupBottomSheet()
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    BottomSheetScreen {
                        InfoDialogContent(title, message)
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
        behavior.isDraggable = true
        behavior.isHideable = true
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }
}

@Composable
private fun InfoDialogContent(title: String, message: String) {
    Box {
        Grip(Modifier.align(Alignment.TopCenter))
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            MarginVertical(margin = 34.dp)
            H3(text = title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            MarginVertical(margin = 16.dp)
            B0(
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = black2,
                text = message
            )
            MarginVertical(margin = 24.dp)
        }
    }
}

@Preview
@Composable
private fun InfoDialogContentPreview() {
    FearlessTheme {
        BottomSheetScreen {
            InfoDialogContent("Liquidity provider fee", "A portion of each trade (0.3%) goes to liquidity providers as a protocol incentive.")
        }
    }
}
