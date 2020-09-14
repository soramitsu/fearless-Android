package jp.co.soramitsu.feature_account_impl.presentation.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.accounts.model.AccountModel
import kotlinx.android.synthetic.main.fragment_accounts.accountsList
import kotlinx.android.synthetic.main.fragment_accounts.addAccount
import kotlinx.android.synthetic.main.fragment_accounts.fearlessToolbar

class AccountsFragment : BaseFragment<AccountsViewModel>(), AccountsAdapter.AccountItemHandler {
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

        fearlessToolbar.setAction(R.string.common_edit) {
            viewModel.editClicked()
        }

        fearlessToolbar.showBackButton {
            viewModel.backClicked()
        }

        fearlessToolbar.setTitle(R.string.profile_accounts_title)

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

    override fun subscribe(viewModel: AccountsViewModel) {
        viewModel.groupedAccountModelsLiveData.observe(adapter::submitList)

        viewModel.selectedAccountLiveData.observe(adapter::updateSelectedAccount)
    }

    override fun infoClicked(accountModel: AccountModel) {
        viewModel.infoClicked(accountModel)
    }

    override fun checkClicked(accountModel: AccountModel) {
        viewModel.selectAccountClicked(accountModel)
    }
}