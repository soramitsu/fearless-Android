package jp.co.soramitsu.feature_account_impl.presentation.editAccounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.common.accountManagment.AccountModel
import kotlinx.android.synthetic.main.fragment_edit_accounts.accountsList
import kotlinx.android.synthetic.main.fragment_edit_accounts.fearlessToolbar

class EditAccountsFragment : BaseFragment<EditAccountsViewModel>(), EditAccountsAdapter.EditAccountItemHandler {
    private lateinit var adapter: EditAccountsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_edit_accounts, container, false)

    override fun initViews() {
        accountsList.setHasFixedSize(true)

        adapter = EditAccountsAdapter(this)
        accountsList.adapter = adapter

        fearlessToolbar.setRightActionClickListener {
            viewModel.backClicked()
        }

        fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }
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

    override fun subscribe(viewModel: EditAccountsViewModel) {
        viewModel.accountListingLiveData.observe(adapter::submitListing)

        viewModel.deleteConfirmationLiveData.observeEvent(::showDeleteConfirmation)
    }

    private fun showDeleteConfirmation(account: AccountModel) {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.account_delete_confirmation_title)
            .setMessage(R.string.account_delete_confirmation_description)
            .setPositiveButton(R.string.account_delete_confirm) { _, _ ->
                viewModel.deleteConfirmed(account)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun deleteClicked(accountModel: AccountModel) {
        viewModel.deleteClicked(accountModel)
    }
}