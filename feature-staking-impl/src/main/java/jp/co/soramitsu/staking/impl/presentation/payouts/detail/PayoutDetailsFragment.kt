package jp.co.soramitsu.staking.impl.presentation.payouts.detail

import android.os.Bundle
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.formatDateFromMillis
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentPayoutDetailsBinding
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable

@AndroidEntryPoint
class PayoutDetailsFragment : BaseFragment<PayoutDetailsViewModel>(R.layout.fragment_payout_details) {

    companion object {
        const val KEY_PAYOUT = "validator"

        fun getBundle(payout: PendingPayoutParcelable): Bundle {
            return Bundle().apply {
                putParcelable(KEY_PAYOUT, payout)
            }
        }
    }

    override val viewModel: PayoutDetailsViewModel by viewModels()

    private val binding by viewBinding(FragmentPayoutDetailsBinding::bind)

    override fun initViews() {
        with(binding) {
            payoutDetailsContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            payoutDetailsToolbar.setHomeButtonListener { viewModel.backClicked() }

            payoutDetailsSubmit.setOnClickListener { viewModel.payoutClicked() }

            payoutDetailsValidator.setWholeClickListener { viewModel.validatorExternalActionClicked() }
        }
    }

    override fun subscribe(viewModel: PayoutDetailsViewModel) {
        setupExternalActions(viewModel)

        viewModel.payoutDetails.observe {
            // TODO perform date formatting in viewModel
            with(binding) {
                payoutDetailsDate.text = it.createdAt.formatDateFromMillis(requireContext())
                payoutDetailsEra.text = it.eraDisplay
                payoutDetailsReward.text = it.reward
                payoutDetailsRewardFiat.text = it.rewardFiat

                payoutDetailsValidator.setMessage(it.validatorAddressModel.nameOrAddress)
                payoutDetailsValidator.setTextIcon(it.validatorAddressModel.image)
            }
        }
    }
}
