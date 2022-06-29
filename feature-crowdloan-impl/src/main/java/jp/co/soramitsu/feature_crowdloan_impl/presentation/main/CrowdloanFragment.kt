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
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.databinding.FragmentCrowdloansBinding
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.setupAssetSelector
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import javax.inject.Inject

class CrowdloanFragment : BaseFragment<CrowdloanViewModel>(R.layout.fragment_crowdloans), CrowdloanAdapter.Handler {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentCrowdloansBinding::bind)

    private val adapter by lazy(LazyThreadSafetyMode.NONE) {
        CrowdloanAdapter(imageLoader, this)
    }

    override fun initViews() {
        binding.crowdloanContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        binding.crowdloanList.setHasFixedSize(true)
        binding.crowdloanList.adapter = adapter

        binding.learnMoreWrapper.setOnClickListener { viewModel.learnMoreClicked() }
        binding.crowdloanRefresh.setOnRefreshListener {
            viewModel.refresh()
            binding.crowdloanRefresh.isRefreshing = false
        }
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
        setupAssetSelector(binding.crowdloanAssetSelector, viewModel, imageLoader)

        viewModel.crowdloanModelsFlow.observe { loadingState ->
            binding.crowdloanRefresh.setVisible(loadingState is LoadingState.Loaded && loadingState.data.isNotEmpty())
            binding.crowdloanPlaceholder.setVisible(loadingState is LoadingState.Loaded && loadingState.data.isEmpty())
            binding.crowdloanProgress.setVisible(loadingState is LoadingState.Loading)

            if (loadingState is LoadingState.Loaded) {
                adapter.submitList(loadingState.data)
            }
        }

        viewModel.mainDescription.observe(binding.crowdloanMainDescription::setText)

        viewModel.learnMoreLiveData.observe {
            binding.learnMoreText.text = it
        }

        observeBrowserEvents(viewModel)

        viewModel.blockingProgress.observe {
            binding.blockingProgress.setVisible(it)
        }
    }

    override fun crowdloanClicked(chainId: ChainId, paraId: ParaId) {
        viewModel.crowdloanClicked(chainId, paraId)
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
