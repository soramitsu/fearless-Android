package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AssetSelector
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.hideSoftKeyboard
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.dialog.infoDialog
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentStakingBinding
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.domain.model.NominatorStatus
import jp.co.soramitsu.staking.impl.domain.model.StashNoneStatus
import jp.co.soramitsu.staking.impl.domain.model.ValidatorStatus
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarnings
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingAssetInfo
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingPoolInfo
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.presentation.view.DelegationOptionsBottomSheet
import jp.co.soramitsu.staking.impl.presentation.view.DelegationRecyclerViewAdapter
import jp.co.soramitsu.staking.impl.presentation.view.StakeSummaryView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StakingFragment : BaseFragment<StakingViewModel>(R.layout.fragment_staking), DelegationRecyclerViewAdapter.DelegationHandler {

    @Inject
    protected lateinit var imageLoader: ImageLoader
    private val delegationAdapter by lazy { DelegationRecyclerViewAdapter(this) }

    override val viewModel: StakingViewModel by viewModels()

    private val binding by viewBinding(FragmentStakingBinding::bind)

    var currentEnteredAmountFlow = MutableStateFlow("")

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

    private var observeDelegationsJob: Job? = null
    private var observeAlertsJob: Job? = null
    private var observeStakingStateJob: Job? = null

    override fun subscribe(viewModel: StakingViewModel) {
        observeValidations(viewModel)
        setupComposeViews()
        observeAlertsJob?.cancel()
        observeAlertsJob = viewModel.alertsFlow.onEach { loadingState ->
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
                    binding.stakingAlertsInfo.showLoading()
                }
            }
        }.launchIn(viewModel.stakingStateScope)

        viewModel.stakingViewStateOld.observe { loadingState ->
            observeDelegationsJob?.cancel()
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
                        is StakingPoolWelcomeViewState -> {
                            binding.stakingEstimate.setVisible(false)
                            binding.stakingStakeSummary.setVisible(false)
                            binding.collatorsList.setVisible(false)
                        }
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
                            observeDelegationsJob = stakingState.delegations.onEach {
                                if (it is LoadingState.Loaded) {
                                    delegationAdapter.submitList(it.data)
                                }
                            }.launchIn(viewModel.stakingStateScope)
                            observeDelegationsJob?.start()

                            observeWelcomeState(stakingState.welcomeViewState)

                            binding.stakingStakeSummary.isVisible = false
                            binding.stakingEstimate.isVisible = true
                            binding.startStakingBtn.isVisible = true
                        }
                        is Pool -> {
                            binding.stakingEstimate.setVisible(false)
                            binding.stakingStakeSummary.setVisible(false)
                            binding.collatorsList.setVisible(false)
                        }
                    }
                }
            }
        }

        combine(viewModel.networkInfo, viewModel.stakingTypeFlow) { state, stakingType ->
            state to stakingType
        }.distinctUntilChanged().observe { (state, stakingType) ->
            when {
                state is LoadingState.Loading<*> && stakingType == StakingType.RELAYCHAIN -> {
                    binding.parachainStakingNetworkInfo.isVisible = false
                    binding.stakingNetworkInfo.isVisible = true
                    binding.stakingNetworkInfo.showLoading()
                }
                state is LoadingState.Loading<*> && stakingType == StakingType.PARACHAIN -> {
                    binding.stakingNetworkInfo.isVisible = false
                    binding.parachainStakingNetworkInfo.isVisible = true
                    binding.parachainStakingNetworkInfo.showLoading()
                }
                stakingType == StakingType.POOL -> {
                    binding.stakingNetworkInfo.isVisible = false
                    binding.parachainStakingNetworkInfo.isVisible = false
                }
                state is LoadingState.Loaded<StakingNetworkInfoModel> -> {
                    when (val model = state.data) {
                        is StakingNetworkInfoModel.Parachain -> {
                            setupNetworkInfo(model)
                        }
                        is StakingNetworkInfoModel.RelayChain -> {
                            setupNetworkInfo(model)
                        }
                        is StakingNetworkInfoModel.Pool -> Unit
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

    private fun setupComposeViews() {
        viewModel.showRewardEstimationEvent.observeEvent {
            StakingRewardEstimationBottomSheet(requireContext(), it).show()
        }
        binding.composeContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.state.collectAsState()
                state?.let {
                    FearlessAppTheme {
                        Column(modifier = Modifier.padding(horizontal = Dp(16f))) {
                            AssetSelector(state = it.selectorState, onClick = { viewModel.assetSelectorMixin.assetSelectorClicked() })
                            MarginVertical(margin = Dp(16f))
                            it.networkInfoState?.let { networkState ->
                                StakingAssetInfo(networkState)
                            }
                            it.stakingViewState?.let { stakingViewState ->
                                when (stakingViewState) {
                                    is StakingViewState.Pool.PoolMember -> {
                                        MarginVertical(margin = Dp(16f))
                                        StakingPoolInfo(
                                            stakingViewState.stakeInfoViewState,
                                            onClick = viewModel::onManagePoolStake
                                        )
                                    }
                                    is StakingViewState.Pool.Welcome -> {
                                        MarginVertical(margin = Dp(16f))
                                        EstimatedEarnings(
                                            stakingViewState.estimatedEarnings,
                                            viewModel::onEstimatedEarningsInfoClick,
                                            viewModel::onPoolsAmountInput
                                        )
                                        MarginVertical(margin = Dp(16f))
                                        Spacer(modifier = Modifier.weight(1f, fill = true))
                                        AccentButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(Dp(52f)),
                                            text = stringResource(id = R.string.staking_start_title),
                                            onClick = viewModel::startStakingPoolClick
                                        )
                                        MarginVertical(margin = Dp(16f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        viewModel.assetSelectorMixin.showAssetChooser.observeEvent {
            StakingAssetSelectorBottomSheet(
                imageLoader = imageLoader,
                context = requireContext(),
                payload = it,
                onClicked = viewModel.assetSelectorMixin::assetChosen
            ).show()
        }

        binding.quickInput.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isInputFocused by viewModel.isInputFocused.collectAsState()
                val bottom = WindowInsets.ime.getBottom(LocalDensity.current)

                val isSoftKeyboardOpen = bottom > 0

                val isShowQuickInput = isInputFocused && isSoftKeyboardOpen

                FearlessAppTheme {
                    if (isShowQuickInput) {
                        QuickInput(
                            values = QuickAmountInput.values(),
                            onQuickAmountInput = {
                                hideSoftKeyboard()
                                viewModel.onQuickAmountInput(it)
                            },
                            onDoneClick = ::hideSoftKeyboard
                        )
                    }
                }
            }
        }

        viewModel.enteredAmountEvent.observeEvent {
            viewModel.launch {
                currentEnteredAmountFlow.emit(it)
            }
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

        observeStakingStateJob?.cancel()
        observeStakingStateJob = stakingViewState.stakeSummaryFlow.onEach { summaryState ->
            when (summaryState) {
                is LoadingState.Loaded<StakeSummaryModel<S>> -> {
                    val summary = summaryState.data

                    hideLoading()
                    setElectionStatus(mapStatus(summary))
                    setTotalStaked(summary.totalStaked)
                    setRewardsApr(summary.totalRewards)
                    showRewardsAprFiat()
                    summary.totalRewardsFiat?.let { setRewardsAprFiat(it) } ?: hideRewardsAprFiat()
                    if (summary.totalStakedFiat == null) {
                        hideTotalStakeFiat()
                    } else {
                        showTotalStakedFiat()
                        setTotalStakedFiat(summary.totalStakedFiat)
                    }
                }
                is LoadingState.Loading -> {}
            }
        }.launchIn(viewModel.stakingStateScope)
    }

    private fun showStatusAlert(title: String, message: String) {
        infoDialog(requireContext(), childFragmentManager, title, message)
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
        return when (summary.status) {
            is NominatorStatus.Inactive -> StakeSummaryView.Status.Inactive(summary.currentEraDisplay)
            NominatorStatus.Active -> StakeSummaryView.Status.Active(summary.currentEraDisplay)
            is NominatorStatus.Waiting -> StakeSummaryView.Status.Waiting(summary.status.timeLeft)
        }
    }

    private var returnsJob: Job? = null

    private fun observeWelcomeState(stakingState: WelcomeViewState) {
        returnsJob?.cancel()
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

        returnsJob = stakingState.returns.onEach { rewards ->
            binding.stakingEstimate.hideReturnsLoading()
            binding.stakingEstimate.populateMonthEstimation(rewards.monthly)
            binding.stakingEstimate.populateYearEstimation(rewards.yearly)
        }.launchIn(viewModel.stakingStateScope)
        returnsJob?.start()

        currentEnteredAmountFlow = stakingState.enteredAmountFlow
        binding.stakingEstimate.amountInput.bindTo(stakingState.enteredAmountFlow, viewLifecycleOwner.lifecycleScope)
        binding.stakingEstimate.amountInput.setOnFocusChangeListener { v, hasFocus ->
            viewModel.onAmountInputFocusChanged(hasFocus)
        }

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

    override fun onStop() {
        super.onStop()
        returnsJob?.cancel()
        observeDelegationsJob?.cancel()
        observeAlertsJob?.cancel()
    }
}
