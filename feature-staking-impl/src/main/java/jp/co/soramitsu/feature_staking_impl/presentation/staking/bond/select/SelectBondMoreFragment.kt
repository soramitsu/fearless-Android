package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select

import android.os.Bundle
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
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentBondMoreBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import javax.inject.Inject

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class SelectBondMoreFragment : BaseFragment<SelectBondMoreViewModel>(R.layout.fragment_bond_more) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentBondMoreBinding::bind)

    companion object {

        fun getBundle(payload: SelectBondMorePayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    override fun initViews() {
        binding.bondMoreContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        binding.bondMoreToolbar.setHomeButtonListener { viewModel.backClicked() }
        binding.bondMoreContinue.prepareForProgress(viewLifecycleOwner)
        binding.bondMoreContinue.setOnClickListener { viewModel.nextClicked() }
        binding.bondMoreConfirm.prepareForProgress(viewLifecycleOwner)
        binding.bondMoreConfirm.setOnClickListener { viewModel.confirmClicked() }
    }

    override fun inject() {
        val payload = argument<SelectBondMorePayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectBondMoreFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: SelectBondMoreViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)

        viewModel.showNextProgress.observe(binding.bondMoreContinue::setProgress)
        viewModel.showNextProgress.observe(binding.bondMoreConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            binding.bondMoreAmount.setAssetBalance(it.assetBalance)
            binding.bondMoreAmount.setAssetName(it.tokenName)
            binding.bondMoreAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        binding.bondMoreAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(binding.bondMoreAmount::setAssetBalanceFiatAmount)
        }

        viewModel.feeLiveData.observe {
            binding.bondMoreFee.setFeeStatus(it)
            binding.bondMoreConfirmFee.setFeeStatus(it)
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

        viewModel.oneScreenConfirmation.let { showConfirm ->
            binding.stakingMoreRewardHint.isVisible = showConfirm
            binding.confirmBondMoreLayout.isVisible = showConfirm
            binding.bondMoreFee.isGone = showConfirm
            binding.bondMoreContinue.isGone = showConfirm
        }
    }
}
