package jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_account_api.presenatation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import kotlinx.android.synthetic.main.fragment_confirm_reward_destination.confirmRewardDestinationConfirm
import kotlinx.android.synthetic.main.fragment_confirm_reward_destination.confirmRewardDestinationContainer
import kotlinx.android.synthetic.main.fragment_confirm_reward_destination.confirmRewardDestinationFee
import kotlinx.android.synthetic.main.fragment_confirm_reward_destination.confirmRewardDestinationOriginAccount
import kotlinx.android.synthetic.main.fragment_confirm_reward_destination.confirmRewardDestinationRewardDestination
import kotlinx.android.synthetic.main.fragment_confirm_reward_destination.confirmRewardDestinationToolbar

private const val KEY_PAYLOAD = "KEY_PAYLOAD"

class ConfirmRewardDestinationFragment : BaseFragment<ConfirmRewardDestinationViewModel>() {

    companion object {

        fun getBundle(payload: ConfirmRewardDestinationPayload) = Bundle().apply {
            putParcelable(KEY_PAYLOAD, payload)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_confirm_reward_destination, container, false)
    }

    override fun initViews() {
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

    override fun inject() {
        val payload = argument<ConfirmRewardDestinationPayload>(KEY_PAYLOAD)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmRewardDestinationFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmRewardDestinationViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(confirmRewardDestinationConfirm::setProgress)

        viewModel.rewardDestinationLiveData.observe(confirmRewardDestinationRewardDestination::showRewardDestination)

        viewModel.feeLiveData.observe(confirmRewardDestinationFee::setFeeStatus)

        viewModel.originAccountModelLiveData.observe {
            confirmRewardDestinationOriginAccount.setMessage(it.nameOrAddress)
            confirmRewardDestinationOriginAccount.setTextIcon(it.image)
        }
    }
}
