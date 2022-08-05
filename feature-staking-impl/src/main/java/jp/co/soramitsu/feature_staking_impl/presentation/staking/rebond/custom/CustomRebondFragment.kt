package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.custom

import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentRebondCustomBinding
import javax.inject.Inject

@AndroidEntryPoint
class CustomRebondFragment : BaseFragment<CustomRebondViewModel>(R.layout.fragment_rebond_custom) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentRebondCustomBinding::bind)

    override val viewModel: CustomRebondViewModel by viewModels()

    override fun initViews() {
        with(binding) {
            rebondToolbar.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            rebondToolbar.setHomeButtonListener { viewModel.backClicked() }
            rebondContinue.prepareForProgress(viewLifecycleOwner)
            rebondContinue.setOnClickListener { viewModel.confirmClicked() }
        }
    }

    override fun subscribe(viewModel: CustomRebondViewModel) {
        observeValidations(viewModel)
        observeRetries(viewModel)

        viewModel.showNextProgress.observe(binding.rebondContinue::setProgress)

        viewModel.assetModelFlow.observe {
            binding.rebondAmount.setAssetBalance(it.assetBalance)
            binding.rebondAmount.setAssetName(it.tokenName)
            binding.rebondAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        binding.rebondAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.amountFiatFLow.observe {
            it?.let(binding.rebondAmount::setAssetBalanceFiatAmount)
        }

        viewModel.feeLiveData.observe(binding.rebondFee::setFeeStatus)
    }
}
