package jp.co.soramitsu.feature_staking_impl.presentation.staking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorSummary
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.StakingNetworkInfoModel
import jp.co.soramitsu.feature_staking_impl.presentation.view.NominatorSummaryView
import kotlinx.android.synthetic.main.fragment_staking.stakingAvatar
import kotlinx.android.synthetic.main.fragment_staking.stakingContainer
import kotlinx.android.synthetic.main.fragment_staking.stakingEstimate
import kotlinx.android.synthetic.main.fragment_staking.stakingNetworkInfo
import kotlinx.android.synthetic.main.fragment_staking.stakingNominatorSummary
import kotlinx.android.synthetic.main.fragment_staking.stakingValidatorSummary
import kotlinx.android.synthetic.main.fragment_staking.startStakingBtn

class StakingFragment : BaseFragment<StakingViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_staking, container, false)
    }

    override fun initViews() {
        stakingContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        stakingEstimate.hideAssetBalanceDollarAmount()

        stakingAvatar.setOnClickListener {
            viewModel.avatarClicked()
        }

        stakingNetworkInfo.storyItemHandler = viewModel::storyClicked
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .stakingComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: StakingViewModel) {
        viewModel.currentStakingState.observe { stakingState ->
            startStakingBtn.setVisible(stakingState is WelcomeViewState)
            stakingEstimate.setVisible(stakingState is WelcomeViewState)
            stakingNominatorSummary.setVisible(stakingState is NominatorViewState)
            stakingValidatorSummary.setVisible(stakingState is ValidatorViewState)

            when (stakingState) {
                is NominatorViewState -> {
                    stakingState.syncStakingRewards()

                    stakingState.nominatorSummaryLiveData.observe { summaryState ->
                        when (summaryState) {
                            is LoadingState.Loading<*> -> {
                                // TODO
                            }

                            is LoadingState.Loaded<NominatorSummaryModel> -> {
                                val summary = summaryState.data

                                stakingNominatorSummary.setElectionStatus(mapNominatorStatus(summary))
                                stakingNominatorSummary.setTotalStaked(summary.totalStaked, summary.totalStakedFiat)
                                stakingNominatorSummary.setTotalRewards(summary.totalRewards, summary.totalRewardsFiat)
                            }
                        }
                    }
                }

                is WelcomeViewState -> {
                    stakingState.assetLiveData.observe {
                        stakingEstimate.setAssetImageResource(it.tokenIconRes)
                        stakingEstimate.setAssetName(it.tokenName)
                        stakingEstimate.setAssetBalance(it.assetBalance)
                    }

                    stakingState.amountFiat.observe { amountFiat ->
                        stakingEstimate.showAssetBalanceDollarAmount()
                        stakingEstimate.setAssetBalanceDollarAmount(amountFiat)
                    }

                    stakingState.returns.observe { rewards ->
                        stakingEstimate.hideReturnsLoading()
                        stakingEstimate.populateMonthEstimation(rewards.monthly)
                        stakingEstimate.populateYearEstimation(rewards.yearly)
                    }

                    stakingEstimate.amountInput.bindTo(stakingState.enteredAmountFlow, lifecycleScope)

                    startStakingBtn.setOnClickListener { stakingState.nextClicked() }
                }
            }
        }

        viewModel.networkInfoStateLiveData.observe { state ->
            when (state) {
                is LoadingState.Loading -> {
                }
                is LoadingState.Loaded<StakingNetworkInfoModel> -> {
                    with(state.data) {
                        stakingNetworkInfo.hideLoading()
                        stakingNetworkInfo.setTotalStake(totalStake)
                        stakingNetworkInfo.setNominatorsCount(nominatorsCount)
                        stakingNetworkInfo.setMinimumStake(minimumStake)
                        stakingNetworkInfo.setLockupPeriod(lockupPeriod)
                        if (totalStakeFiat == null) {
                            stakingNetworkInfo.hideTotalStakeFiat()
                        } else {
                            stakingNetworkInfo.showTotalStakeFiat()
                            stakingNetworkInfo.setTotalStakeFiat(totalStakeFiat)
                        }

                        if (minimumStakeFiat == null) {
                            stakingNetworkInfo.hideMinimumStakeFiat()
                        } else {
                            stakingNetworkInfo.showMinimumStakeFiat()
                            stakingNetworkInfo.setMinimumStakeFiat(minimumStakeFiat)
                        }
                    }
                }
            }
        }

        viewModel.stories.observe(stakingNetworkInfo::submitStories)

        viewModel.networkInfoTitle.observe(stakingNetworkInfo::setTitle)

        viewModel.currentAddressModelLiveData.observe {
            stakingAvatar.setImageDrawable(it.image)
        }
    }

    private fun mapNominatorStatus(summary: NominatorSummaryModel): NominatorSummaryView.Status {

        return when (summary.status) {
            NominatorSummary.Status.INACTIVE -> NominatorSummaryView.Status.Inactive(summary.currentEraDisplay)
            NominatorSummary.Status.ACTIVE -> NominatorSummaryView.Status.Active(summary.currentEraDisplay)
            NominatorSummary.Status.WAITING -> NominatorSummaryView.Status.Waiting
            NominatorSummary.Status.ELECTION -> NominatorSummaryView.Status.Election
        }
    }
}
