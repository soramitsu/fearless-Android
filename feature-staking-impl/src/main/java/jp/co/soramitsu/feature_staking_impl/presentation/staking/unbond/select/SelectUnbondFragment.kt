package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select

import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSelectUnbondBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import javax.inject.Inject

class SelectUnbondFragment : BaseFragment<SelectUnbondViewModel>(R.layout.fragment_select_unbond) {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(payload: SelectUnbondPayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    private val binding by viewBinding(FragmentSelectUnbondBinding::bind)

    override fun initViews() {
        with(binding) {
            unbondContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }

                consume(true)
            }

            unbondToolbar.setHomeButtonListener { viewModel.backClicked() }
            unbondContinue.prepareForProgress(viewLifecycleOwner)
            unbondContinue.setOnClickListener { viewModel.nextClicked() }
            unbondConfirm.prepareForProgress(viewLifecycleOwner)
            unbondConfirm.setOnClickListener { viewModel.confirmClicked() }
        }
    }

    override fun inject() {
        val payload = argument<SelectUnbondPayload>(PAYLOAD_KEY)
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectUnbondFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: SelectUnbondViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)

        viewModel.showNextProgress.observe(binding.unbondContinue::setProgress)

        viewModel.assetModelFlow.observe {
            binding.unbondAmount.setAssetBalance(it.assetBalance)
            binding.unbondAmount.setAssetName(it.tokenName)
            binding.unbondAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        binding.unbondAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(binding.unbondAmount::setAssetBalanceFiatAmount)
        }

        viewModel.feeLiveData.observe {
            binding.unbondFee.setFeeStatus(it)
            binding.unbondConfirmFee.setFeeStatus(it)
        }

        viewModel.lockupPeriodLiveData.observe {
            binding.unbondPeriod.showValue(it)
            binding.unbondConfirmPeriod.showValue(it)
        }

        viewModel.collatorLiveData.observe {
            it.ifPresent {
                binding.collatorAddressView.isVisible = true
                binding.collatorAddressView.setMessage(it.nameOrAddress)
                binding.collatorAddressView.setTextIcon(it.image)
            }
        }

        viewModel.accountLiveData.observe {
            it.ifPresent {
                binding.accountAddressView.isVisible = true
                binding.accountAddressView.setMessage(it.nameOrAddress)
                binding.accountAddressView.setTextIcon(it.image)
            }
        }

        viewModel.unbondHint.observe {
            binding.unbondHint.text = it
        }

        viewModel.oneScreenConfirmation.let { showConfirm ->
            binding.confirmUnbondLayout.isVisible = showConfirm
            binding.unbondConfirmPeriod.isVisible = showConfirm
            binding.unbondPeriod.isGone = showConfirm
            binding.unbondFee.isGone = showConfirm
            binding.unbondContinue.isGone = showConfirm
        }
    }
}
