package jp.co.soramitsu.app.navigation.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.di.main.MainApi
import jp.co.soramitsu.app.di.main.MainComponent
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils

class OnboardingFragment : BaseFragment<OnboardingViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun initViews() {
    }

    override fun inject() {
        FeatureUtils.getFeature<MainComponent>(this, MainApi::class.java)
            .onboardingComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: OnboardingViewModel) {
    }
}