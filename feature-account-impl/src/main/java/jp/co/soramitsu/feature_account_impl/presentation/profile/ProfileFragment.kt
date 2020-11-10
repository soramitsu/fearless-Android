package jp.co.soramitsu.feature_account_impl.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_profile.aboutTv
import kotlinx.android.synthetic.main.fragment_profile.accountView
import kotlinx.android.synthetic.main.fragment_profile.languageWrapper
import kotlinx.android.synthetic.main.fragment_profile.networkWrapper
import kotlinx.android.synthetic.main.fragment_profile.profileAccounts
import kotlinx.android.synthetic.main.fragment_profile.selectedLanguageTv
import kotlinx.android.synthetic.main.fragment_profile.selectedNetworkTv

class ProfileFragment : BaseFragment<ProfileViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun initViews() {
        accountView.setWholeClickListener { viewModel.accountActionsClicked() }

        aboutTv.setOnClickListener { viewModel.aboutClicked() }

        profileAccounts.setOnClickListener { viewModel.accountsClicked() }
        networkWrapper.setOnClickListener { viewModel.networksClicked() }
        languageWrapper.setOnClickListener { viewModel.languagesClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .profileComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ProfileViewModel) {
        viewModel.selectedAccountLiveData.observe { account ->
            account.name?.let(accountView::setTitle)

            accountView.setText(account.address)

            selectedNetworkTv.text = account.network.name
        }

        viewModel.accountIconLiveData.observe {
            accountView.setAccountIcon(it.image)
        }

        viewModel.selectedLanguageLiveData.observe {
            selectedLanguageTv.text = it.displayName
        }

        viewModel.showAccountActionsEvent.observeEvent {
            val address = viewModel.selectedAccountLiveData.value

            address?.let { showAccountActions(it) }
        }

        viewModel.openBrowserEvent.observeEvent(this::showBrowser)
    }

    private fun showAccountActions(account: Account) {
        ProfileActionsSheet(
            requireContext(),
            account.address,
            account.network.type,
            viewModel::addressCopyClicked,
            viewModel::viewExternalClicked,
            viewModel::accountsClicked
        ).show()
    }
}