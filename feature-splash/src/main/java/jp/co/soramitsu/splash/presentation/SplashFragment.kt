package jp.co.soramitsu.splash.presentation

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.splash.di.SplashFeatureApi
import jp.co.soramitsu.splash.di.SplashFeatureComponent
import javax.inject.Inject

class SplashFragment : BaseFragment<SplashViewModel>() {

    @Inject lateinit var splashViewModel: SplashViewModel

    override fun initViews() {
    }

    override fun inject() {
        FeatureUtils.getFeature<SplashFeatureComponent>(this, SplashFeatureApi::class.java)
            .splashComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SplashViewModel) {}
}