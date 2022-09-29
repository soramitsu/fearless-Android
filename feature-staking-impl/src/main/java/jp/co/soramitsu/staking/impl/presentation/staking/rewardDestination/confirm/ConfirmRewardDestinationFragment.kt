package jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm

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
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmRewardDestinationBinding
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload

const val KEY_PAYLOAD = "KEY_PAYLOAD"

@AndroidEntryPoint
class ConfirmRewardDestinationFragment : BaseFragment<ConfirmRewardDestinationViewModel>(R.layout.fragment_confirm_reward_destination) {

    companion object {

        fun getBundle(payload: ConfirmRewardDestinationPayload) = Bundle().apply {
            putParcelable(KEY_PAYLOAD, payload)
        }
    }

    override val viewModel: ConfirmRewardDestinationViewModel by viewModels()

    private val binding by viewBinding(FragmentConfirmRewardDestinationBinding::bind)

    override fun initViews() {
        with(binding) {
            confirmRewardDestinationContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }

                consume(true)
            }

            confirmRewardDestinationToolbar.setHomeButtonListener { viewModel.backClicked() }

            confirmRewardDestinationOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

            confirmRewardDestinationConfirm.prepareForProgress(viewLifecycleOwner)
            confirmRewardDestinationConfirm.setOnClickListener { viewModel.confirmClicked() }

            confirmRewardDestinationRewardDestination.setPayoutAccountClickListener { viewModel.payoutAccountClicked() }
        }
    }

    override fun subscribe(viewModel: ConfirmRewardDestinationViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(binding.confirmRewardDestinationConfirm::setProgress)

        viewModel.rewardDestinationLiveData.observe(binding.confirmRewardDestinationRewardDestination::showRewardDestination)

        viewModel.feeLiveData.observe(binding.confirmRewardDestinationFee::setFeeStatus)

        viewModel.originAccountModelLiveData.observe {
            binding.confirmRewardDestinationOriginAccount.setMessage(it.nameOrAddress)
            binding.confirmRewardDestinationOriginAccount.setTextIcon(it.image)
        }
    }
}
