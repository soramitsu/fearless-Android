package jp.co.soramitsu.feature_staking_impl.presentation.payouts.detail

import android.os.Bundle
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.formatDateFromMillis
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentPayoutDetailsBinding
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable
import javax.inject.Inject

@AndroidEntryPoint
class PayoutDetailsFragment : BaseFragment<PayoutDetailsViewModel>(R.layout.fragment_payout_details) {

    companion object {
        private const val KEY_PAYOUT = "validator"

        fun getBundle(payout: PendingPayoutParcelable): Bundle {
            return Bundle().apply {
                putParcelable(KEY_PAYOUT, payout)
            }
        }
    }

    @Inject
    lateinit var factory: PayoutDetailsViewModel.PayoutDetailsViewModelFactory

    private val vm: PayoutDetailsViewModel by viewModels {
        PayoutDetailsViewModel.provideFactory(
            factory,
            requireArguments().getParcelable<PendingPayoutParcelable>(KEY_PAYOUT) as PendingPayoutParcelable
        )
    }
    override val viewModel: PayoutDetailsViewModel
        get() = vm

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
