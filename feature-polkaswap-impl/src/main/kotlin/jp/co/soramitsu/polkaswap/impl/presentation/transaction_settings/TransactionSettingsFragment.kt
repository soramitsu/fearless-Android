package jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel

@AndroidEntryPoint
class TransactionSettingsFragment : BaseComposeFragment<TransactionSettingsViewModel>() {
    companion object {
        const val SETTINGS_MODEL_KEY = "settingsModel"
        fun getBundle(initialSettings: TransactionSettingsModel) = bundleOf(SETTINGS_MODEL_KEY to initialSettings)
    }

    override val viewModel: TransactionSettingsViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        BottomSheetScreen {
            val state by viewModel.state.collectAsState()
            TransactionSettingsContent(
                state = state,
                callbacks = viewModel
            )
        }
    }

}
