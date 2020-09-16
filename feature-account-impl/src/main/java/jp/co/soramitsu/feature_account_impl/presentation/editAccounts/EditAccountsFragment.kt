package jp.co.soramitsu.feature_account_impl.presentation.editAccounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.common.accountManagment.AccountModel
import kotlinx.android.synthetic.main.fragment_accounts.accountsList
import kotlinx.android.synthetic.main.fragment_accounts.addAccount
import kotlinx.android.synthetic.main.fragment_accounts.fearlessToolbar

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

    override fun subscribe(viewModel: EditAccountsViewModel) {
        viewModel.accountListingLiveData.observe(adapter::submitListing)
    }

    override fun deleteClicked(accountModel: AccountModel) {
        viewModel.deleteClicked(accountModel)
    }
}