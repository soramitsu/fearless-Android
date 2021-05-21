package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeAmount
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeContainer
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeContinue
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeToolbar

class CrowdloanContributeFragment : BaseFragment<CrowdloanContributeViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_contribute, container, false)
    }

    override fun initViews() {
        crowdloanContributeContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        crowdloanContributeToolbar.setHomeButtonListener { viewModel.backClicked() }
        crowdloanContributeContinue.prepareForProgress(viewLifecycleOwner)
        crowdloanContributeContinue.setOnClickListener { viewModel.nextClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectBondMoreFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: CrowdloanContributeViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)

        viewModel.showNextProgress.observe(crowdloanContributeContinue::setProgress)

        viewModel.assetModelFlow.observe {
            crowdloanContributeAmount.setAssetBalance(it.assetBalance)
            crowdloanContributeAmount.setAssetName(it.tokenName)
            crowdloanContributeAmount.setAssetImageResource(it.tokenIconRes)
        }

        crowdloanContributeAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it.let(crowdloanContributeAmount::setAssetBalanceDollarAmount)
        }

        viewModel.feeLiveData.observe(crowdloanContributeFee::setFeeStatus)
    }
}
