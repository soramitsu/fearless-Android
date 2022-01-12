package jp.co.soramitsu.feature_staking_impl.presentation.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_account_api.presenatation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeViews
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.displayFeeStatus
import kotlinx.android.synthetic.main.fragment_confirm_stake.*

class ConfirmStakingFragment : BaseFragment<ConfirmStakingViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
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
        onBackPressed { viewModel.backClicked() }

        confirmStakeOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

        confirmStakeConfirm.prepareForProgress(viewLifecycleOwner)
        confirmStakeConfirm.setOnClickListener { viewModel.confirmClicked() }

        confirmStakeSelectedValidators.setOnClickListener { viewModel.nominationsClicked() }

        confirmStakeRewardDestination.setPayoutAccountClickListener { viewModel.payoutAccountClicked() }
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
        observeRetries(viewModel)
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(confirmStakeConfirm::setProgress)

        viewModel.rewardDestinationLiveData.observe {

            if (it != null) {
                confirmStakeRewardDestination.makeVisible()
                confirmStakeRewardDestination.showRewardDestination(it)
            } else {
                confirmStakeRewardDestination.makeGone()
            }
        }

        viewModel.assetModelLiveData.observe {
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

        viewModel.displayAmountLiveData.observe { bondedAmount ->
            confirmStakeAmount.setVisible(bondedAmount != null)

            bondedAmount?.let { confirmStakeAmount.amountInput.setText(it.toString()) }
        }

        viewModel.unstakingTime.observe {
            confirmStakingUnstakingPeriodLength.text = it
        }

        viewModel.eraHoursLength.observe {
            confirmStakingEachEraLength.text = it
        }
    }
}
