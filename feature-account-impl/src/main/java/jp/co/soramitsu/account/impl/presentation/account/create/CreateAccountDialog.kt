package jp.co.soramitsu.account.impl.presentation.account.create

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.feature_account_impl.R

@AndroidEntryPoint
class CreateAccountDialog : BaseComposeBottomSheetDialogFragment<CreateAccountViewModel>() {
    companion object {

        const val IS_FROM_GOOGLE_BACKUP_KEY = "IS_FROM_GOOGLE_BACKUP_KEY"

        fun getBundle(isFromGoogleBackup: Boolean): Bundle {
            return bundleOf(IS_FROM_GOOGLE_BACKUP_KEY to isFromGoogleBackup)
        }
    }

    override val viewModel: CreateAccountViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        BottomSheetScreen {
            CreateAccountDialogContent(
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

        viewModel.showScreenshotsWarningEvent.observeEvent {
            showScreenshotWarningDialog()
        }
    }

    private fun showScreenshotWarningDialog() {
        val res = requireContext()
        ErrorDialog(
            title = res.getString(R.string.common_no_screenshot_title),
            message = res.getString(R.string.common_no_screenshot_message),
            positiveButtonText = res.getString(R.string.common_ok),
            positiveClick = viewModel::screenshotWarningConfirmed
        ).show(childFragmentManager)
    }
}
