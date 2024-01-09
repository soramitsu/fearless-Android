package jp.co.soramitsu.staking.impl.presentation.confirm

import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmStakeBinding
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeViews
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.displayFeeStatus
import javax.inject.Inject

@AndroidEntryPoint
class ConfirmStakingFragment : BaseFragment<ConfirmStakingViewModel>(R.layout.fragment_confirm_stake) {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentConfirmStakeBinding::bind)

    override val viewModel: ConfirmStakingViewModel by viewModels()

    override fun initViews() {
        binding.stakingConfirmationContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        binding.confirmStakeToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        with(binding) {
            confirmStakeOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

            confirmStakeConfirm.prepareForProgress(viewLifecycleOwner)
            confirmStakeConfirm.setOnClickListener { viewModel.confirmClicked() }

            confirmStakeSelectedValidators.setOnClickListener { viewModel.nominationsClicked() }

            confirmStakeRewardDestination.setPayoutAccountClickListener { viewModel.payoutAccountClicked() }

            confirmStakeSelectedCollator.setWholeClickListener { viewModel.onSelectedCollatorCopyClicked() }
        }
    }

    override fun subscribe(viewModel: ConfirmStakingViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(binding.confirmStakeConfirm::setProgress)

        viewModel.rewardDestinationLiveData.observe {
            if (it != null) {
                binding.confirmStakeRewardDestination.makeVisible()
                binding.confirmStakeRewardDestination.showRewardDestination(it)
            } else {
                binding.confirmStakeRewardDestination.makeGone()
            }
        }

        viewModel.assetModelLiveData.observe {
            binding.confirmStakeAmount.setAssetBalance(it.assetBalance)
            binding.confirmStakeAmount.setAssetName(it.tokenName)
            binding.confirmStakeAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        viewModel.feeLiveData.observe {
            displayFeeStatus(
                it,
                FeeViews(
                    binding.confirmStakingFeeProgress,
                    binding.confirmStakingFeeFiat,
                    binding.confirmStakingFeeToken
                )
            )
        }

        viewModel.currentAccountModelLiveData.observe {
            binding.confirmStakeOriginAccount.setMessage(it.nameOrAddress)
            binding.confirmStakeOriginAccount.setTextIcon(it.image)
        }

        viewModel.nominationsLiveData.observe {
            binding.confirmStakeSelectedValidatorsCount.text = it
        }

        viewModel.displayAmountLiveData.observe { bondedAmount ->
            binding.confirmStakeAmount.setVisible(bondedAmount != null)

            bondedAmount?.let { binding.confirmStakeAmount.amountInput.setText(it.formatCrypto()) }
        }

        viewModel.unstakingTime.observe {
            binding.confirmStakingUnstakingPeriodLength.text = it
        }

        viewModel.eraHoursLength.observe {
            binding.confirmStakingEachEraLength.text = it
        }

        viewModel.selectedCollatorLiveData.observe {
            binding.confirmStakeSelectedCollator.isVisible = it != null
            binding.confirmStakeSelectedValidators.isVisible = it == null
            it?.let { model ->
                binding.confirmStakeSelectedCollator.setMessage(model.address)

                binding.confirmStakeSelectedCollator.setLabel(model.nameOrAddress)

                binding.confirmStakeSelectedCollator.loadIcon(model.image)
            }
        }
    }
}
