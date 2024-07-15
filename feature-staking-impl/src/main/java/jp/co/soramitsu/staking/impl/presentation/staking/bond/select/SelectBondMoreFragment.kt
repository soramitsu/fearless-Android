package jp.co.soramitsu.staking.impl.presentation.staking.bond.select

import android.os.Bundle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentBondMoreBinding

const val PAYLOAD_KEY = "PAYLOAD_KEY"

@AndroidEntryPoint
class SelectBondMoreFragment : BaseFragment<SelectBondMoreViewModel>(R.layout.fragment_bond_more) {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentBondMoreBinding::bind)

    override val viewModel: SelectBondMoreViewModel by viewModels()

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

    override fun subscribe(viewModel: SelectBondMoreViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        setupComposeViews()

        viewModel.showNextProgress.observe(binding.bondMoreContinue::setProgress)
        viewModel.showNextProgress.observe(binding.bondMoreConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            binding.bondMoreAmount.setAssetName(it.tokenName)
            binding.bondMoreAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        viewModel.stakeCreatorBalanceFlow.observe {
            binding.bondMoreAmount.setAssetBalance(it)
        }

        binding.bondMoreAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)
        binding.bondMoreAmount.amountInput.setOnFocusChangeListener { v, hasFocus ->
            viewModel.onAmountInputFocusChanged(hasFocus)
        }

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
