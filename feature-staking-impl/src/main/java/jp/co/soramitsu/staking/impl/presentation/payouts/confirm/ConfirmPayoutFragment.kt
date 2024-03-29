package jp.co.soramitsu.staking.impl.presentation.payouts.confirm

import android.os.Bundle
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmPayoutBinding
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeViews
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.displayFeeStatus

@AndroidEntryPoint
class ConfirmPayoutFragment : BaseFragment<ConfirmPayoutViewModel>(R.layout.fragment_confirm_payout) {

    companion object {
        const val KEY_PAYOUTS = "validator"

        fun getBundle(payload: ConfirmPayoutPayload): Bundle {
            return Bundle().apply {
                putParcelable(KEY_PAYOUTS, payload)
            }
        }
    }

    override val viewModel: ConfirmPayoutViewModel by viewModels()

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

    override fun subscribe(viewModel: ConfirmPayoutViewModel) {
        setupExternalActions(viewModel)
        observeValidations(viewModel)

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
