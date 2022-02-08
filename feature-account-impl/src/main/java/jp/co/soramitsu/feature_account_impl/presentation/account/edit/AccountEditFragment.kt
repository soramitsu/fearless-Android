package jp.co.soramitsu.feature_account_impl.presentation.account.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.dragAndDropItemTouchHelper
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.account.model.LightMetaAccountUi
import kotlinx.android.synthetic.main.fragment_accounts.addAccount
import kotlinx.android.synthetic.main.fragment_edit_accounts.accountsList
import kotlinx.android.synthetic.main.fragment_edit_accounts.fearlessToolbar

class AccountEditFragment : BaseFragment<AccountEditViewModel>(), EditAccountsAdapter.EditAccountItemHandler {
    private lateinit var adapter: EditAccountsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_edit_accounts, container, false)

    override fun initViews() {
        accountsList.setHasFixedSize(true)

        val dragHelper = dragAndDropItemTouchHelper(viewModel.dragAndDropDelegate)

        dragHelper.attachToRecyclerView(accountsList)

        adapter = EditAccountsAdapter(this, dragHelper)
        accountsList.adapter = adapter

        fearlessToolbar.setRightActionClickListener {
            viewModel.doneClicked()
        }

        fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        addAccount.setOnClickListener { viewModel.addAccountClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .editAccountsComponentFactory()
            .create(this)
            .inject(this)
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
