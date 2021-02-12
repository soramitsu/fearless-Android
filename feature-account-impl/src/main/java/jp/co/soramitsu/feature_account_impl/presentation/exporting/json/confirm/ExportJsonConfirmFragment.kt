package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmAdvanced
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmChangePassword
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmExport
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmNetworkInput
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmToolbar
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmValue

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class ExportJsonConfirmFragment : ExportFragment<ExportJsonConfirmViewModel>() {

    companion object {
        fun getBundle(payload: ExportJsonConfirmPayload): Bundle {
            return Bundle().apply {
                putParcelable(PAYLOAD_KEY, payload)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_json_confirm, container, false)
    }

    override fun initViews() {
        exportJsonConfirmToolbar.setHomeButtonListener { viewModel.back() }

        exportJsonConfirmExport.setOnClickListener { viewModel.confirmClicked() }

        exportJsonConfirmChangePassword.setOnClickListener { viewModel.changePasswordClicked() }

        with(exportJsonConfirmAdvanced) {
            configure(encryptionTypeField, FieldState.DISABLED)
            configure(derivationPathField, FieldState.HIDDEN)
        }

        exportJsonConfirmNetworkInput.isEnabled = false
    }

    override fun inject() {
        val payload = argument<ExportJsonConfirmPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportJsonConfirmFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ExportJsonConfirmViewModel) {
        super.subscribe(viewModel)

        viewModel.cryptoTypeLiveData.observe {
            exportJsonConfirmAdvanced.setEncryption(it.name)
        }

        viewModel.networkTypeLiveData.observe {
            exportJsonConfirmNetworkInput.setTextIcon(it.networkTypeUI.icon)
            exportJsonConfirmNetworkInput.setMessage(it.name)
        }

        exportJsonConfirmValue.setMessage(viewModel.json)
    }
}