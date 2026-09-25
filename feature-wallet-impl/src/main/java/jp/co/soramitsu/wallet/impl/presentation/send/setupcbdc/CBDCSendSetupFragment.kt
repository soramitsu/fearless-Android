package jp.co.soramitsu.wallet.impl.presentation.send.setupcbdc

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import java.lang.Integer.max
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.wallet.impl.domain.model.QrContentCBDC
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogContract
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogCoordinator
import jp.co.soramitsu.wallet.impl.presentation.bindTransferValidationDialog

@AndroidEntryPoint
class CBDCSendSetupFragment : BaseComposeBottomSheetDialogFragment<CBDCSendSetupViewModel>() {

    companion object {
        const val KEY_CBDC_INFO = "KEY_CBDC_INFO"
        fun getBundle(cbdcQrInfo: QrContentCBDC) = bundleOf(
            KEY_CBDC_INFO to cbdcQrInfo,
        )
    }

    override val viewModel: CBDCSendSetupViewModel by viewModels()
    private val validationDialogCoordinator:
        TransferValidationDialogCoordinator by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        CBCDSendSetupContent(
            state = state,
            callback = viewModel
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            // r will be populated with the coordinates of your view that area still visible.
            view.getWindowVisibleDisplayFrame(r)
            val heightDiff: Int = view.rootView.height - (r.bottom - r.top)

            // if more than 100 pixels, its probably a keyboard...
            viewModel.setSoftKeyboardOpen(heightDiff > 500)
        }

        childFragmentManager.bindTransferValidationDialog(
            TransferValidationDialogContract.CBDC_SEND_SETUP,
            validationDialogCoordinator,
            viewLifecycleOwner,
            onPositive = viewModel::warningConfirmed
        )
        viewModel.openValidationWarningEvent.observeEvent { (result, warning) ->
            validationDialogCoordinator.enqueue(
                TransferValidationDialogContract.CBDC_SEND_SETUP,
                result,
                warning
            )
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
