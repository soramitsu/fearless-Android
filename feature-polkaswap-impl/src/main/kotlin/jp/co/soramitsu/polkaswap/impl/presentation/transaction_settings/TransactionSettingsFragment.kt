package jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings

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
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel

@AndroidEntryPoint
class TransactionSettingsFragment : BaseComposeBottomSheetDialogFragment<TransactionSettingsViewModel>() {
    companion object {
        const val SETTINGS_MODEL_KEY = "settingsModel"
        fun getBundle(initialSettings: TransactionSettingsModel) = bundleOf(SETTINGS_MODEL_KEY to initialSettings)
    }

    override val viewModel: TransactionSettingsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        BottomSheetScreen {
            val state by viewModel.state.collectAsState()
            TransactionSettingsContent(
                state = state,
                callbacks = viewModel
            )
        }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }
}
