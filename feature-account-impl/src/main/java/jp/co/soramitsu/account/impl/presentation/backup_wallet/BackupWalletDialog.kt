package jp.co.soramitsu.account.impl.presentation.backup_wallet

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
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen

@AndroidEntryPoint
class BackupWalletDialog : BaseComposeBottomSheetDialogFragment<BackupWalletViewModel>() {

    companion object {

        const val ACCOUNT_ID_KEY = "ACCOUNT_ID_KEY"

        fun getBundle(metaAccountId: Long): Bundle {
            return Bundle().apply {
                putLong(ACCOUNT_ID_KEY, metaAccountId)
            }
        }
    }

    override val viewModel: BackupWalletViewModel by viewModels()

    private val launcher: ActivityResultLauncher<Intent> by lazy {
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> viewModel.onGoogleSignInSuccess()
                Activity.RESULT_CANCELED -> { /* no action */ }
                else -> {
                    val googleSignInStatus = result.data?.extras?.get("googleSignInStatus")
                    viewModel.onGoogleLoginError(googleSignInStatus.toString())
                }
            }
        }
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        BottomSheetScreen {
            BackupWalletContent(
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

        viewModel.authorizeGoogle(launcher = launcher)
    }
}
