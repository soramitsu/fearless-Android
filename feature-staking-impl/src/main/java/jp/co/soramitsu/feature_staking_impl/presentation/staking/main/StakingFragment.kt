package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
import jp.co.soramitsu.feature_staking_impl.presentation.view.DelegationOptionsBottomSheet
import jp.co.soramitsu.feature_staking_impl.presentation.view.DelegationRecyclerViewAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.view.StakeSummaryView
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.setupAssetSelector
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class StakingFragment : BaseFragment<StakingViewModel>(R.layout.fragment_staking), DelegationRecyclerViewAdapter.DelegationHandler {

    @Inject
    protected lateinit var imageLoader: ImageLoader
    private val delegationAdapter by lazy { DelegationRecyclerViewAdapter(this) }

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

            parachainStakingNetworkInfo.storyItemHandler = {
                viewModel.storyClicked(StoryGroupModel(it.elements))
            }
        }

        binding.collatorsList.layoutManager = object : LinearLayoutManager(requireContext()) {
            override fun canScrollVertically() = false
        }
        binding.collatorsList.adapter = delegationAdapter
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
            if (loadingState is LoadingState.Loaded) {
                binding.stakingAlertsInfo.hideLoading()

                if (loadingState.data.isEmpty()) {
                    binding.stakingAlertsInfo.makeGone()
                } else {
                    binding.stakingAlertsInfo.makeVisible()
                    binding.stakingAlertsInfo.setStatus(loadingState.data)
                }
            }
        }

        viewModel.stakingViewState.observe { loadingState ->
            when (loadingState) {
                is LoadingState.Loading -> {
                    binding.startStakingBtn.setVisible(false)
                    binding.stakingEstimate.setVisible(false)
                    binding.stakingStakeSummary.setVisible(false)
                    binding.collatorsList.setVisible(false)
                }
                is LoadingState.Loaded -> {
                    val stakingState = loadingState.data

                    val isEstimatesVisible = stakingState is RelaychainWelcomeViewState || stakingState is ParachainWelcomeViewState

                    binding.startStakingBtn.setVisible(isEstimatesVisible)
                    binding.stakingEstimate.setVisible(isEstimatesVisible)
                    binding.stakingStakeSummary.setVisible(stakingState is StakeViewState<*>)
                    binding.collatorsList.setVisible(stakingState is DelegatorViewState)

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
                            observeWelcomeState(stakingState)
                        }
                        is DelegatorViewState -> {
                            stakingState.delegations.onEach {
                                if (it is LoadingState.Loaded) {
                                    delegationAdapter.submitList(it.data)
                                }
                            }.launchIn(viewModel.stakingStateScope)

                            observeWelcomeState(stakingState.welcomeViewState)

                            binding.stakingStakeSummary.isVisible = false
                            binding.stakingEstimate.isVisible = true
                            binding.startStakingBtn.isVisible = true
                        }
                    }
                }
            }
        }

        combine(viewModel.networkInfo, viewModel.assetSelectorMixin.selectedAssetFlow) { state, asset ->
            state to asset.token.configuration.staking
        }.distinctUntilChanged().observe { (state, stakingType) ->
            when {
                state is LoadingState.Loading<*> && stakingType == Chain.Asset.StakingType.RELAYCHAIN -> {
                    binding.parachainStakingNetworkInfo.isVisible = false
                    binding.stakingNetworkInfo.isVisible = true
                    binding.stakingNetworkInfo.showLoading()
                }
                state is LoadingState.Loading<*> && stakingType == Chain.Asset.StakingType.PARACHAIN -> {
                    binding.stakingNetworkInfo.isVisible = false
                    binding.parachainStakingNetworkInfo.isVisible = true
                    binding.parachainStakingNetworkInfo.showLoading()
                }
                state is LoadingState.Loaded<StakingNetworkInfoModel> -> {
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
            binding.stakingNetworkInfo.submitStories(it)
            binding.parachainStakingNetworkInfo.submitStories(it)
        }

        viewModel.networkInfoTitle.observe {
            binding.stakingNetworkInfo.setTitle(it)
            binding.parachainStakingNetworkInfo.setTitle(it)
        }

        viewModel.currentAddressModelLiveData.observe {
            binding.stakingAvatar.setImageDrawable(it.image)
        }
    }

    private fun setupNetworkInfo(model: StakingNetworkInfoModel.RelayChain) {
        binding.stakingNetworkInfo.isVisible = true
        binding.parachainStakingNetworkInfo.isVisible = false
        with(binding.stakingNetworkInfo) {
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
        binding.stakingNetworkInfo.isVisible = false
        binding.parachainStakingNetworkInfo.isVisible = true
        with(binding.parachainStakingNetworkInfo) {
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

    private fun observeWelcomeState(stakingState: WelcomeViewState) {
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

    override fun moreClicked(model: DelegatorViewState.CollatorDelegationModel) {
        showDelegatorOptions(model)
    }

    private fun showDelegatorOptions(model: DelegatorViewState.CollatorDelegationModel) {
        DelegationOptionsBottomSheet(
            context = requireContext(),
            model = model,
            onStakingBalance = viewModel::onStakingBalance,
            onYourCollator = viewModel::openCollatorInfo
        ).show()
    }
}
