package jp.co.soramitsu.account.impl.presentation.profile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.copyAddressClicked
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.theme.black
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet

@AndroidEntryPoint
class ProfileFragment : BaseComposeFragment<ProfileViewModel>() {

    @Inject
    lateinit var imageLoader: ImageLoader

    override val viewModel: ProfileViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        LaunchedEffect(Unit) {
            subscribe(viewModel)
        }
        val state by viewModel.state.collectAsState()
        ProfileScreen(state = state, callback = viewModel)
    }

    @Composable
    override fun Background() {
        Box(modifier = Modifier.fillMaxSize().background(black))
    }

    fun subscribe(viewModel: ProfileViewModel) {
        observeBrowserEvents(viewModel)

        viewModel.showExternalActionsEvent.observeEvent(::showAccountActions)

        viewModel.showFiatChooser.observeEvent(::showFiatChooser)
    }

    private fun showFiatChooser(payload: DynamicListBottomSheet.Payload<FiatCurrency>) {
        FiatCurrenciesChooserBottomSheetDialog(
            requireContext(),
            imageLoader,
            payload,
            viewModel::onFiatSelected
        ).show()
    }

    private fun showAccountActions(payload: ExternalAccountActions.Payload) {
        ProfileActionsSheet(
            requireContext(),
            payload,
            viewModel::copyAddressClicked,
            viewModel::viewExternalClicked,
            viewModel::walletsClicked
        ).show()
    }
}
