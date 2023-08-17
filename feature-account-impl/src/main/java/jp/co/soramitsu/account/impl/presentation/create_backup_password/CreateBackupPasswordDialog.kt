package jp.co.soramitsu.account.impl.presentation.create_backup_password

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
import jp.co.soramitsu.account.api.presentation.create_backup_password.CreateBackupPasswordPayload
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class CreateBackupPasswordDialog : BaseComposeBottomSheetDialogFragment<CreateBackupPasswordViewModel>() {

    companion object {
        const val PAYLOAD_KEY = "PAYLOAD_KEY"
        const val RESULT_BACKUP_KEY = "RESULT_BACKUP_KEY"

        fun getBundle(payload: CreateBackupPasswordPayload): Bundle {
            return bundleOf(PAYLOAD_KEY to payload)
        }
    }

    override val viewModel: CreateBackupPasswordViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        CreateBackupPasswordContent(
            state = state,
            callback = viewModel
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var constantDiff = 0
        view.postDelayed({
            val w = Rect()
            view.getWindowVisibleDisplayFrame(w)
            constantDiff = view.rootView.height - (w.bottom - w.top)
        }, 100)

        view.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            // r will be populated with the coordinates of your view that area still visible.
            view.getWindowVisibleDisplayFrame(r)
            val heightDiff: Int = view.rootView.height - (r.bottom - r.top)

            context?.let {
                val correctedDiff = Integer.max(heightDiff - constantDiff, 0)
                viewModel.setHeightDiffDp((correctedDiff / Density(it).density).dp)
            }
        }
    }
}
