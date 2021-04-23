package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select

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
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_bond_more.bondMoreContainer
import kotlinx.android.synthetic.main.fragment_bond_more.bondMoreFee
import kotlinx.android.synthetic.main.fragment_bond_more.bondMoreToolbar
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingAmountField
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingNext

class SelectBondMoreFragment : BaseFragment<SelectBondMoreViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bond_more, container, false)
    }

    override fun initViews() {
        bondMoreContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        bondMoreToolbar.setHomeButtonListener { viewModel.backClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectBondMoreFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SelectBondMoreViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)

        viewModel.showNextProgress.observe { show ->
            setupStakingNext.setState(if (show) ButtonState.PROGRESS else ButtonState.NORMAL)
        }

        viewModel.assetModelFlow.observe {
            setupStakingAmountField.setAssetBalance(it.assetBalance)
            setupStakingAmountField.setAssetName(it.tokenName)
            setupStakingAmountField.setAssetImageResource(it.tokenIconRes)
        }

        setupStakingAmountField.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(setupStakingAmountField::setAssetBalanceDollarAmount)
        }

        viewModel.feeLiveData.observe(bondMoreFee::setFeeStatus)
    }
}
