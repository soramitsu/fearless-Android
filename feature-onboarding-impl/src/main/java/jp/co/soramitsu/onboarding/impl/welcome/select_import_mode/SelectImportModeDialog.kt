package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment

@AndroidEntryPoint
class SelectImportModeDialog : BaseComposeBottomSheetDialogFragment<SelectImportModeViewModel>() {

    override val viewModel: SelectImportModeViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        SelectImportModeContent(
            callback = viewModel
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    companion object {

        const val RESULT_IMPORT_MODE = "RESULT_IMPORT_MODE"

        fun getBundle() = bundleOf()
    }
}
