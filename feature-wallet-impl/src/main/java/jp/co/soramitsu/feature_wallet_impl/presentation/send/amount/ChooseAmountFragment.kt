package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.account.external.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.icon
import jp.co.soramitsu.feature_wallet_impl.presentation.send.BalanceDetailsBottomSheet
import jp.co.soramitsu.feature_wallet_impl.presentation.send.observeTransferChecks
import jp.co.soramitsu.feature_wallet_impl.presentation.send.phishing.observePhishingCheck
import jp.co.soramitsu.feature_wallet_impl.util.formatAsToken
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountBalance
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountBalanceLabel
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountFee
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountFeeProgress
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountField
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountNext
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountRecipientView
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountToken
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountToolbar

private const val KEY_ADDRESS = "KEY_ADDRESS"

class ChooseAmountFragment : BaseFragment<ChooseAmountViewModel>() {

    companion object {
        fun getBundle(recipientAddress: String) = Bundle().apply {
            putString(KEY_ADDRESS, recipientAddress)
        }
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

        chooseAmountBalanceLabel.setOnClickListener { viewModel.availableBalanceClicked() }
    }

    override fun inject() {
        val address = argument<String>(KEY_ADDRESS)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .chooseAmountComponentFactory()
            .create(this, address)
            .inject(this)
    }

    override fun subscribe(viewModel: ChooseAmountViewModel) {
        setupExternalActions(viewModel)

        observeTransferChecks(viewModel, viewModel::warningConfirmed)

        viewModel.feeLiveData.observe {
            chooseAmountFee.text = it?.feeAmount?.formatAsToken(it.type) ?: getString(R.string.common_error_general_title)
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

        viewModel.assetLiveData.observe {
            chooseAmountBalance.text = it.available.formatAsToken(it.token.type)

            chooseAmountToken.setTextIcon(it.token.type.icon)
            chooseAmountToken.setMessage(it.token.type.displayName)
        }

        viewModel.feeErrorLiveData.observeEvent {
            showRetry(it)
        }

        viewModel.continueButtonStateLiveData.observe(chooseAmountNext::setState)

        viewModel.showBalanceDetailsEvent.observeEvent {
            val asset = viewModel.assetLiveData.value!!

            BalanceDetailsBottomSheet(requireContext(), asset, it).show()
        }

        observePhishingCheck(viewModel)

        chooseAmountField.content.onTextChanged(viewModel::amountChanged)
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