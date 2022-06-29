package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.dialog.infoDialog
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentStakingBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.StashNoneStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.ValidatorStatus
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.feature_staking_impl.presentation.view.StakeSummaryView
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.setupAssetSelector

class StakingFragment : BaseFragment<StakingViewModel>(R.layout.fragment_staking) {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentStakingBinding::bind)

    override fun initViews() {
        with(binding) {
            stakingContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            stakingEstimate.hideAssetBalanceFiatAmount()

            stakingAvatar.setOnClickListener {
                viewModel.avatarClicked()
            }

            stakingNetworkInfo.storyItemHandler = {
                viewModel.storyClicked(StoryGroupModel(it.elements))
            }
        }
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
        observeValidations(viewModel)
        setupAssetSelector(binding.stakingAssetSelector, viewModel, imageLoader)

        viewModel.alertsFlow.observe { loadingState ->
            when (loadingState) {
                is LoadingState.Loaded -> {
                    binding.stakingAlertsInfo.hideLoading()

                    if (loadingState.data.isEmpty()) {
                        binding.stakingAlertsInfo.makeGone()
                    } else {
                        binding.stakingAlertsInfo.makeVisible()
                        binding.stakingAlertsInfo.setStatus(loadingState.data)
                    }
                }

                is LoadingState.Loading -> {
                    binding.stakingAlertsInfo.makeVisible()
                    binding.stakingAlertsInfo.showLoading()
                }
            }
        }

        viewModel.stakingViewStateFlow.observe { loadingState ->
            when (loadingState) {
                is LoadingState.Loading -> {
                    binding.startStakingBtn.setVisible(false)
                    binding.stakingEstimate.setVisible(false)
                    binding.stakingStakeSummary.setVisible(false)
                }
                is LoadingState.Loaded -> {
                    val stakingState = loadingState.data

                    binding.startStakingBtn.setVisible(stakingState is WelcomeViewState)
                    binding.stakingEstimate.setVisible(stakingState is WelcomeViewState)
                    binding.stakingStakeSummary.setVisible(stakingState is StakeViewState<*>)

                    when (stakingState) {
                        is NominatorViewState -> {
                            binding.stakingStakeSummary.bindStakeSummary(stakingState, ::mapNominatorStatus)
                        }

                        is ValidatorViewState -> {
                            binding.stakingStakeSummary.bindStakeSummary(stakingState, ::mapValidatorStatus)
                        }

                        is StashNoneViewState -> {
                            binding.stakingStakeSummary.bindStakeSummary(stakingState, ::mapStashNoneStatus)
                        }

                        is WelcomeViewState -> {
                            observeValidations(stakingState)

                            stakingState.assetLiveData.observe {
                                binding.stakingEstimate.setAssetImageUrl(it.imageUrl, imageLoader)
                                binding.stakingEstimate.setAssetName(it.tokenName)
                                binding.stakingEstimate.setAssetBalance(it.assetBalance)
                            }

                            stakingState.amountFiat.observe { amountFiat ->
                                binding.stakingEstimate.showAssetBalanceFiatAmount()
                                binding.stakingEstimate.setAssetBalanceFiatAmount(amountFiat)
                            }

                            stakingState.returns.observe { rewards ->
                                binding.stakingEstimate.hideReturnsLoading()
                                binding.stakingEstimate.populateMonthEstimation(rewards.monthly)
                                binding.stakingEstimate.populateYearEstimation(rewards.yearly)
                            }

                            binding.stakingEstimate.amountInput.bindTo(stakingState.enteredAmountFlow, viewLifecycleOwner.lifecycleScope)

                            binding.startStakingBtn.setOnClickListener { stakingState.nextClicked() }

                            binding.stakingEstimate.infoActions.setOnClickListener { stakingState.infoActionClicked() }

                            stakingState.showRewardEstimationEvent.observeEvent {
                                StakingRewardEstimationBottomSheet(requireContext(), it).show()
                            }
                        }
                    }
                }
            }
        }

        viewModel.networkInfoStateLiveData.observe { state ->
            when (state) {
                is LoadingState.Loading<*> -> binding.stakingNetworkInfo.showLoading()

                is LoadingState.Loaded<StakingNetworkInfoModel> -> {
                    with(state.data) {
                        with(binding) {
                            stakingNetworkInfo.hideLoading()
                            stakingNetworkInfo.setTotalStake(totalStake)
                            stakingNetworkInfo.setNominatorsCount(nominatorsCount)
                            stakingNetworkInfo.setMinimumStake(minimumStake)
                            stakingNetworkInfo.setLockupPeriod(lockupPeriod)
                        }
                        if (totalStakeFiat == null) {
                            binding.stakingNetworkInfo.hideTotalStakeFiat()
                        } else {
                            binding.stakingNetworkInfo.showTotalStakeFiat()
                            binding.stakingNetworkInfo.setTotalStakeFiat(totalStakeFiat)
                        }

                        if (minimumStakeFiat == null) {
                            binding.stakingNetworkInfo.hideMinimumStakeFiat()
                        } else {
                            binding.stakingNetworkInfo.showMinimumStakeFiat()
                            binding.stakingNetworkInfo.setMinimumStakeFiat(minimumStakeFiat)
                        }
                    }
                }
            }
        }

        viewModel.stories.observe(binding.stakingNetworkInfo::submitStories)

        viewModel.networkInfoTitle.observe(binding.stakingNetworkInfo::setTitle)

        viewModel.currentAddressModelLiveData.observe {
            binding.stakingAvatar.setImageDrawable(it.image)
        }
    }

    private fun <S> StakeSummaryView.bindStakeSummary(
        stakingViewState: StakeViewState<S>,
        mapStatus: (StakeSummaryModel<S>) -> StakeSummaryView.Status
    ) {
        setStatusClickListener {
            stakingViewState.statusClicked()
        }

        setStakeInfoClickListener {
            stakingViewState.moreActionsClicked()
        }

        stakingViewState.showStatusAlertEvent.observeEvent { (title, message) ->
            showStatusAlert(title, message)
        }

        moreActions.setVisible(stakingViewState.manageStakingActionsButtonVisible)

        stakingViewState.showManageActionsEvent.observeEvent {
            ManageStakingBottomSheet(requireContext(), it, stakingViewState::manageActionChosen).show()
        }

        stakingViewState.stakeSummaryFlow.observe { summaryState ->
            when (summaryState) {
                is LoadingState.Loaded<StakeSummaryModel<S>> -> {
                    val summary = summaryState.data

                    hideLoading()
                    setElectionStatus(mapStatus(summary))
                    setTotalStaked(summary.totalStaked)
                    setTotalRewards(summary.totalRewards)

                    if (summary.totalStakedFiat == null) {
                        hideTotalStakeFiat()
                    } else {
                        showTotalStakedFiat()
                        setTotalStakedFiat(summary.totalStakedFiat)
                    }

                    if (summary.totalRewardsFiat == null) {
                        hideTotalRewardsFiat()
                    } else {
                        showTotalRewardsFiat()
                        setTotalRewardsFiat(summary.totalRewardsFiat)
                    }
                }
                is LoadingState.Loading -> {}
            }
        }
    }

    private fun showStatusAlert(title: String, message: String) {
        infoDialog(requireContext()) {
            setTitle(title)
            setMessage(message)
        }
    }

    private fun mapNominatorStatus(summary: NominatorSummaryModel): StakeSummaryView.Status {
        return when (summary.status) {
            is NominatorStatus.Inactive -> StakeSummaryView.Status.Inactive(summary.currentEraDisplay)
            NominatorStatus.Active -> StakeSummaryView.Status.Active(summary.currentEraDisplay)
            is NominatorStatus.Waiting -> StakeSummaryView.Status.Waiting(summary.status.timeLeft)
        }
    }

    private fun mapValidatorStatus(summary: ValidatorSummaryModel): StakeSummaryView.Status {
        return when (summary.status) {
            ValidatorStatus.INACTIVE -> StakeSummaryView.Status.Inactive(summary.currentEraDisplay)
            ValidatorStatus.ACTIVE -> StakeSummaryView.Status.Active(summary.currentEraDisplay)
        }
    }

    private fun mapStashNoneStatus(summary: StashNoneSummaryModel): StakeSummaryView.Status {
        return when (summary.status) {
            StashNoneStatus.INACTIVE -> StakeSummaryView.Status.Inactive(summary.currentEraDisplay)
        }
    }
}
