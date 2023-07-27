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
        val googleSignInStatus = result.data?.extras?.get("googleSignInStatus")
        println("!!! WelcomeFragment GoogleLogin result: $googleSignInStatus ")
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.onGoogleLoginError(googleSignInStatus.toString())
        } else {
            viewModel.openAddWalletThroughGoogleScreen()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
        observeBrowserEvents(viewModel)
    }
    fun initViews() {
//        configureTermsAndPrivacy(
//            getString(R.string.onboarding_terms_and_conditions_1),
//            getString(R.string.onboarding_terms_and_conditions_2),
//            getString(R.string.onboarding_privacy_policy)
//        )

        viewModel.events
            .onEach(::handleEvents)
            .launchIn(viewLifecycleOwner.lifecycleScope)

//        with(binding) {
//            termsTv.movementMethod = LinkMovementMethod.getInstance()
//            termsTv.highlightColor = Color.TRANSPARENT
//        }
    }

    private fun handleEvents(event: WelcomeEvent) {
        when (event) {
            WelcomeEvent.AuthorizeGoogle -> handleAuthorizeGoogleEvent()
        }
    }

    private fun handleAuthorizeGoogleEvent() {
        viewModel.authorizeGoogle(launcher = launcher)
    }

//    private fun configureTermsAndPrivacy(sourceText: String, terms: String, privacy: String) {
//        binding.termsTv.text = createSpannable(sourceText) {
//            clickable(terms) {
//                viewModel.termsClicked()
//            }
//
//            clickable(privacy) {
//                viewModel.privacyClicked()
//            }
//        }
//    }

    @ExperimentalMaterialApi
    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState, modalBottomSheetState: ModalBottomSheetState) {
        val state by viewModel.state.collectAsState()

        FearlessAppTheme {
            WelcomeScreen(
                state = state,
//                scrollState = scrollState,
                callbacks = viewModel
            )
        }
    }
}
