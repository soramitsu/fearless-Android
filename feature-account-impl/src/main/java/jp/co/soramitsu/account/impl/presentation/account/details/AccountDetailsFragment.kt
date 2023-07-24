package jp.co.soramitsu.account.impl.presentation.account.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import coil.ImageLoader
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.copyAddressClicked
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.PLAY_MARKET_BROWSER_URI
import jp.co.soramitsu.common.base.BaseComposeBottomSheetDialogFragment
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_account_impl.R

const val ACCOUNT_ID_KEY = "ACCOUNT_ADDRESS_KEY"

@AndroidEntryPoint
class AccountDetailsDialog : BaseComposeBottomSheetDialogFragment<AccountDetailsViewModel>(), ChainAccountsAdapter.Handler {

    @Inject lateinit var imageLoader: ImageLoader

//    private val binding by viewBinding(FragmentAccountDetailsBinding::bind)

    override val viewModel: AccountDetailsViewModel by viewModels()

    private val adapter by lazy(LazyThreadSafetyMode.NONE) {
        ChainAccountsAdapter(this, imageLoader)
    }

    companion object {

        fun getBundle(metaAccountId: Long): Bundle {
            return Bundle().apply {
                putLong(ACCOUNT_ID_KEY, metaAccountId)
            }
        }
    }

    @Composable
    override fun Content(padding: PaddingValues) {
        val state by viewModel.state.collectAsState()
        BottomSheetScreen {
            AccountDetailsContent(
                state = state,
                callback = viewModel
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeBrowserEvents(viewModel)

//        binding.accountDetailsNameField.content.bindTo(viewModel.accountNameFlow, viewLifecycleOwner.lifecycleScope)

//        viewModel.chainAccountProjections.observe { adapter.submitList(it) }

        viewModel.showExternalActionsEvent.observeEvent(::showAccountActions)
        viewModel.showExportSourceChooser.observeEvent(::showExportSourceChooser)
        viewModel.showImportChainAccountChooser.observeEvent(::showImportChainAccountChooser)
        viewModel.showUnsupportedChainAlert.observeEvent { showUnsupportedChainAlert() }
        viewModel.openPlayMarket.observeEvent { openPlayMarket() }

    }

//    override fun initViews() {
//        with(binding) {
//            accountDetailsToolbar.setHomeButtonListener {
//                viewModel.backClicked()
//            }
//
//            accountDetailsNameField.content.filters = nameInputFilters()
//            accountDetailsChainAccounts.setHasFixedSize(true)
//            accountDetailsChainAccounts.adapter = adapter
//        }
//    }

//    override fun subscribe(viewModel: AccountDetailsViewModel) {
//        observeBrowserEvents(viewModel)
//
////        binding.accountDetailsNameField.content.bindTo(viewModel.accountNameFlow, viewLifecycleOwner.lifecycleScope)
//
////        viewModel.chainAccountProjections.observe { adapter.submitList(it) }
//
//        viewModel.showExternalActionsEvent.observeEvent(::showAccountActions)
//        viewModel.showExportSourceChooser.observeEvent(::showExportSourceChooser)
//        viewModel.showImportChainAccountChooser.observeEvent(::showImportChainAccountChooser)
//        viewModel.showUnsupportedChainAlert.observeEvent { showUnsupportedChainAlert() }
//        viewModel.openPlayMarket.observeEvent { openPlayMarket() }
//    }

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
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_APP_URI)))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_BROWSER_URI)))
        }
    }

    override fun chainAccountClicked(item: AccountInChainUi) {
        viewModel.chainAccountClicked(item)
    }

    override fun chainAccountOptionsClicked(item: AccountInChainUi) {
        viewModel.chainAccountOptionsClicked(item)
    }

    private fun showAccountActions(payload: ExternalAccountActions.Payload) {
        WalletAccountActionsSheet(
            context = requireContext(),
            content = payload,
            onCopy = viewModel::copyAddressClicked,
            onReplace = viewModel::showImportChainAccountChooser,
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

    private fun showImportChainAccountChooser(payload: ImportChainAccountsPayload) {
        ImportChainAccountActionsSheet(
            context = requireContext(),
            payload = payload,
            onCreateAccount = viewModel::createChainAccount,
            onImportAccount = viewModel::importChainAccount
        ).show()
    }
}
