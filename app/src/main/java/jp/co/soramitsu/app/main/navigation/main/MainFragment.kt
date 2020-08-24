package jp.co.soramitsu.app.main.navigation.main

import jp.co.soramitsu.app.main.di.MainApi
import jp.co.soramitsu.app.main.di.MainComponent
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils

class MainFragment : BaseFragment<MainViewModel>() {

    override fun initViews() {
        TODO("Not yet implemented")
    }

    override fun inject() {
        FeatureUtils.getFeature<MainComponent>(this, MainApi::class.java)
            .mainFragmentComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: MainViewModel) {
        TODO("Not yet implemented")
    }
}