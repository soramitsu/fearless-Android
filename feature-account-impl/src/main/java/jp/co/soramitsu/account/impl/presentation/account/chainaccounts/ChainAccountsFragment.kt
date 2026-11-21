package jp.co.soramitsu.account.impl.presentation.account.chainaccounts

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.copyAddressClicked
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.account.impl.presentation.account.details.WalletAccountActionsSheet
import jp.co.soramitsu.common.openPlayMarketPage
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_account_impl.R

const val ACCOUNT_ID_KEY = "ACCOUNT_ID_KEY"
const val ACCOUNT_TYPE_KEY = "ACCOUNT_TYPE_KEY"

@AndroidEntryPoint
class ChainAccountsDialog : BaseComposeBottomSheetDialogFragment<ChainAccountsViewModel>(), ChainAccountsAdapter.Handler {

    override val viewModel: ChainAccountsViewModel by viewModels()

    companion object {
        fun getBundle(metaAccountId: Long, type: WalletEcosystem) = bundleOf(
            ACCOUNT_ID_KEY to metaAccountId,
            ACCOUNT_TYPE_KEY to type
        )
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        BottomSheetScreen {
            ChainAccountsContent(
                state = state,
                callback = viewModel
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeBrowserEvents(viewModel)

        viewModel.showExternalActionsEvent.observeEvent(::showAccountActions)
        viewModel.showExportSourceChooser.observeEvent(::showExportSourceChooser)
        viewModel.showUnsupportedChainAlert.observeEvent { showUnsupportedChainAlert() }
        viewModel.openPlayMarket.observeEvent { openPlayMarket() }
    }

    override fun setupBehavior(behavior: BottomSheetBehavior<FrameLayout>) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isHideable = true
        behavior.skipCollapsed = true
    }

    private fun showUnsupportedChainAlert() {
        AlertBottomSheet.Builder(requireContext())
            .setTitle(R.string.update_needed_text)
            .setMessage(R.string.chain_unsupported_text)
            .setButtonText(R.string.common_update)
            .callback { viewModel.updateAppClicked() }
            .build()
            .show()
    }

    private fun openPlayMarket() {
        requireContext().openPlayMarketPage()
    }

    override fun chainAccountClicked(item: AccountInChainUi) {
        viewModel.chainAccountClicked(item)
    }

    override fun chainAccountOptionsClicked(item: AccountInChainUi) {
        viewModel.chainAccountOptionsClicked(item)
    }

    private fun showAccountActions(payload: ExternalAccountActions.Payload) {
        require(payload is WalletAccountActionsSheet.Payload)
        WalletAccountActionsSheet(
            context = requireContext(),
            content = payload,
            onCopy = viewModel::copyAddressClicked,
            onExternalView = viewModel::viewExternalClicked,
            onExportAccount = viewModel::exportClicked,
            onSwitchNode = viewModel::switchNode
        ).show()
    }

    private fun showExportSourceChooser(payload: ExportSourceChooserPayload) {
        SourceTypeChooserBottomSheetDialog(
            titleRes = R.string.select_save_type,
            context = requireActivity(),
            payload = DynamicListBottomSheet.Payload(payload.sources),
            onClicked = { viewModel.exportTypeSelected(it, payload.chainId) }
        ).show()
    }
}
