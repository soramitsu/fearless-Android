package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.dialog.infoDialog
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.StashNoneStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.ValidatorStatus
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.feature_staking_impl.presentation.view.StakeSummaryView
import jp.co.soramitsu.feature_wallet_api.presentation.view.AssetSelectorBottomSheet
import kotlinx.android.synthetic.main.fragment_staking.stakingAlertsInfo
import kotlinx.android.synthetic.main.fragment_staking.stakingAssetSelector
import kotlinx.android.synthetic.main.fragment_staking.stakingAvatar
import kotlinx.android.synthetic.main.fragment_staking.stakingContainer
import kotlinx.android.synthetic.main.fragment_staking.stakingEstimate
import kotlinx.android.synthetic.main.fragment_staking.stakingNetworkInfo
import kotlinx.android.synthetic.main.fragment_staking.stakingStakeSummary
import kotlinx.android.synthetic.main.fragment_staking.startStakingBtn
import javax.inject.Inject
import kotlin.time.ExperimentalTime

class StakingFragment : BaseFragment<StakingViewModel>() {

    @Inject protected lateinit var imageLoader: ImageLoader

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
        }

        stakingEstimate.hideAssetBalanceDollarAmount()

        stakingAvatar.setOnClickListener {
            viewModel.avatarClicked()
        }

        stakingNetworkInfo.storyItemHandler = viewModel::storyClicked

        stakingAssetSelector.onClick {
            viewModel.assetSelectorClicked()
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

    @ExperimentalTime
    override fun subscribe(viewModel: StakingViewModel) {
        observeValidations(viewModel)

        viewModel.showAssetChooser.observe {
            AssetSelectorBottomSheet(
                imageLoader = imageLoader,
                context = requireContext(),
                payload = it,
                onClicked = viewModel::assetSelected
            ).show()
        }

        viewModel.selectedAssetFlow.observe {
            stakingAssetSelector.setState(imageLoader, it)
        }

        viewModel.alertsFlow.observe { loadingState ->
            when (loadingState) {
                is LoadingState.Loaded -> {
                    stakingAlertsInfo.hideLoading()

                    if (loadingState.data.isEmpty()) {
                        stakingAlertsInfo.makeGone()
                    } else {
                        stakingAlertsInfo.makeVisible()
                        stakingAlertsInfo.setStatus(loadingState.data)
                    }
                }

                is LoadingState.Loading -> {
                    stakingAlertsInfo.showLoading()
                }
            }
        }

        viewModel.stakingViewStateFlow.observe { stakingState ->
            startStakingBtn.setVisible(stakingState is WelcomeViewState)
            stakingEstimate.setVisible(stakingState is WelcomeViewState)
            stakingStakeSummary.setVisible(stakingState is StakeViewState<*>)

            when (stakingState) {
                is NominatorViewState -> {
                    stakingStakeSummary.bindStakeSummary(stakingState, ::mapNominatorStatus)
                }

                is ValidatorViewState -> {
                    stakingStakeSummary.bindStakeSummary(stakingState, ::mapValidatorStatus)
                }

                is StashNoneViewState -> {
                    stakingStakeSummary.bindStakeSummary(stakingState, ::mapStashNoneStatus)
                }

                is WelcomeViewState -> {
                    observeValidations(stakingState)

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

                    stakingEstimate.amountInput.bindTo(stakingState.enteredAmountFlow, viewLifecycleOwner.lifecycleScope)

                    startStakingBtn.setOnClickListener { stakingState.nextClicked() }

                    stakingEstimate.infoActions.setOnClickListener { stakingState.infoActionClicked() }

                    stakingState.showRewardEstimationEvent.observeEvent {
                        StakingRewardEstimationBottomSheet(requireContext(), it).show()
                    }
                }
            }
        }

        viewModel.networkInfoStateLiveData.observe { state ->
            when (state) {
                is LoadingState.Loading<*> -> stakingNetworkInfo.showLoading()

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

    @ExperimentalTime
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
