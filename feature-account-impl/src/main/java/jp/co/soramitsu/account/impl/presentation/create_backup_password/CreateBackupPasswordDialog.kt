package jp.co.soramitsu.account.impl.presentation.create_backup_password

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.create_backup_password.SaveBackupPayload
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class CreateBackupPasswordDialog : BaseComposeBottomSheetDialogFragment<CreateBackupPasswordViewModel>() {

    companion object {
        const val PAYLOAD_KEY = "PAYLOAD_KEY"
        const val RESULT_BACKUP_KEY = "RESULT_BACKUP_KEY"

        /** The wallet was created here but not backed up; the caller continues with it. */
        const val RESULT_BACKUP_SKIPPED = Activity.RESULT_FIRST_USER

        fun getBundle(payload: SaveBackupPayload?): Bundle {
            return bundleOf(PAYLOAD_KEY to payload)
        }
    }

    override val viewModel: CreateBackupPasswordViewModel by viewModels()

    private val launcher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // The launcher also opens Drive permission screens, whose RESULT_OK carries no sign-in account.
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onGoogleAuthorized()
        } else {
            viewModel.onGoogleSignInFailed(GoogleSignInResult.parse(result.resultCode, result.data))
        }
    }

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
        behavior.isHideable = !viewModel.isWalletCreated.value
        behavior.skipCollapsed = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.requestGoogleConsent.onEach {
            it.getContentIfNotHandled()?.let { launcher.launch(it) }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.requestGoogleSignIn.onEach {
            it.getContentIfNotHandled()?.let { viewModel.signInToGoogle(launcher) }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        // A swipe would drop the sheet without a result and leave the created wallet unknown to the
        // caller, so once it exists the sheet is left through back or "Back up later".
        viewModel.isWalletCreated.onEach { isWalletCreated ->
            isCancelable = !isWalletCreated
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }
}
