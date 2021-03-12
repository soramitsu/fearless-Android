package jp.co.soramitsu.feature_staking_impl.presentation.staking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_staking.stakingAvatar
import kotlinx.android.synthetic.main.fragment_staking.stakingContainer
import kotlinx.android.synthetic.main.fragment_staking.stakingEstimate
import kotlinx.android.synthetic.main.fragment_staking.stakingNetworkInfo
import kotlinx.android.synthetic.main.fragment_staking.stakingTitle
import kotlinx.android.synthetic.main.fragment_staking.startStakingBtn

class StakingFragment : BaseFragment<StakingViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_staking, container, false)
    }

    override fun initViews() {
        stakingContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        val background = with(requireContext()) {
            addRipple(getCutCornerDrawable(R.color.blurColor))
        }
        stakingNetworkInfo.background = background

        stakingEstimate.hideAssetBalanceDollarAmount()

        startStakingBtn.setOnClickListener { viewModel.nextClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .stakingComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: StakingViewModel) {
        viewModel.currentStakingState.observe {
            stakingTitle.text = it::class.simpleName
        }

        stakingEstimate.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.currentAddressModelLiveData.observe {
            stakingAvatar.setImageDrawable(it.image)
        }

        viewModel.asset.observe {
            stakingEstimate.setAssetImageResource(it.tokenIconRes)
            stakingEstimate.setAssetName(it.tokenName)
            stakingEstimate.setAssetBalance(it.assetBalance)
        }

        viewModel.amountFiat.observe { amountFiat ->
            stakingEstimate.showAssetBalanceDollarAmount()
            stakingEstimate.setAssetBalanceDollarAmount(amountFiat)
        }

        viewModel.returns.observe { rewards ->
            stakingEstimate.populateMonthEstimation(rewards.monthly)
            stakingEstimate.populateYearEstimation(rewards.yearly)
        }

        stakingEstimate.amountInput.onTextChanged(viewModel::onAmountChanged)
    }
}