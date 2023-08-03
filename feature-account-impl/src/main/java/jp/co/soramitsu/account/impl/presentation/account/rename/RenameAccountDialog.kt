package jp.co.soramitsu.account.impl.presentation.account.rename

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
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen

@AndroidEntryPoint
class RenameAccountDialog : BaseComposeBottomSheetDialogFragment<RenameAccountViewModel>() {
    companion object {

        const val WALLET_ID_KEY = "WALLET_ID_KEY"

        fun getBundle(walletId: Long): Bundle {
            return bundleOf(WALLET_ID_KEY to walletId)
        }
    }

    override val viewModel: RenameAccountViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        BottomSheetScreen {
            RenameAccountDialogContent(
                state = state,
                callback = viewModel
            )
        }
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
