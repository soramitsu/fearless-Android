package jp.co.soramitsu.soracard.impl.presentation.get

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContract

@AndroidEntryPoint
class GetSoraCardFragment : BaseComposeFragment<GetSoraCardViewModel>() {

    override val viewModel: GetSoraCardViewModel by viewModels()

    private val soraCardRegistration = registerForActivityResult(
        SoraCardContract()
    ) { viewModel.handleSoraCardResult(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.launchSoraCardRegistration.observe { contractData ->
            soraCardRegistration.launch(contractData)
        }
    }

    @ExperimentalMaterialApi
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState,
    ) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        FearlessAppTheme {
            GetSoraCardScreenWithToolbar(
                state = state,
                scrollState = scrollState,
                callbacks = viewModel,
            )
        }
    }
}
