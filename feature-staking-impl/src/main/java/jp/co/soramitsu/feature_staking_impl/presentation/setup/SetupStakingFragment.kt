package jp.co.soramitsu.feature_staking_impl.presentation.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingAmountField
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingContainer
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingTargetPayout
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingTargetPayoutDestination
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingTargetRestake

class SetupStakingFragment : BaseFragment<SetupStakingViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup_staking, container, false)
    }

    override fun initViews() {
        setupStakingContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        setupStakingTargetPayout.setOnClickListener { viewModel.payoutClicked() }
        setupStakingTargetRestake.setOnClickListener { viewModel.restakeClicked() }

        setupStakingTargetPayoutDestination.setWholeClickListener { viewModel.payoutDestinationClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .setupStakingComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SetupStakingViewModel) {
        viewModel.payoutTargetLiveData.observe {
            setupStakingTargetPayoutDestination.setVisible(it is PayoutTarget.Payout)
            setupStakingTargetRestake.isChecked = it is PayoutTarget.Restake
            setupStakingTargetPayout.isChecked = it is PayoutTarget.Payout

            if (it is PayoutTarget.Payout) {
                setupStakingTargetPayoutDestination.setMessage(it.destination.nameOrAddress)
                setupStakingTargetPayoutDestination.setTextIcon(it.destination.image)
            }
        }

        viewModel.assetModelsFlow.observe {
            val available = getString(R.string.common_balance_format, it.assetBalance)

            setupStakingAmountField.setAssetBalance(available)
            setupStakingAmountField.setAssetName(it.tokenName)
            setupStakingAmountField.setAssetImageResource(it.tokenIconRes)
        }

        setupStakingAmountField.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.returnsLiveData.observe {
            setupStakingTargetPayout.setPercentageGain(it.payout.gain)
            setupStakingTargetPayout.setTokenAmount(it.payout.amount)

            setupStakingTargetRestake.setPercentageGain(it.restake.gain)
            setupStakingTargetRestake.setTokenAmount(it.restake.amount)
        }

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(setupStakingAmountField::setAssetBalanceDollarAmount)
        }

        viewModel.showDestinationChooserEvent.observeEvent(::showDestinationChooser)
    }

    private fun showDestinationChooser(payload: DynamicListBottomSheet.Payload<AddressModel>) {
        AccountChooserBottomSheetDialog(
            requireContext(),
            payload,
            viewModel::payoutDestinationChanged
        ).show()
    }
}