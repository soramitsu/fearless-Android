package jp.co.soramitsu.feature_account_impl.presentation.account.list

import android.os.Bundle
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentAccountsBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.account.model.LightMetaAccountUi

private const val ARG_DIRECTION = "ARG_DIRECTION"

class AccountListFragment : BaseFragment<AccountListViewModel>(R.layout.fragment_accounts), AccountsAdapter.AccountItemHandler {
    private lateinit var adapter: AccountsAdapter

    private val binding by viewBinding(FragmentAccountsBinding::bind)

    companion object {

        fun getBundle(accountChosenNavDirection: AccountChosenNavDirection) = Bundle().apply {
            putSerializable(ARG_DIRECTION, accountChosenNavDirection)
        }
    }

    override fun initViews() {
        adapter = AccountsAdapter(this)

        with(binding) {
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
    }

    override fun inject() {
        val accountChosenNavDirection = argument<AccountChosenNavDirection>(ARG_DIRECTION)

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .accountsComponentFactory()
            .create(this, accountChosenNavDirection)
            .inject(this)
    }

    override fun subscribe(viewModel: AccountListViewModel) {
        viewModel.accountsFlow.observe { adapter.submitList(it) }

        viewModel.openWalletOptionsEvent.observeEvent { metaAccountId ->
            WalletOptionsBottomSheet(
                context = requireContext(),
                metaAccountId = metaAccountId,
                onViewWallet = viewModel::openWalletDetails,
                onExportWallet = viewModel::openExportWallet
            ).show()
        }
    }

    override fun optionsClicked(accountModel: LightMetaAccountUi) {
        viewModel.optionsClicked(accountModel)
    }

    override fun checkClicked(accountModel: LightMetaAccountUi) {
        viewModel.selectAccountClicked(accountModel)
    }
}
