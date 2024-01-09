package jp.co.soramitsu.staking.impl.presentation.staking.unbond.select

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.hideSoftKeyboard
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSelectUnbondBinding

@AndroidEntryPoint
class SelectUnbondFragment : BaseFragment<SelectUnbondViewModel>(R.layout.fragment_select_unbond) {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    override val viewModel: SelectUnbondViewModel by viewModels()

    companion object {
        const val PAYLOAD_KEY = "PAYLOAD_KEY"

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

    override fun subscribe(viewModel: SelectUnbondViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        setupComposeViews()

        viewModel.showNextProgress.observe(binding.unbondContinue::setProgress)
        viewModel.showNextProgress.observe(binding.unbondConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            binding.unbondAmount.setAssetBalance(it.assetBalance)
            binding.unbondAmount.setAssetName(it.tokenName)
            binding.unbondAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        binding.unbondAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)
        binding.unbondAmount.amountInput.setOnFocusChangeListener { v, hasFocus ->
            viewModel.onAmountInputFocusChanged(hasFocus)
        }

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

    private fun setupComposeViews() {
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
                            modifier = Modifier
                                .imePadding(),
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
}
