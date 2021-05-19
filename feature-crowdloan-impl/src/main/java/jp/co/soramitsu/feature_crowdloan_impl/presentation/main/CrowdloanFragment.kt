package jp.co.soramitsu.feature_crowdloan_impl.presentation.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import kotlinx.android.synthetic.main.fragment_crowdloans.crowdloanContainer
import kotlinx.android.synthetic.main.fragment_crowdloans.crowdloanList
import javax.inject.Inject

class CrowdloanFragment : BaseFragment<CrowdloanViewModel>() {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val adapter by lazy(LazyThreadSafetyMode.NONE) {
        CrowdloanAdapter(imageLoader)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_crowdloans, container, false)
    }

    override fun initViews() {
        crowdloanContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        crowdloanList.setHasFixedSize(true)
        crowdloanList.adapter = adapter
    }

    override fun inject() {
        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            requireContext(),
            CrowdloanFeatureApi::class.java
        )
            .crowdloansFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: CrowdloanViewModel) {
        viewModel.crowdloanModelsFlow.observe(adapter::submitList)
    }
}
