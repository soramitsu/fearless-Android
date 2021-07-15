package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.dialog.warningDialog
import jp.co.soramitsu.common.view.setFromAddressModel
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionAmount
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionCall
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionConfirm
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionContainer
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionFee
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionModule
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionOrigin
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionRawData
import kotlinx.android.synthetic.main.fragment_sign_beacon_transaction.signBeaconTransactionToolbar

private const val SIGN_PAYLOAD_KEY = "SIGN_PAYLOAD_KEY"

class SignBeaconTransactionFragment : BaseFragment<SignBeaconTransactionViewModel>() {

    companion object {

        const val SIGN_RESULT_KEY = "SIGN_STATUS_KEY"

        fun getBundle(payload: String) = Bundle().apply {
            putString(SIGN_PAYLOAD_KEY, payload)
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
            .create(this, argument(SIGN_PAYLOAD_KEY))
            .inject(this)
    }

    override fun subscribe(viewModel: SignBeaconTransactionViewModel) {
        viewModel.operationModel.observe {
            signBeaconTransactionModule.showValue(it.module)
            signBeaconTransactionCall.showValue(it.call)

            signBeaconTransactionAmount.showValueOrHide(it.amount?.token, it.amount?.fiat)

            signBeaconTransactionRawData.setMessage(it.rawData)
        }

        viewModel.feeLiveData.observe(signBeaconTransactionFee::setFeeStatus)

        viewModel.currentAccountAddressModel.observe(signBeaconTransactionOrigin::setFromAddressModel)
    }
}
