package jp.co.soramitsu.soracard.impl.presentation.get

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContract
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult

@AndroidEntryPoint
class GetSoraCardFragment : BaseComposeFragment<GetSoraCardViewModel>() {

    override val viewModel: GetSoraCardViewModel by viewModels()

    private val soraCardRegistration = registerForActivityResult(
        SoraCardContract()
    ) { result ->
        handleSoraCardResult(result)
    }

    private var soraCardSignIn = registerForActivityResult(
        SoraCardContract()
    ) { result ->
        handleSoraCardResult(result)
    }

    private fun handleSoraCardResult(result: SoraCardResult) {
        when (result) {
            is SoraCardResult.Failure -> {}
            is SoraCardResult.Canceled -> {}
            is SoraCardResult.Success -> {
                viewModel.updateSoraCardInfo(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    accessTokenExpirationTime = result.accessTokenExpirationTime,
                    kycStatus = result.status.toString()
                )
            }

            SoraCardResult.Logout -> {}
            is SoraCardResult.NavigateTo -> {}
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.launchSoraCardRegistration.observeEvent { contractData ->
            soraCardRegistration.launch(contractData)
        }

        viewModel.launchSoraCardSignIn.observeEvent { contractData ->
            soraCardSignIn.launch(contractData)
        }
    }

    @ExperimentalMaterialApi
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()

        FearlessAppTheme {
            GetSoraCardScreenWithToolbar(
                state = state,
                scrollState = scrollState,
                callbacks = viewModel
            )
        }
    }
}
