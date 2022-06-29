package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select

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

    @Inject protected lateinit var imageLoader: ImageLoader

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
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectUnbondFactory()
            .create(this)
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

        viewModel.feeLiveData.observe(binding.unbondFee::setFeeStatus)

        viewModel.lockupPeriodLiveData.observe(binding.unbondPeriod::showValue)
    }
}
