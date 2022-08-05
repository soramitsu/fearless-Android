package jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts

import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentAccountsForExportBinding
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain
import jp.co.soramitsu.feature_account_impl.presentation.account.details.AccountInChainUi
import jp.co.soramitsu.feature_account_impl.presentation.account.details.ChainAccountsAdapter
import javax.inject.Inject

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

@AndroidEntryPoint
class AccountsForExportFragment : BaseFragment<AccountsForExportViewModel>(R.layout.fragment_accounts_for_export), ChainAccountsAdapter.Handler {

    @Inject
    lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentAccountsForExportBinding::bind)

    @Inject
    lateinit var factory: AccountsForExportViewModel.AccountsForExportViewModelFactory

    private val vm: AccountsForExportViewModel by viewModels {
        AccountsForExportViewModel.provideFactory(
            factory,
            argument(PAYLOAD_KEY)
        )
    }
    override val viewModel: AccountsForExportViewModel
        get() = vm

    private val adapter by lazy(LazyThreadSafetyMode.NONE) {
        ChainAccountsAdapter(this, imageLoader)
    }

    companion object {
        fun getBundle(metaAccountId: Long, from: AccountInChain.From) = bundleOf(PAYLOAD_KEY to AccountsForExportPayload(metaAccountId, from))
    }

    override fun initViews() {
        with(binding) {
            accountsForExportToolbar.setHomeButtonListener {
                viewModel.backClicked()
            }

            accountsForExport.setHasFixedSize(true)
            accountsForExport.adapter = adapter

            exportBtn.setOnClickListener {
                viewModel.onExportClick()
            }
        }
    }

    override fun subscribe(viewModel: AccountsForExportViewModel) {
        viewModel.chainAccountProjections.observe { adapter.submitList(it) }
        viewModel.showExportSourceChooser.observeEvent(::showExportSourceChooser)
    }

    private fun showExportSourceChooser(payload: ExportSourceChooserPayload) {
        SourceTypeChooserBottomSheetDialog(
            titleRes = R.string.select_save_type,
            context = requireActivity(),
            payload = DynamicListBottomSheet.Payload(payload.sources),
            onClicked = { viewModel.exportTypeSelected(it, payload.chainId) }
        )
            .show()
    }

    override fun chainAccountClicked(item: AccountInChainUi) {
    }

    override fun chainAccountOptionsClicked(item: AccountInChainUi) {
    }
}
