package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.util.formatAsToken
import jp.co.soramitsu.feature_wallet_impl.util.formatDateTime
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailAmount
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailDate
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailFee
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailFrom
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailHash
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailRepeat
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailStatus
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailStatusIcon
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailTo
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailToolbar
import kotlinx.android.synthetic.main.fragment_transaction_details.transactionDetailTotal

private const val KEY_TRANSACTION = "KEY_DRAFT"

class TransactionDetailFragment : BaseFragment<TransactionDetailViewModel>() {

    companion object {
        fun getBundle(transaction: TransactionModel) = Bundle().apply {
            putParcelable(KEY_TRANSACTION, transaction)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_transaction_details, container, false)

    override fun initViews() {
        transactionDetailToolbar.setHomeButtonListener { viewModel.backClicked() }
    }

    override fun inject() {
        val transaction = argument<TransactionModel>(KEY_TRANSACTION)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .transactionDetailComponentFactory()
            .create(this, transaction)
            .inject(this)
    }

    override fun subscribe(viewModel: TransactionDetailViewModel) {
        with(viewModel.transaction) {
            transactionDetailStatus.setText(statusAppearance.labelRes)
            transactionDetailStatusIcon.setImageResource(statusAppearance.icon)

            transactionDetailDate.text = date.formatDateTime(requireContext())

            transactionDetailAmount.text = amount.formatAsToken(token)
            transactionDetailFee.text = fee.formatAsToken(token)

            transactionDetailHash.setText(hash)

            transactionDetailTotal.text = total.formatAsToken(token)
        }

        viewModel.recipientAddressModelLiveData.observe { addressModel ->
            transactionDetailFrom.setText(addressModel.address)
            transactionDetailFrom.setTextIcon(addressModel.image)

            transactionDetailFrom.setActionClickListener { viewModel.copyAddressClicked(addressModel.address) }
        }

        viewModel.senderAddressModelLiveData.observe { addressModel ->
            transactionDetailTo.setText(addressModel.address)
            transactionDetailTo.setTextIcon(addressModel.image)

            transactionDetailTo.setActionClickListener { viewModel.copyAddressClicked(addressModel.address) }
        }

        viewModel.retryAddressModelLiveData.observe {
            transactionDetailRepeat.setTitle(it.address)
            transactionDetailRepeat.setAccountIcon(it.image)

            transactionDetailRepeat.setActionListener { viewModel.repeatTransaction() }
        }
    }
}