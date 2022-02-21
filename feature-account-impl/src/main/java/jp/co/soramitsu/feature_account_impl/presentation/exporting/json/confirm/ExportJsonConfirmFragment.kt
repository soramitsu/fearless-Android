package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import coil.ImageLoader
import javax.inject.Inject
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

class ExportJsonConfirmFragment : ExportFragment<ExportJsonConfirmViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(payload: ExportJsonConfirmPayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_json_confirm, container, false)
    }

    override fun initViews() {
        exportJsonConfirmToolbar.setHomeButtonListener { viewModel.back() }

        exportJsonConfirmExport.setOnClickListener { viewModel.confirmClicked() }

        exportJsonConfirmChangePassword.setOnClickListener { viewModel.changePasswordClicked() }

        with(exportJsonConfirmAdvanced) {
            configure(substrateEncryptionTypeField, FieldState.DISABLED)
            configure(substrateDerivationPathField, FieldState.HIDDEN)
            configureHint(substrateDerivationPathHintView, FieldState.HIDDEN)
            configureEthereum(FieldState.HIDDEN)
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
            exportJsonConfirmAdvanced.setSubstrateEncryption(it.name)
        }

        viewModel.chainLiveData.observe {
            exportJsonConfirmNetworkInput.loadIcon(it.icon, imageLoader)
            exportJsonConfirmNetworkInput.setMessage(it.name)
        }

        exportJsonConfirmValue.setMessage(viewModel.json)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CHOOSER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            viewModel.shareCompleted()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
