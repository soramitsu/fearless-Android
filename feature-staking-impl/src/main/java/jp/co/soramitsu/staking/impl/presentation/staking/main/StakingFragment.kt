package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
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
import jp.co.soramitsu.common.utils.hideSoftKeyboard
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.dialog.infoDialog
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentStakingBinding
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.CollatorStakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarnings
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.EstimatedEarningsViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingAssetInfo
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingAssetInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingParachainInfo
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingPoolInfo
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakingRelayChainInfo
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.staking.impl.presentation.view.DelegationOptionsBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class StakingFragment : BaseFragment<StakingViewModel>(R.layout.fragment_staking) {

    @Inject
    lateinit var imageLoader: ImageLoader

    override val viewModel: StakingViewModel by viewModels()

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

    }

    private var observeAlertsJob: Job? = null

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
        viewModel.showManageStakeEvent.observeEvent {
            ManageStakingBottomSheet(requireContext(), it, viewModel::onManageStakeActionChosen).show()
        }
        viewModel.showStakingStatusEvent.observeEvent {
            showStatusAlert(it.title, it.message)
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
                            if (it.networkInfoState is StakingAssetInfoViewState.StakingPool) {
                                StakingAssetInfo(it.networkInfoState)
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
                                            viewModel::onPoolsAmountInput,
                                            viewModel::onAmountInputFocusChanged
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
                                    is StakingViewState.RelayChain,
                                    is StakingViewState.Parachain -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }

        binding.nonPoolStakingBody.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.state.collectAsState()
                state?.stakingViewState?.let { stakingState ->
                    FearlessAppTheme {
                        NonPoolStakingBody(stakingState)
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

    }

    @Composable
    private fun NonPoolStakingBody(state: StakingViewState) {
        Column(modifier = Modifier.padding(horizontal = Dp(16f))) {
            when (state) {
                is StakingViewState.RelayChain.Welcome -> WelcomeStakingBody(state.estimatedEarnings)
                is StakingViewState.RelayChain.Stake -> {
                    StakingRelayChainInfo(
                        state = state.stakeInfoViewState,
                        onClick = { viewModel.onManageRelayChainStake(state) },
                        onStatusClick = { viewModel.onRelayChainStatusClick(state) }
                    )
                    MarginVertical(margin = Dp(8f))
                }
                is StakingViewState.Parachain.Welcome -> WelcomeStakingBody(state.estimatedEarnings)
                is StakingViewState.Parachain.Delegator -> {
                    state.delegations.forEach { delegation ->
                        StakingParachainInfo(delegation.stakeInfo) {
                            showDelegatorOptions(delegation)
                        }
                        MarginVertical(margin = Dp(8f))
                    }
                    WelcomeStakingBody(state.estimatedEarnings)
                }
                is StakingViewState.Pool -> Unit
            }
        }
    }

    @Composable
    private fun WelcomeStakingBody(state: EstimatedEarningsViewState) {
        EstimatedEarnings(
            state,
            viewModel::onEstimatedEarningsInfoClick,
            viewModel::onPoolsAmountInput,
            viewModel::onAmountInputFocusChanged
        )
        MarginVertical(margin = Dp(16f))
        AccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dp(52f)),
            text = stringResource(id = R.string.staking_start_title),
            onClick = viewModel::startNonPoolStakingClick
        )
        MarginVertical(margin = Dp(16f))
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

    private fun showStatusAlert(title: String, message: String) {
        infoDialog(requireContext(), childFragmentManager, title, message)
    }

    private fun showDelegatorOptions(model: CollatorStakeInfoViewState) {
        DelegationOptionsBottomSheet(
            context = requireContext(),
            onStakingBalance = { viewModel.onStakingBalance(model) },
            onYourCollator = model.collator?.let { { viewModel.openCollatorInfo(model) } }
        ).show()
    }

    override fun onStop() {
        super.onStop()
        observeAlertsJob?.cancel()
    }
}
