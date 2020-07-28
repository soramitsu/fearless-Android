package jp.co.soramitsu.feature_onboarding_impl.presentation.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import kotlinx.android.synthetic.main.fragment_create_account.toolbar
import kotlinx.android.synthetic.main.fragment_privacy.privacyWebView

class PrivacyFragment : BaseFragment<PrivacyViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_privacy, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .privacyComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: PrivacyViewModel) {
        observe(viewModel.privacyAddressLiveData, Observer {
            privacyWebView.loadUrl(it)
        })
    }
}