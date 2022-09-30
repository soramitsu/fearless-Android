package jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm

import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmRebondBinding
import javax.inject.Inject

@AndroidEntryPoint
class ConfirmRebondFragment : BaseFragment<ConfirmRebondViewModel>(R.layout.fragment_confirm_rebond) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentConfirmRebondBinding::bind)

    override val viewModel: ConfirmRebondViewModel by viewModels()

    companion object {
        const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(payload: ConfirmRebondPayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    override fun initViews() {
        with(binding) {
            confirmRebondToolbar.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            confirmRebondOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

            confirmRebondToolbar.setHomeButtonListener { viewModel.backClicked() }
            confirmRebondConfirm.prepareForProgress(viewLifecycleOwner)
            confirmRebondConfirm.setOnClickListener { viewModel.confirmClicked() }
        }
    }

    override fun subscribe(viewModel: ConfirmRebondViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)
        observeRetries(viewModel)

        viewModel.showNextProgress.observe(binding.confirmRebondConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            binding.confirmRebondAmount.setAssetBalance(it.assetBalance)
            binding.confirmRebondAmount.setAssetName(it.tokenName)
            binding.confirmRebondAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        binding.confirmRebondAmount.amountInput.setText(viewModel.amount)

        viewModel.amountFiatFLow.observe {
            it?.let(binding.confirmRebondAmount::setAssetBalanceFiatAmount)
        }

        viewModel.feeLiveData.observe(binding.confirmRebondFee::setFeeStatus)

        viewModel.originAddressModelLiveData.observe {
            binding.confirmRebondOriginAccount.setMessage(it.nameOrAddress)
            binding.confirmRebondOriginAccount.setTextIcon(it.image)
        }
    }
}
