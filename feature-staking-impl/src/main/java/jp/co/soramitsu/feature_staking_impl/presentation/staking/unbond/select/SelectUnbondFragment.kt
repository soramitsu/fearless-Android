package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select

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
import kotlinx.android.synthetic.main.fragment_select_unbond.unbondAmount
import kotlinx.android.synthetic.main.fragment_select_unbond.unbondContainer
import kotlinx.android.synthetic.main.fragment_select_unbond.unbondContinue
import kotlinx.android.synthetic.main.fragment_select_unbond.unbondFee
import kotlinx.android.synthetic.main.fragment_select_unbond.unbondPeriod
import kotlinx.android.synthetic.main.fragment_select_unbond.unbondToolbar

class SelectUnbondFragment : BaseFragment<SelectUnbondViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_select_unbond, container, false)
    }

    override fun initViews() {
        unbondContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        unbondToolbar.setHomeButtonListener { viewModel.backClicked() }
        unbondContinue.prepareForProgress(viewLifecycleOwner)
        unbondContinue.setOnClickListener { viewModel.nextClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectUnbondFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SelectUnbondViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)

        viewModel.showNextProgress.observe(unbondContinue::setProgress)

        viewModel.assetModelFlow.observe {
            unbondAmount.setAssetBalance(it.assetBalance)
            unbondAmount.setAssetName(it.tokenName)
            unbondAmount.setAssetImageResource(it.tokenIconRes)
        }

        unbondAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(unbondAmount::setAssetBalanceDollarAmount)
        }

        viewModel.feeLiveData.observe(unbondFee::setFeeStatus)

        viewModel.lockupPeriodLiveData.observe(unbondPeriod::showValue)
    }
}
