package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.observeTransferChecks
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentChooseAmountBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.send.BalanceDetailsBottomSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.observePhishingCheck
import javax.inject.Inject

private const val KEY_ADDRESS = "KEY_ADDRESS"
private const val KEY_ASSET_PAYLOAD = "KEY_ASSET_PAYLOAD"

private const val QUICK_VALUE_MAX = 1.0
private const val QUICK_VALUE_75 = 0.75
private const val QUICK_VALUE_50 = 0.5
private const val QUICK_VALUE_25 = 0.25

@AndroidEntryPoint
class ChooseAmountFragment : BaseFragment<ChooseAmountViewModel>(R.layout.fragment_choose_amount) {

    @Inject
    lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentChooseAmountBinding::bind)

    @Inject
    lateinit var factory: ChooseAmountViewModel.ChooseAmountViewModelFactory

    private val vm: ChooseAmountViewModel by viewModels {
        ChooseAmountViewModel.provideFactory(
            factory,
            argument(KEY_ADDRESS),
            argument(KEY_ASSET_PAYLOAD)
        )
    }
    override val viewModel: ChooseAmountViewModel
        get() = vm

    companion object {
        fun getBundle(recipientAddress: String, assetPayload: AssetPayload) =
            bundleOf(KEY_ADDRESS to recipientAddress, KEY_ASSET_PAYLOAD to assetPayload)
    }

    override fun initViews() {
        with(binding) {
            chooseAmountNext.prepareForProgress(viewLifecycleOwner)

            chooseAmountRecipientView.setActionClickListener { viewModel.recipientAddressClicked() }

            chooseAmountToolbar.setHomeButtonListener { viewModel.backClicked() }

            chooseAmountNext.setOnClickListener { viewModel.nextClicked() }

            chooseAmountMax.setOnClickListener { viewModel.quickInputSelected(QUICK_VALUE_MAX) }
            chooseAmount75.setOnClickListener { viewModel.quickInputSelected(QUICK_VALUE_75) }
            chooseAmount50.setOnClickListener { viewModel.quickInputSelected(QUICK_VALUE_50) }
            chooseAmount25.setOnClickListener { viewModel.quickInputSelected(QUICK_VALUE_25) }
        }

        binding.chooseAmountField.amountInput.apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit

                override fun afterTextChanged(p0: Editable?) {
                    setSelection(text.length)
                }
            })
        }
    }

    override fun subscribe(viewModel: ChooseAmountViewModel) {
        setupExternalActions(viewModel)

        observeTransferChecks(viewModel, viewModel::warningConfirmed)

        viewModel.feeLiveData.observe {
            binding.chooseAmountFee.text = it?.feeAmount?.formatTokenAmount(it.type) ?: getString(R.string.common_error_general_title)
        }
        viewModel.tipAmountTextLiveData.observe {
            binding.chooseAmountTipGroup.makeVisible()
            binding.chooseAmountTip.text = it
        }
        viewModel.tipFiatAmountLiveData.observe {
            binding.chooseAmountTipFiat.text = it
        }
        viewModel.feeFiatLiveData.observe {
            binding.chooseAmountFeeFiat.text = it ?: ""
        }

        viewModel.feeLoadingLiveData.observe { loading ->
            val textColorRes = if (loading) R.color.gray3 else R.color.white
            binding.chooseAmountFee.setTextColorRes(textColorRes)

            binding.chooseAmountFeeProgress.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.recipientModelLiveData.observe {
            binding.chooseAmountRecipientView.setMessage(it.address)

            binding.chooseAmountRecipientView.setTextIcon(it.image)
        }
        viewModel.assetModelLiveData.observe {
            val transferableAmount =
                resources.getString(R.string.wallet_send_transferable_amount_caption, it.available.orZero().formatTokenAmount(it.token.configuration))
            binding.chooseAmountField.setAssetBalance(transferableAmount)
            binding.chooseAmountField.setAssetName(it.token.configuration.symbol)
            binding.chooseAmountField.setAssetImageUrl(it.token.configuration.iconUrl, imageLoader)
            val toolbarTitle = resources.getString(R.string.wallet_send_navigation_title, it.token.configuration.symbol)
            binding.chooseAmountToolbar.setTitle(toolbarTitle)
        }

        viewModel.enteredFiatAmountLiveData.observe {
            it?.let(binding.chooseAmountField::setAssetBalanceFiatAmount)
        }

        viewModel.amountRawLiveData.observe {
            binding.chooseAmountField.amountInput.setText(it)
        }

        viewModel.feeErrorLiveData.observeEvent {
            showRetry(it)
        }

        viewModel.continueButtonStateLiveData.observe(binding.chooseAmountNext::setState)

        viewModel.showBalanceDetailsEvent.observeEvent {
            BalanceDetailsBottomSheet(requireContext(), it).show()
        }

        observePhishingCheck(viewModel)

        binding.chooseAmountField.amountInput.onTextChanged(viewModel::amountChanged)
    }

    private fun showRetry(reason: RetryReason) {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.choose_amount_network_error)
            .setMessage(reason.reasonRes)
            .setCancelable(false)
            .setPositiveButton(R.string.common_retry) { _, _ -> viewModel.retry(reason) }
            .setNegativeButton(R.string.common_cancel) { _, _ -> viewModel.backClicked() }
            .show()
    }
}
