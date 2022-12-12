package jp.co.soramitsu.common.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AlertSheet
import jp.co.soramitsu.common.compose.component.emptyClick
import jp.co.soramitsu.common.compose.theme.FearlessTheme

class ErrorDialog(
    private val payload: AlertViewState,
    private val isHideable: Boolean = true,
    private val onDialogDismiss: () -> Unit = emptyClick
) :
    BottomSheetDialogFragment() {

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
                    AlertSheet(
                        state = payload,
                        onBackClicked = {
                            onDialogDismiss()
                            dismiss()
                        },
                        onTopUpClicked = {
                            onDialogDismiss()
                            dismiss()
                        }
                    )
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
        behavior.isHideable = isHideable
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }
}
