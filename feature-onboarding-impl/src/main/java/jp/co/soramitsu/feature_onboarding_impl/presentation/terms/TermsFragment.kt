package jp.co.soramitsu.feature_onboarding_impl.presentation.terms

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
import kotlinx.android.synthetic.main.fragment_terms.termsWebView

class TermsFragment : BaseFragment<TermsViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_terms, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .termsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: TermsViewModel) {
        observe(viewModel.termsAddressLiveData, Observer {
            termsWebView.loadUrl(it)
        })
    }
}