package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalActionsSheet
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import kotlinx.android.synthetic.main.fragment_extrinsic_details.*
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalViewCallback
import kotlinx.android.synthetic.main.fragment_reward_slash_details.*


private const val KEY_EXTRINSIC = "KEY_EXTRINSIC"

class ExtrinsicDetailFragment : BaseFragment<ExtrinsicDetailViewModel>() {
    companion object {
        fun getBundle(operation: OperationModel) = Bundle().apply {
            putParcelable(KEY_EXTRINSIC, operation)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_extrinsic_details, container, false)

    override fun initViews() {
        extrinsicDetailToolbar.setHomeButtonListener { viewModel.backClicked() }

        extrinsicDetailHash.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.TRANSACTION_HASH)
        }

        extrinsicDetailFrom.setWholeClickListener {
            viewModel.showExternalActionsClicked(ExternalActionsSource.FROM_ADDRESS)
        }
    }

    override fun inject() {
        val operation = argument<OperationModel>(KEY_EXTRINSIC)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .extrinsicDetailComponentFactory()
            .create(this, operation)
            .inject(this)
    }

    override fun subscribe(viewModel: ExtrinsicDetailViewModel) {
        with(viewModel.operation) {
            extrinsicDetailHash.setMessage(hash)
            extrinsicDetailFrom.setMessage(getDisplayAddress())
            extrinsicDetailStatus.setText(statusAppearance.labelRes)
            extrinsicDetailStatusIcon.setImageResource(statusAppearance.icon)
            extrinsicDetailDate.text = time.formatDateTime(requireContext())
            extrinsicDetailModule.text = (transactionType as OperationModel.TransactionModelType.Extrinsic).module
            extrinsicDetailCall.text = transactionType.call
            extrinsicDetailFee.text = transactionType.fee.formatTokenAmount(tokenType)
        }

        viewModel.showExternalExtrinsicActionsEvent.observeEvent(::showExternalActions)
        viewModel.openBrowserEvent.observeEvent(::showBrowser)
    }


    private fun showExternalActions(externalActionsSource: ExternalActionsSource) {
        when (externalActionsSource) {
            ExternalActionsSource.TRANSACTION_HASH -> showExternalTransactionActions()
            ExternalActionsSource.FROM_ADDRESS -> showExternalAddressActions(viewModel.operation.getDisplayAddress())
        }
    }

    private fun showExternalAddressActions(
        address: String
    ) = showExternalActionsSheet(
        copyLabelRes = R.string.common_copy_address,
        value = address,
        externalViewCallback = viewModel::viewAccountExternalClicked
    )

    private fun showExternalTransactionActions() {
        showExternalActionsSheet(
            R.string.transaction_details_copy_hash,
            viewModel.operation.hash,
            viewModel::viewTransactionExternalClicked
        )
    }

    private fun showExternalActionsSheet(
        @StringRes copyLabelRes: Int,
        value: String,
        externalViewCallback: ExternalViewCallback
    ) {
        val payload = ExternalActionsSheet.Payload(
            copyLabel = copyLabelRes,
            content = ExternalAccountActions.Payload(
                value = value,
                networkType = Node.NetworkType.POLKADOT //TODO add networktype to operationModel
            )
        )

        ExternalActionsSheet(
            context = requireContext(),
            payload = payload,
            onCopy = viewModel::copyStringClicked,
            onViewExternal = externalViewCallback
        )
            .show()
    }
}
