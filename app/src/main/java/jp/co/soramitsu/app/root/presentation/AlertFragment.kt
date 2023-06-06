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
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AlertSheet
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import kotlin.coroutines.cancellation.CancellationException

const val emptyResultKey = ""

@AndroidEntryPoint
class AlertFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var navigator: Navigator

    companion object {
        const val KEY_PAYLOAD = "payload"
        private const val KEY_RESULT = "result"
        private const val KEY_RESULT_DESTINATION = "result_destination"

        fun getBundle(payload: AlertViewState, resultKey: String, resultDestinationId: Int) =
            bundleOf(KEY_PAYLOAD to payload, KEY_RESULT to resultKey, KEY_RESULT_DESTINATION to resultDestinationId)
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
        val resultKey = requireNotNull(requireArguments().getString(KEY_RESULT))
        val resultDestinationId = requireNotNull(requireArguments().getInt(KEY_RESULT_DESTINATION))
        val state = requireNotNull(payload)
        return ComposeView(requireContext()).apply {
            setContent {
                FearlessTheme {
                    AlertSheet(
                        state = state,
                        onBackClicked = {
                            navigator.setAlertResult(resultKey, Result.failure<Unit>(Exception()), resultDestinationId)
                            dismiss()
                        },
                        onTopUpClicked = {
                            navigator.setAlertResult(resultKey, Result.success(Unit), resultDestinationId)
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
        dialog?.setOnCancelListener {
            val resultKey = requireNotNull(requireArguments().getString(KEY_RESULT))
            val resultDestinationId = requireNotNull(requireArguments().getInt(KEY_RESULT_DESTINATION))
            navigator.setAlertResult(resultKey, Result.failure<Unit>(CancellationException()), resultDestinationId)
        }
    }

    private fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
        behavior.isHideable = true
    }
}
