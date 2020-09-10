package jp.co.soramitsu.feature_account_impl.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_profile.aboutTv
import kotlinx.android.synthetic.main.fragment_profile.accountView
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
        accountView.setOnCopyClickListener { viewModel.addressCopyClicked() }
        accountView.setOnClickListener { viewModel.accountViewClicked() }
        aboutTv.setOnClickListener { viewModel.aboutClicked() }
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
        viewModel.account.observe {
            accountView.setAccountName(it.name)
        }

        viewModel.shortenAddress.observe {
            accountView.setAccountAddress(it)
        }

        viewModel.accountIconLiveData.observe {
            accountView.setAccountIcon(it)
        }

        viewModel.selectedNetworkLiveData.observe {
            selectedNetworkTv.text = it
        }

        viewModel.selectedLanguageLiveData.observe {
            selectedLanguageTv.text = it
        }
    }
}