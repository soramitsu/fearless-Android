package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import coil.ImageLoader
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.observeTransferChecks
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.send.BalanceDetailsBottomSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.observePhishingCheck
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmount25
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmount50
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmount75
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountFee
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountFeeFiat
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountFeeProgress
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountField
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountMax
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountNext
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountRecipientView
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountToolbar
import javax.inject.Inject

private const val KEY_ADDRESS = "KEY_ADDRESS"
private const val KEY_ASSET_PAYLOAD = "KEY_ASSET_PAYLOAD"

private const val QUICK_VALUE_MAX = 1.0
private const val QUICK_VALUE_75 = 0.75
private const val QUICK_VALUE_50 = 0.5
private const val QUICK_VALUE_25 = 0.25

class ChooseAmountFragment : BaseFragment<ChooseAmountViewModel>() {

    @Inject
    lateinit var imageLoader: ImageLoader

    companion object {
        fun getBundle(recipientAddress: String, assetPayload: AssetPayload) =
            bundleOf(KEY_ADDRESS to recipientAddress, KEY_ASSET_PAYLOAD to assetPayload)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_choose_amount, container, false)

    override fun initViews() {
        chooseAmountNext.prepareForProgress(viewLifecycleOwner)

        chooseAmountRecipientView.setActionClickListener { viewModel.recipientAddressClicked() }

        chooseAmountToolbar.setHomeButtonListener { viewModel.backClicked() }

        chooseAmountNext.setOnClickListener { viewModel.nextClicked() }

        chooseAmountMax.setOnClickListener { viewModel.quickInputSelected(QUICK_VALUE_MAX) }
        chooseAmount75.setOnClickListener { viewModel.quickInputSelected(QUICK_VALUE_75) }
        chooseAmount50.setOnClickListener { viewModel.quickInputSelected(QUICK_VALUE_50) }
        chooseAmount25.setOnClickListener { viewModel.quickInputSelected(QUICK_VALUE_25) }

        chooseAmountField.amountInput.apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit

                override fun afterTextChanged(p0: Editable?) {
                    setSelection(text.length)
                }
            })
        }
    }

    override fun inject() {
        val address = argument<String>(KEY_ADDRESS)
        val assetPayload = argument<AssetPayload>(KEY_ASSET_PAYLOAD)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .chooseAmountComponentFactory()
            .create(this, address, assetPayload)
            .inject(this)
    }

    override fun subscribe(viewModel: ChooseAmountViewModel) {
        setupExternalActions(viewModel)

        observeTransferChecks(viewModel, viewModel::warningConfirmed)

        viewModel.feeLiveData.observe {
            chooseAmountFee.text = it?.feeAmount?.formatTokenAmount(it.type) ?: getString(R.string.common_error_general_title)
        }
        viewModel.feeFiatLiveData.observe {
            chooseAmountFeeFiat.text = it ?: ""
        }

        viewModel.feeLoadingLiveData.observe { loading ->
            val textColorRes = if (loading) R.color.gray3 else R.color.white
            chooseAmountFee.setTextColorRes(textColorRes)

            chooseAmountFeeProgress.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.recipientModelLiveData.observe {
            chooseAmountRecipientView.setMessage(it.address)

            chooseAmountRecipientView.setTextIcon(it.image)
        }
        viewModel.assetModelLiveData.observe {
            val transferableAmount =
                resources.getString(R.string.wallet_send_transferable_amount_caption, it.available.formatTokenAmount(it.token.configuration))
            chooseAmountField.setAssetBalance(transferableAmount)
            chooseAmountField.setAssetName(it.token.configuration.symbol)
            chooseAmountField.setAssetImageUrl(it.token.configuration.iconUrl, imageLoader)
            val toolbarTitle = resources.getString(R.string.wallet_send_navigation_title, it.token.configuration.symbol)
            chooseAmountToolbar.setTitle(toolbarTitle)
        }

        viewModel.enteredFiatAmountLiveData.observe {
            it?.let(chooseAmountField::setAssetBalanceDollarAmount)
        }

        viewModel.amountRawLiveData.observe {
            chooseAmountField.amountInput.setText(it)
        }

        viewModel.feeErrorLiveData.observeEvent {
            showRetry(it)
        }

        viewModel.continueButtonStateLiveData.observe(chooseAmountNext::setState)

        viewModel.showBalanceDetailsEvent.observeEvent {
            BalanceDetailsBottomSheet(requireContext(), it).show()
        }

        observePhishingCheck(viewModel)

        chooseAmountField.amountInput.onTextChanged(viewModel::amountChanged)
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
