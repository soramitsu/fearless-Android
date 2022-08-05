package jp.co.soramitsu.feature_staking_impl.presentation.setup

import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSetupStakingBinding
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.observeRewardDestinationChooser
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeViews
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.displayFeeStatus
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

@AndroidEntryPoint
class SetupStakingFragment : BaseFragment<SetupStakingViewModel>(R.layout.fragment_setup_staking) {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentSetupStakingBinding::bind)

    override val viewModel: SetupStakingViewModel by viewModels()

    override fun initViews() {
        with(binding) {
            setupStakingContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }

                consume(true)
            }

            setupStakingToolbar.setHomeButtonListener { viewModel.backClicked() }
            onBackPressed { viewModel.backClicked() }

            setupStakingNext.prepareForProgress(viewLifecycleOwner)
            setupStakingNext.setOnClickListener { viewModel.nextClicked() }
            setupStakingPayoutViewer.setOnViewMoreClickListener { viewModel.learnMoreClicked(viewModel) }
        }
    }

    override fun subscribe(viewModel: SetupStakingViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        observeBrowserEvents(viewModel)
        observeRewardDestinationChooser(viewModel, binding.setupStakingRewardDestinationChooser)

        viewModel.showNextProgress.observe(binding.setupStakingNext::setProgress)

        viewModel.assetModelsFlow.observe {
            binding.setupStakingAmountField.setAssetBalance(it.assetBalance)
            binding.setupStakingAmountField.setAssetName(it.tokenName)
            binding.setupStakingAmountField.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        binding.setupStakingAmountField.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(binding.setupStakingAmountField::setAssetBalanceFiatAmount)
        }

        viewModel.feeLiveData.observe {
            displayFeeStatus(
                it,
                FeeViews(
                    binding.setupStakingFeeProgress,
                    binding.setupStakingFeeFiat,
                    binding.setupStakingFeeToken
                )
            )
        }

        viewModel.showMinimumStakeAlert.observeEvent {
            AlertBottomSheet.Builder(requireContext())
                .setTitle(R.string.staking_inactive_bond)
                .setMessage(resources.getString(R.string.min_staking_warning_text, it))
                .setButtonText(R.string.common_confirm)
                .callback { viewModel.minimumStakeConfirmed() }
                .build()
                .show()
        }

        viewModel.currentAccountAddressModel.observe {
            binding.setupStakingPayoutViewer.setAccountInfo(it)
        }

        viewModel.rewardReturnsLiveData.observe {
            binding.setupStakingPayoutViewer.setRewardEstimation(it.payout)
        }

        viewModel.currentStakingType.observe {
            binding.setupStakingRewardDestinationChooser.isVisible = it == Chain.Asset.StakingType.RELAYCHAIN
            binding.setupStakingPayoutViewer.isVisible = it == Chain.Asset.StakingType.PARACHAIN
        }
    }
}
