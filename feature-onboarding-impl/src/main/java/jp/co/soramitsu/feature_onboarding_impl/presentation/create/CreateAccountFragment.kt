package jp.co.soramitsu.feature_onboarding_impl.presentation.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent

class CreateAccountFragment: BaseFragment<CreateAccountViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_create_account, container, false)
    }

    override fun initViews() {
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .createAccountComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: CreateAccountViewModel) {
    }
}