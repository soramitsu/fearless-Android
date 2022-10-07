package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import dev.chrisbanes.insetter.applyInsetter
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateSignerPayload
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.view.dialog.warningDialog
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.DAppMetadataModel
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionAmount
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionConfirm
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionContainer
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionDappName
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionFee
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionNetwork
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionOrigin
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionRawData
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionReceiver
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionToolbar

private const val SIGN_PAYLOAD_KEY = "SIGN_PAYLOAD_KEY"

class SignBeaconTransactionFragment : BaseFragment<SignBeaconTransactionViewModel>() {

    companion object {

        const val SIGN_RESULT_KEY = "SIGN_STATUS_KEY"
        const val METADATA_KEY = "METADATA_KEY"

        fun getBundle(payload: SubstrateSignerPayload, dAppMetadata: DAppMetadataModel) = Bundle().apply {
            val result = when (payload) {
                is SubstrateSignerPayload.Raw -> {
                    payload.data
                }
                else -> ""
            }
            putString(SIGN_PAYLOAD_KEY, result)
            putParcelable(METADATA_KEY, dAppMetadata)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_sign_beacon_transaction, container, false)

    override fun initViews() {
        signBeaconTransactionContainer.applyInsetter {
            type(statusBars = true) {
                margin()
            }
        }

        signBeaconTransactionToolbar.setHomeButtonListener { openExitDialog() }
        onBackPressed { openExitDialog() }

        signBeaconTransactionConfirm.setOnClickListener { viewModel.confirmClicked() }
        signBeaconTransactionRawData.setOnClickListener { viewModel.rawDataClicked() }
    }

    private fun openExitDialog() {
        warningDialog(
            requireContext(),
            onConfirm = { viewModel.exit() }
        ) {
            setTitle(R.string.common_are_you_sure)
            setMessage(R.string.beacon_decline_signing_message)
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .signBeaconTransactionFactory()
            .create(this, argument(SIGN_PAYLOAD_KEY), argument(METADATA_KEY))
            .inject(this)
    }

    override fun subscribe(viewModel: SignBeaconTransactionViewModel) {
        viewModel.operationModel.observe {
            when (it) {
                is SignableOperationModel.Success -> {
                    signBeaconTransactionAmount.showValueOrHide(it.amount?.token, it.amount?.fiat)
                    signBeaconTransactionDappName.showValueOrHide(viewModel.dAppMetadataModel.name)
                    signBeaconTransactionNetwork.showValueOrHide(it.chainName)
                }
                is SignableOperationModel.Failure -> {
                    signBeaconTransactionAmount.makeGone()
                    signBeaconTransactionRawData.makeGone()
                    signBeaconTransactionFee.makeGone()
                }
            }
        }

        viewModel.feeLiveData.observe(signBeaconTransactionFee::setFeeStatus)

        viewModel.currentAccountAddressModel.observe {
            signBeaconTransactionOrigin.setTitle(it.name ?: "")
            signBeaconTransactionOrigin.setAccountIcon(it.image)
        }

        viewModel.totalBalanceLiveData.observe {
            signBeaconTransactionOrigin.setText(it)
        }

        viewModel.receiver.observe {
            signBeaconTransactionReceiver.isVisible = it != null
            it ?: return@observe
            signBeaconTransactionReceiver.setAccountIcon(it.image)
            signBeaconTransactionReceiver.setTitle("To")
            signBeaconTransactionReceiver.setText(it.address)
        }
    }
}
