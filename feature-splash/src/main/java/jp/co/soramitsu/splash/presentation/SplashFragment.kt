package jp.co.soramitsu.splash.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.splash.R
import jp.co.soramitsu.splash.di.SplashFeatureApi
import jp.co.soramitsu.splash.di.SplashFeatureComponent
import javax.inject.Inject

class SplashFragment : BaseFragment<SplashViewModel>() {

    @Inject lateinit var splashViewModel: SplashViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun initViews() {
    }

    override fun inject() {
        FeatureUtils.getFeature<SplashFeatureComponent>(this, SplashFeatureApi::class.java)
            .splashComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SplashViewModel) {
        viewModel.removeSplashBackgroundLiveData.observeEvent {
            (activity as? SplashBackgroundHolder)?.removeSplashBackground()
        }
    }
}
