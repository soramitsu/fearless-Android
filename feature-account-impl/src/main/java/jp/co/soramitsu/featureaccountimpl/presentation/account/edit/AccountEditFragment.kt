package jp.co.soramitsu.featureaccountimpl.presentation.account.edit

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.dragAndDropItemTouchHelper
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentEditAccountsBinding
import jp.co.soramitsu.featureaccountimpl.presentation.account.model.LightMetaAccountUi

@AndroidEntryPoint
class AccountEditFragment : BaseFragment<AccountEditViewModel>(R.layout.fragment_edit_accounts), EditAccountsAdapter.EditAccountItemHandler {

    private val binding by viewBinding(FragmentEditAccountsBinding::bind)

    override val viewModel: AccountEditViewModel by viewModels()

    private lateinit var adapter: EditAccountsAdapter

    override fun initViews() {
        binding.accountsList.setHasFixedSize(true)

        val dragHelper = dragAndDropItemTouchHelper(viewModel.dragAndDropDelegate)

        dragHelper.attachToRecyclerView(binding.accountsList)

        adapter = EditAccountsAdapter(this, dragHelper)

        with(binding) {
            accountsList.adapter = adapter

            fearlessToolbar.setRightActionClickListener {
                viewModel.doneClicked()
            }

            fearlessToolbar.setHomeButtonListener {
                viewModel.backClicked()
            }

            addAccount.setOnClickListener { viewModel.addAccountClicked() }
        }
    }

    override fun subscribe(viewModel: AccountEditViewModel) {
        viewModel.accountListingLiveData.observe(adapter::submitList)

        viewModel.deleteConfirmationLiveData.observeEvent(::showDeleteConfirmation)

        viewModel.dragAndDropDelegate.unsyncedSwapLiveData.observe { payload ->
            adapter.submitList(payload)
        }
    }

    private fun showDeleteConfirmation(metaId: Long) {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.account_delete_confirmation_title)
            .setMessage(R.string.account_delete_confirmation_description)
            .setPositiveButton(R.string.account_delete_confirm) { _, _ ->
                viewModel.deleteConfirmed(metaId)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun deleteClicked(accountModel: LightMetaAccountUi) {
        viewModel.deleteClicked(accountModel)
    }
}
