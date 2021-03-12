package jp.co.soramitsu.app.root.presentation.main.coming_soon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.di.RootApi
import jp.co.soramitsu.app.root.di.RootComponent
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.showBrowser
import kotlinx.android.synthetic.main.fragment_coming_soon.comingSoonDevStatus
import kotlinx.android.synthetic.main.fragment_coming_soon.comingSoonRoadmap

class ComingSoonFragment : BaseFragment<ComingSoonViewModel>() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.fragment_coming_soon, container, false)
    }

    override fun initViews() {
        comingSoonRoadmap.setOnClickListener { viewModel.roadMapClicked() }
        comingSoonDevStatus.setOnClickListener { viewModel.devStatusClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<RootComponent>(this, RootApi::class.java)
            .comingSoonComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ComingSoonViewModel) {
        viewModel.openBrowserEvent.observeEvent(this::showBrowser)
    }
}
