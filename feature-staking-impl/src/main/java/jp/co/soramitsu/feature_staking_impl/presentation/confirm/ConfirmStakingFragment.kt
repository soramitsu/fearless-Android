package jp.co.soramitsu.feature_staking_impl.presentation.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.account.external.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeViews
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.displayFeeStatus
import jp.co.soramitsu.feature_staking_impl.presentation.setup.RewardDestinationModel
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakeAmount
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakeConfirm
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakeOriginAccount
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakePayoutAccount
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakeRewardDestination
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakeSelectedValidators
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakeSelectedValidatorsCount
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakeToolbar
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakingFeeFiat
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakingFeeProgress
import kotlinx.android.synthetic.main.fragment_confirm_stake.confirmStakingFeeToken
import kotlinx.android.synthetic.main.fragment_confirm_stake.stakingConfirmationContainer

class ConfirmStakingFragment : BaseFragment<ConfirmStakingViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_confirm_stake, container, false)
    }

    override fun initViews() {
        stakingConfirmationContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        confirmStakeToolbar.setHomeButtonListener { viewModel.backClicked() }

        confirmStakeOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

        confirmStakeConfirm.prepareForProgress(viewLifecycleOwner)
        confirmStakeConfirm.setOnClickListener { viewModel.confirmClicked() }

        confirmStakeSelectedValidators.setOnClickListener { viewModel.nominationsClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmStakingComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmStakingViewModel) {
        observeRetries(viewModel, themedContext())
        observeValidations(viewModel, themedContext())
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe { show ->
            confirmStakeConfirm.setState(if (show) ButtonState.PROGRESS else ButtonState.NORMAL)
        }

        viewModel.rewardDestinationLiveData.observe {
            confirmStakePayoutAccount.setVisible(it is RewardDestinationModel.Payout)

            when (it) {
                is RewardDestinationModel.Restake -> {
                    confirmStakeRewardDestination.setText(R.string.staking_setup_restake)
                }
                is RewardDestinationModel.Payout -> {
                    confirmStakeRewardDestination.setText(R.string.staking_setup_payout)
                    confirmStakePayoutAccount.setMessage(it.destination.nameOrAddress)
                    confirmStakePayoutAccount.setTextIcon(it.destination.image)
                }
            }
        }

        viewModel.assetLiveData.observe {
            confirmStakeAmount.setAssetBalance(it.assetBalance)
            confirmStakeAmount.setAssetName(it.tokenName)
            confirmStakeAmount.setAssetImageResource(it.tokenIconRes)
        }

        viewModel.feeLiveData.observe {
            displayFeeStatus(
                it,
                FeeViews(confirmStakingFeeProgress, confirmStakingFeeFiat, confirmStakingFeeToken)
            )
        }

        viewModel.currentAccountModelLiveData.observe {
            confirmStakeOriginAccount.setMessage(it.nameOrAddress)
            confirmStakeOriginAccount.setTextIcon(it.image)
        }

        viewModel.nominationsLiveData.observe {
            confirmStakeSelectedValidatorsCount.text = it
        }

        confirmStakeAmount.amountInput.setText(viewModel.stakingAmount)
    }
}