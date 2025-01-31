package jp.co.soramitsu.account.impl.presentation.options_ecosystem_accounts

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
import jp.co.soramitsu.common.model.WalletEcosystem

@AndroidEntryPoint
class OptionsEcosystemAccountsFragment : BaseComposeBottomSheetDialogFragment<OptionsEcosystemAccountsViewModel>() {

    companion object {
        const val KEY_META_ID = "key_meta_id"
        const val KEY_TYPE = "key_type"

        fun getBundle(metaId: Long, type: WalletEcosystem) = bundleOf(
            KEY_META_ID to metaId,
            KEY_TYPE to type
        )
    }

    override val viewModel: OptionsEcosystemAccountsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        OptionsEcosystemAccountsContent(
            state = state,
            onBackupEcosystemAccountsClicked = viewModel::onBackupEcosystemAccountsClicked,
            onEcosystemAccountsClicked = viewModel::onEcosystemAccountsClicked,
            onBackClicked = viewModel::onBackClicked
        )
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
    }
}
