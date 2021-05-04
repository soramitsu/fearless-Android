package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.custom

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
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_rebond_custom.rebondAmount
import kotlinx.android.synthetic.main.fragment_rebond_custom.rebondContinue
import kotlinx.android.synthetic.main.fragment_rebond_custom.rebondFee
import kotlinx.android.synthetic.main.fragment_rebond_custom.rebondToolbar

class CustomRebondFragment : BaseFragment<CustomRebondViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_rebond_custom, container, false)
    }

    override fun initViews() {
        rebondToolbar.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        rebondToolbar.setHomeButtonListener { viewModel.backClicked() }
        rebondContinue.prepareForProgress(viewLifecycleOwner)
        rebondContinue.setOnClickListener { viewModel.confirmClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .rebondCustomFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: CustomRebondViewModel) {
        observeValidations(viewModel)
        observeRetries(viewModel)

        viewModel.showNextProgress.observe(rebondContinue::setProgress)

        viewModel.assetModelFlow.observe {
            rebondAmount.setAssetBalance(it.assetBalance)
            rebondAmount.setAssetName(it.tokenName)
            rebondAmount.setAssetImageResource(it.tokenIconRes)
        }

        rebondAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.amountFiatFLow.observe {
            it?.let(rebondAmount::setAssetBalanceDollarAmount)
        }

        viewModel.feeLiveData.observe(rebondFee::setFeeStatus)
    }
}
