package jp.co.soramitsu.feature_crowdloan_impl.presentation.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import kotlinx.android.synthetic.main.fragment_crowdloans.blockingProgress
import kotlinx.android.synthetic.main.fragment_crowdloans.crowdloanContainer
import kotlinx.android.synthetic.main.fragment_crowdloans.crowdloanList
import kotlinx.android.synthetic.main.fragment_crowdloans.crowdloanMainDescription
import kotlinx.android.synthetic.main.fragment_crowdloans.crowdloanPlaceholder
import kotlinx.android.synthetic.main.fragment_crowdloans.crowdloanProgress
import kotlinx.android.synthetic.main.fragment_crowdloans.learnMoreText
import kotlinx.android.synthetic.main.fragment_crowdloans.learnMoreWrapper
import javax.inject.Inject

class CrowdloanFragment : BaseFragment<CrowdloanViewModel>(), CrowdloanAdapter.Handler {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val adapter by lazy(LazyThreadSafetyMode.NONE) {
        CrowdloanAdapter(imageLoader, this)
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

        learnMoreWrapper.setOnClickListener { viewModel.learnMoreClicked() }
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
        viewModel.crowdloanModelsFlow.observe { loadingState ->
            crowdloanList.setVisible(loadingState is LoadingState.Loaded && loadingState.data.isNotEmpty())
            crowdloanPlaceholder.setVisible(loadingState is LoadingState.Loaded && loadingState.data.isEmpty())
            crowdloanProgress.setVisible(loadingState is LoadingState.Loading)

            if (loadingState is LoadingState.Loaded) {
                adapter.submitList(loadingState.data)
            }
        }

        viewModel.mainDescription.observe(crowdloanMainDescription::setText)

        viewModel.learnMoreLiveData.observe {
            learnMoreText.text = it
        }

        observeBrowserEvents(viewModel)

        viewModel.blockingProgress.observe {
            blockingProgress.setVisible(it)
        }
    }

    override fun crowdloanClicked(paraId: ParaId) {
        viewModel.crowdloanClicked(paraId)
    }

    override fun copyReferralClicked(code: String) {
        CrowdloanReferralActionsSheet(
            context = requireContext(),
            code = code,
            onCopy = viewModel::copyStringClicked,
            onShare = ::startSharingIntent
        )
            .show()
    }

    private fun startSharingIntent(code: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, code)
        }

        startActivity(Intent.createChooser(intent, getString(R.string.share_referral_code)))
    }

}
