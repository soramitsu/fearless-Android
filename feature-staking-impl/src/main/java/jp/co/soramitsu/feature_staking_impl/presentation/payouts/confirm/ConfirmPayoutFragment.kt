package jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmPayoutBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeViews
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.displayFeeStatus

class ConfirmPayoutFragment : BaseFragment<ConfirmPayoutViewModel>(R.layout.fragment_confirm_payout) {

    companion object {
        private const val KEY_PAYOUTS = "validator"

        fun getBundle(payload: ConfirmPayoutPayload): Bundle {
            return Bundle().apply {
                putParcelable(KEY_PAYOUTS, payload)
            }
        }
    }

    private val binding by viewBinding(FragmentConfirmPayoutBinding::bind)

    override fun initViews() {
        with(binding) {
            confirmPayoutContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            confirmPayoutConfirm.setOnClickListener { viewModel.submitClicked() }

            confirmPayoutToolbar.setHomeButtonListener { viewModel.backClicked() }

            confirmPayoutConfirm.prepareForProgress(viewLifecycleOwner)

            confirmPayoutOriginAccount.setWholeClickListener { viewModel.controllerClicked() }
            confirmPayoutRewardDestination.setWholeClickListener { viewModel.rewardDestinationClicked() }
        }
    }

    override fun inject() {
        val payload = argument<ConfirmPayoutPayload>(KEY_PAYOUTS)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmPayoutFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmPayoutViewModel) {
        setupExternalActions(viewModel)
        observeValidations(viewModel)
        observeRetries(viewModel)

        viewModel.feeLiveData.observe {
            displayFeeStatus(
                it,
                FeeViews(
                    binding.confirmPayoutFeeProgress,
                    binding.confirmPayoutFeeFiat,
                    binding.confirmPayoutFeeToken
                )
            )
        }

        viewModel.initiatorAddressModel.observe {
            with(binding.confirmPayoutOriginAccount) {
                setMessage(it.nameOrAddress)
                setTextIcon(it.image)
            }
        }

        viewModel.rewardDestinationModel.observe {
            with(binding.confirmPayoutRewardDestination) {
                setMessage(it.nameOrAddress)
                setTextIcon(it.image)
            }
        }

        viewModel.totalRewardDisplay.observe { (inToken, inFiat) ->
            binding.confirmPayoutRewardToken.text = inToken
            binding.confirmPayoutRewardFiat.text = inFiat
        }

        viewModel.showNextProgress.observe(binding.confirmPayoutConfirm::setProgress)
    }
}
