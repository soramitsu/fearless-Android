package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalActionsSheet
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalViewCallback
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.android.synthetic.main.fragment_extrinsic_details.extrinsicDetailCall
import kotlinx.android.synthetic.main.fragment_extrinsic_details.extrinsicDetailDate
import kotlinx.android.synthetic.main.fragment_extrinsic_details.extrinsicDetailFee
import kotlinx.android.synthetic.main.fragment_extrinsic_details.extrinsicDetailFrom
import kotlinx.android.synthetic.main.fragment_extrinsic_details.extrinsicDetailHash
import kotlinx.android.synthetic.main.fragment_extrinsic_details.extrinsicDetailModule
import kotlinx.android.synthetic.main.fragment_extrinsic_details.extrinsicDetailStatus
import kotlinx.android.synthetic.main.fragment_extrinsic_details.extrinsicDetailStatusIcon
import kotlinx.android.synthetic.main.fragment_extrinsic_details.extrinsicDetailToolbar

class ExtrinsicDetailFragment : BaseFragment<ExtrinsicDetailViewModel>() {
    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(payload: ExtrinsicDetailsPayload) = bundleOf(PAYLOAD_KEY to payload)
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
        val payload = argument<ExtrinsicDetailsPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .extrinsicDetailComponentFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ExtrinsicDetailViewModel) {
        with(viewModel.payload.operation) {
            extrinsicDetailHash.setMessage(hash)
            extrinsicDetailStatus.setText(statusAppearance.labelRes)
            extrinsicDetailStatusIcon.setImageResource(statusAppearance.icon)
            extrinsicDetailDate.text = time.formatDateTime(requireContext())
            extrinsicDetailModule.text = module
            extrinsicDetailCall.text = call
            extrinsicDetailFee.text = fee
        }

        viewModel.showExternalExtrinsicActionsEvent.observeEvent(::showExternalActions)
        viewModel.openBrowserEvent.observeEvent(::showBrowser)

        viewModel.fromAddressModelLiveData.observe { addressModel ->
            extrinsicDetailFrom.setMessage(addressModel.nameOrAddress)
            extrinsicDetailFrom.setTextIcon(addressModel.image)
        }
    }

    private fun showExternalActions(externalActionsSource: ExternalActionsSource) {
        when (externalActionsSource) {
            ExternalActionsSource.TRANSACTION_HASH -> showExternalTransactionActions()
            ExternalActionsSource.FROM_ADDRESS -> showExternalAddressActions(viewModel.payload.operation.originAddress)
        }
    }

    private fun showExternalAddressActions(
        address: String
    ) = showExternalActionsSheet(
        copyLabelRes = R.string.common_copy_address,
        value = address,
        explorers = viewModel.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, address),
        externalViewCallback = viewModel::openUrl
    )

    private fun showExternalTransactionActions() = showExternalActionsSheet(
        copyLabelRes = R.string.transaction_details_copy_hash,
        value = viewModel.payload.operation.hash,
        explorers = viewModel.getSupportedExplorers(BlockExplorerUrlBuilder.Type.EXTRINSIC, viewModel.payload.operation.hash),
        externalViewCallback = viewModel::openUrl
    )

    private fun showExternalActionsSheet(
        @StringRes copyLabelRes: Int,
        value: String,
        explorers: Map<Chain.Explorer.Type, String>,
        externalViewCallback: ExternalViewCallback
    ) {
        val payload = ExternalActionsSheet.Payload(
            copyLabel = copyLabelRes,
            content = ExternalAccountActions.Payload(
                value = value,
                chainId = viewModel.payload.chainId,
                explorers = explorers
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
