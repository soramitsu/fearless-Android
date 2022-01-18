package jp.co.soramitsu.feature_account_impl.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presenatation.actions.copyAddressClicked
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_profile.aboutTv
import kotlinx.android.synthetic.main.fragment_profile.accountView
import kotlinx.android.synthetic.main.fragment_profile.changePinCodeTv
import kotlinx.android.synthetic.main.fragment_profile.languageWrapper
import kotlinx.android.synthetic.main.fragment_profile.profileWallets
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

        profileWallets.setOnClickListener { viewModel.walletsClicked() }
        languageWrapper.setOnClickListener { viewModel.languagesClicked() }
        changePinCodeTv.setOnClickListener { viewModel.changePinCodeClicked() }
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
        observeBrowserEvents(viewModel)

        viewModel.selectedAccountLiveData.observe { account ->
            account.name?.let(accountView::setTitle)

            selectedNetworkTv.text = account.network.name
        }

        viewModel.accountIconLiveData.observe {
            //todo make avatar dynamic
            val avatar = ContextCompat.getDrawable(requireContext(), R.drawable.ic_wallet_avatar) ?: return@observe
            accountView.setAccountIcon(avatar)
        }

        viewModel.selectedLanguageLiveData.observe {
            selectedLanguageTv.text = it.displayName
        }

        viewModel.showExternalActionsEvent.observeEvent(::showAccountActions)

        viewModel.totalBalanceLiveData.observe {
            accountView.setText(it)
        }
    }

    private fun showAccountActions(payload: ExternalAccountActions.Payload) {
        ProfileActionsSheet(
            requireContext(),
            payload,
            viewModel::copyAddressClicked,
            viewModel::viewExternalClicked,
            viewModel::walletsClicked
        ).show()
    }
}
