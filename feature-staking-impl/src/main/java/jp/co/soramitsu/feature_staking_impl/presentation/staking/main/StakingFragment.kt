package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
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
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.StashNoneStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.ValidatorStatus
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.feature_staking_impl.presentation.view.StakeSummaryView
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.setupAssetSelector
import kotlinx.android.synthetic.main.fragment_staking.parachainStakingNetworkInfo
import kotlinx.android.synthetic.main.fragment_staking.stakingAlertsInfo
import kotlinx.android.synthetic.main.fragment_staking.stakingAssetSelector
import kotlinx.android.synthetic.main.fragment_staking.stakingAvatar
import kotlinx.android.synthetic.main.fragment_staking.stakingContainer
import kotlinx.android.synthetic.main.fragment_staking.stakingEstimate
import kotlinx.android.synthetic.main.fragment_staking.stakingNetworkInfo
import kotlinx.android.synthetic.main.fragment_staking.stakingStakeSummary
import kotlinx.android.synthetic.main.fragment_staking.startStakingBtn
import javax.inject.Inject

class StakingFragment : BaseFragment<StakingViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

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

        stakingEstimate.hideAssetBalanceFiatAmount()

        stakingAvatar.setOnClickListener {
            viewModel.avatarClicked()
        }

        stakingNetworkInfo.storyItemHandler = {
            viewModel.storyClicked(StoryGroupModel(it.elements))
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
        setupAssetSelector(stakingAssetSelector, viewModel, imageLoader)

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
                    stakingAlertsInfo.makeVisible()
                    stakingAlertsInfo.showLoading()
                }
            }
        }

        viewModel.stakingViewState.observe { loadingState ->
            when (loadingState) {
                is LoadingState.Loading -> {
                    startStakingBtn.setVisible(false)
                    stakingEstimate.setVisible(false)
                    stakingStakeSummary.setVisible(false)
                }
                is LoadingState.Loaded -> {
                    val stakingState = loadingState.data

                    val isEstimatesVisible = stakingState is RelaychainWelcomeViewState || stakingState is ParachainWelcomeViewState

                    startStakingBtn.setVisible(isEstimatesVisible)
                    stakingEstimate.setVisible(isEstimatesVisible)
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

                        is RelaychainWelcomeViewState -> {
                            observeValidations(stakingState)

                            stakingState.assetLiveData.observe {
                                stakingEstimate.setAssetImageUrl(it.imageUrl, imageLoader)
                                stakingEstimate.setAssetName(it.tokenName)
                                stakingEstimate.setAssetBalance(it.assetBalance)
                            }

                            stakingState.amountFiat.observe { amountFiat ->
                                stakingEstimate.showAssetBalanceFiatAmount()
                                stakingEstimate.setAssetBalanceFiatAmount(amountFiat)
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
                        is ParachainWelcomeViewState -> {
                            observeValidations(stakingState)

                            stakingState.assetLiveData.observe {
                                stakingEstimate.setAssetImageUrl(it.imageUrl, imageLoader)
                                stakingEstimate.setAssetName(it.tokenName)
                                stakingEstimate.setAssetBalance(it.assetBalance)
                            }

                            stakingState.amountFiat.observe { amountFiat ->
                                stakingEstimate.showAssetBalanceFiatAmount()
                                stakingEstimate.setAssetBalanceFiatAmount(amountFiat)
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
                        is DelegatorViewState -> {
                        }
                    }
                }
            }
        }
        viewModel.networkInfo.observe { state ->
            when (state) {
                is LoadingState.Loading<*> -> {
                    parachainStakingNetworkInfo.showLoading()
                    stakingNetworkInfo.showLoading()
                }

                is LoadingState.Loaded<StakingNetworkInfoModel> -> {
                    when (val model = state.data) {
                        is StakingNetworkInfoModel.Parachain -> {
                            setupNetworkInfo(model)
                        }
                        is StakingNetworkInfoModel.RelayChain -> {
                            setupNetworkInfo(model)
                        }
                    }
                }
            }
        }

        viewModel.stories.observe {
            stakingNetworkInfo.submitStories(it)
            parachainStakingNetworkInfo.submitStories(it)
        }

        viewModel.networkInfoTitle.observe {
            stakingNetworkInfo.setTitle(it)
            parachainStakingNetworkInfo.setTitle(it)
        }

        viewModel.currentAddressModelLiveData.observe {
            stakingAvatar.setImageDrawable(it.image)
        }
    }

    private fun setupNetworkInfo(model: StakingNetworkInfoModel.RelayChain) {
        stakingNetworkInfo.isVisible = true
        parachainStakingNetworkInfo.isVisible = false
        with(stakingNetworkInfo) {
            hideLoading()
            setTotalStake(model.totalStake)
            setNominatorsCount(model.nominatorsCount)
            setMinimumStake(model.minimumStake)
            setLockupPeriod(model.lockupPeriod)
            if (model.totalStakeFiat == null) {
                hideTotalStakeFiat()
            } else {
                showTotalStakeFiat()
                setTotalStakeFiat(model.totalStakeFiat)
            }

            if (model.minimumStakeFiat == null) {
                hideMinimumStakeFiat()
            } else {
                showMinimumStakeFiat()
                setMinimumStakeFiat(model.minimumStakeFiat)
            }
        }
    }

    private fun setupNetworkInfo(model: StakingNetworkInfoModel.Parachain) {
        stakingNetworkInfo.isVisible = false
        parachainStakingNetworkInfo.isVisible = true
        with(parachainStakingNetworkInfo) {
            hideLoading()
            setMinimumStake(model.minimumStake)
            setLockupPeriod(model.lockupPeriod)

            if (model.minimumStakeFiat == null) {
                hideMinimumStakeFiat()
            } else {
                showMinimumStakeFiat()
                setMinimumStakeFiat(model.minimumStakeFiat)
            }
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

    private fun mapNominatorStatus(summary: StakeSummaryModel<NominatorStatus>): StakeSummaryView.Status {
        val currentEraDisplayer = getString(R.string.staking_era_title, summary.currentEraDisplay)
        return when (summary.status) {
            is NominatorStatus.Inactive -> StakeSummaryView.Status.Inactive(currentEraDisplayer)
            NominatorStatus.Active -> StakeSummaryView.Status.Active(currentEraDisplayer)
            is NominatorStatus.Waiting -> StakeSummaryView.Status.Waiting(summary.status.timeLeft)
        }
    }
}
