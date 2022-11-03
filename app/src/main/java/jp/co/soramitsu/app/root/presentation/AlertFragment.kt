package jp.co.soramitsu.app.root.presentation

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.app.root.navigation.setNavigationResult
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.navigation.setNavigationResult

@AndroidEntryPoint
class AlertFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var navigator: Navigator

    companion object {
        const val KEY_PAYLOAD = "payload"
        const val KEY_RESULT = "result"

        fun getBundle(payload: AlertViewState) = bundleOf(KEY_PAYLOAD to payload)
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
        val payload = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(KEY_PAYLOAD, AlertViewState::class.java)
        } else {
            requireArguments().getParcelable(KEY_PAYLOAD)
        }
        val state = requireNotNull(payload)
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    AlertSheet(
                        state = state,
                        onBackClicked = {
                            this@AlertFragment.setNavigationResult<Result<Unit>>(KEY_RESULT, Result.failure(Exception()))
                            dismiss()
                        },
                        onTopUpClicked = {
                            this@AlertFragment.setNavigationResult(KEY_RESULT, Result.success(Unit))
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
        behavior.isHideable = true
    }
}
