package jp.co.soramitsu.account.impl.presentation.nomis_scoring

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen

@AndroidEntryPoint
class ScoreDetailsFragment : BaseComposeBottomSheetDialogFragment<ScoreDetailsViewModel>() {

    companion object {
        fun getBundle(metaId: Long) = bundleOf(META_ACCOUNT_ID_KEY to metaId)

        const val META_ACCOUNT_ID_KEY = "meta_account_id"
    }

    override val viewModel: ScoreDetailsViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsStateWithLifecycle()

        BottomSheetScreen(
            modifier = Modifier.padding(top = 56.dp)
        ) {
            ScoreDetailsContent(state, viewModel)
        }
    }
}