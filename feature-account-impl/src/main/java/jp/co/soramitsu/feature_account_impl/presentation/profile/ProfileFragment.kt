package jp.co.soramitsu.feature_account_impl.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_profile.accountAddressText
import kotlinx.android.synthetic.main.fragment_profile.accountIcon
import kotlinx.android.synthetic.main.fragment_profile.accountTitle
import kotlinx.android.synthetic.main.fragment_profile.copyIcon
import kotlinx.android.synthetic.main.fragment_profile.selectedLanguageTv
import kotlinx.android.synthetic.main.fragment_profile.selectedNetworkTv

class ProfileFragment : BaseFragment<ProfileViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun initViews() {
        copyIcon.setOnClickListener { viewModel.addressCopyClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .profileComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ProfileViewModel) {
        observe(viewModel.accountNameLiveData, Observer {
            accountTitle.text = it
        })

        observe(viewModel.accountAddressLiveData, Observer {
            accountAddressText.text = it
        })

        observe(viewModel.accountIconLiveData, Observer {
            accountIcon.setImageDrawable(it)
        })

        observe(viewModel.selectedNetworkLiveData, Observer {
            selectedNetworkTv.text = it
        })

        observe(viewModel.selectedLanguageLiveData, Observer {
            selectedLanguageTv.text = it
        })
    }
}