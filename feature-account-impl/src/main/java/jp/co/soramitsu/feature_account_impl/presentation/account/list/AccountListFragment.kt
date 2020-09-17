package jp.co.soramitsu.feature_account_impl.presentation.account.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import kotlinx.android.synthetic.main.fragment_accounts.accountsList
import kotlinx.android.synthetic.main.fragment_accounts.addAccount
import kotlinx.android.synthetic.main.fragment_accounts.fearlessToolbar

class AccountListFragment : BaseFragment<AccountListViewModel>(), AccountsAdapter.AccountItemHandler {
    private lateinit var adapter: AccountsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_accounts, container, false)

    override fun initViews() {
        adapter = AccountsAdapter(this)

        accountsList.setHasFixedSize(true)
        accountsList.adapter = adapter

        fearlessToolbar.setRightActionClickListener {
            viewModel.editClicked()
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
            .accountsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: AccountListViewModel) {
        viewModel.accountListingLiveData.observe { adapter.submitList(it.groupedAccounts) }

        viewModel.selectedAccountLiveData.observe(adapter::updateSelectedAccount)
    }

    override fun infoClicked(accountModel: AccountModel) {
        viewModel.infoClicked(accountModel)
    }

    override fun checkClicked(accountModel: AccountModel) {
        viewModel.selectAccountClicked(accountModel)
    }
}