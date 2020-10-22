package jp.co.soramitsu.feature_wallet_impl.presentation.send.amount

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.icon
import jp.co.soramitsu.feature_wallet_impl.util.formatAsToken
import kotlinx.android.synthetic.main.fragment_choose_amount.chooseAmountBalance
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
        chooseAmountRecipientView.setOnCopyClickListener { viewModel.copyRecipientAddressClicked() }

        chooseAmountToolbar.setHomeButtonListener { viewModel.backClicked() }

        chooseAmountNext.setOnClickListener { viewModel.nextClicked() }
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
        viewModel.feeLiveData.observe {
            chooseAmountFee.text = it.amount?.formatAsToken(it.token) ?: getString(R.string.common_error_general_title)
        }

        viewModel.feeLoadingLiveData.observe { loading ->
            val textColorRes = if (loading) R.color.gray3 else R.color.white
            chooseAmountFee.setTextColorRes(textColorRes)

            chooseAmountFeeProgress.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.recipientModelLiveData.observe {
            chooseAmountRecipientView.setAddress(it.address)

            chooseAmountRecipientView.setIcon(it.image)
        }

        viewModel.assetLiveData.observe {
            chooseAmountBalance.text = it.total.formatAsToken(it.token)

            chooseAmountToken.setIcon(it.token.icon)
            chooseAmountToken.setText(it.token.displayName)
        }

        viewModel.continueEnabledLiveData.observe {
            chooseAmountNext.isEnabled = it
        }

        viewModel.feeErrorLiveData.observeEvent {
            showRetry(it)
        }

        viewModel.checkingEnoughFundsLiveData.observe { checking ->
            val textRes = if (checking) R.string.choose_amount_checking else R.string.common_continue

            chooseAmountField.isEnabled = !checking

            chooseAmountNext.setText(textRes)
        }

        chooseAmountField.onTextChanged(viewModel::amountChanged)
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