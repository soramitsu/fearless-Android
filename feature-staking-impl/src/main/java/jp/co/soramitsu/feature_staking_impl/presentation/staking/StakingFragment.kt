package jp.co.soramitsu.feature_staking_impl.presentation.staking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.common.wallet.formatWithDefaultPrecision
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.icon
import kotlinx.android.synthetic.main.fragment_staking.stakingAvatar
import kotlinx.android.synthetic.main.fragment_staking.stakingEstimate
import kotlinx.android.synthetic.main.fragment_staking.stakingNetworkInfo

class StakingFragment : BaseFragment<StakingViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_staking, container, false)
    }

    override fun initViews() {
        val background = with(requireContext()) {
            addRipple(getCutCornerDrawable(R.color.blurColor))
        }
        stakingNetworkInfo.background = background
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
        stakingEstimate.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.currentAddressModelLiveData.observe {
            stakingAvatar.setImageDrawable(it.image)
        }

        viewModel.asset.observe {
            stakingEstimate.setAssetImageResource(it.token.type.icon)
            stakingEstimate.setAssetName(it.token.type.displayName)
            stakingEstimate.setAssetBalance(
                getString(R.string.common_balance_format, it.available.formatWithDefaultPrecision(it.token.type))
            )
        }

        viewModel.returns.observe { rewards ->
            stakingEstimate.setMonthlyGainAsset(rewards.monthly.amount)
            if (rewards.monthly.fiatAmount == null) {
                stakingEstimate.hideMonthlyGainFiat()
            } else {
                stakingEstimate.showMonthlyGainFiat()
                stakingEstimate.setMonthlyGainFiat(rewards.monthly.fiatAmount)
            }
            stakingEstimate.setMonthlyGainPercentage(rewards.monthly.gain)

            stakingEstimate.setYearlyGainAsset(rewards.yearly.amount)
            if (rewards.yearly.fiatAmount == null) {
                stakingEstimate.hideYearlyGainFiat()
            } else {
                stakingEstimate.showYearlyGainFiat()
                stakingEstimate.setYearlyGainFiat(rewards.yearly.fiatAmount)

            }
            stakingEstimate.setYearlyGainPercentage(rewards.yearly.gain)
            if (rewards.amountFiat == null) {
                stakingEstimate.hideAssetBalanceDollarAmount()
            } else {
                stakingEstimate.showAssetBalanceDollarAmount()
                stakingEstimate.setAssetBalanceDollarAmount(rewards.amountFiat)
            }
        }

        stakingEstimate.amountInput.onTextChanged(viewModel::onAmountChanged)
    }
}