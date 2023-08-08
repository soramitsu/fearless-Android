package jp.co.soramitsu.onboarding.impl.welcome

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class WelcomeFragment : BaseComposeFragment<WelcomeViewModel>() {

    companion object {
        const val KEY_PAYLOAD = "key_payload"

        fun getBundle(
            displayBack: Boolean,
            chainAccountData: ChainAccountCreatePayload? = null
        ): Bundle {
            return bundleOf(
                KEY_PAYLOAD to WelcomeFragmentPayload(displayBack, chainAccountData)
            )
        }
    }

    override val viewModel: WelcomeViewModel by viewModels()
    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> viewModel.openAddWalletThroughGoogleScreen()
            Activity.RESULT_CANCELED -> { /* no action */ }
            else -> {
                val googleSignInStatus = result.data?.extras?.get("googleSignInStatus")
                viewModel.onGoogleLoginError(googleSignInStatus.toString())
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeBrowserEvents(viewModel)

        viewModel.events
            .onEach(::handleEvents)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun handleEvents(event: WelcomeEvent) {
        when (event) {
            WelcomeEvent.AuthorizeGoogle -> handleAuthorizeGoogleEvent()
        }
    }

    private fun handleAuthorizeGoogleEvent() {
        viewModel.authorizeGoogle(launcher = launcher)
    }

    @ExperimentalMaterialApi
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()

        FearlessAppTheme {
            WelcomeScreen(
                state = state,
                callbacks = viewModel
            )
        }
    }
}
