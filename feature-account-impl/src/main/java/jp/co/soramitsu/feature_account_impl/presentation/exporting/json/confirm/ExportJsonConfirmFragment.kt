package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import coil.ImageLoader
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportEthereumJsonConfirmExport
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportEthereumJsonConfirmValue
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmAdvanced
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmChangePassword
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmNetworkInput
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportJsonConfirmToolbar
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportSubstrateJsonConfirmExport
import kotlinx.android.synthetic.main.fragment_export_json_confirm.exportSubstrateJsonConfirmValue
import java.io.File
import javax.inject.Inject

class ExportJsonConfirmFragment : ExportFragment<ExportJsonConfirmViewModel>() {

    @Inject
    lateinit var imageLoader: ImageLoader

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(payload: ExportJsonConfirmPayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_json_confirm, container, false)
    }

    override fun initViews() {
        exportJsonConfirmToolbar.setHomeButtonListener { viewModel.back() }

        exportSubstrateJsonConfirmExport.setOnClickListener { viewModel.confirmSubstrateClicked() }
        exportEthereumJsonConfirmExport.setOnClickListener { viewModel.confirmEthereumClicked() }

        exportJsonConfirmChangePassword.setOnClickListener { viewModel.changePasswordClicked() }

        with(exportJsonConfirmAdvanced) {
            configure(substrateEncryptionTypeField, FieldState.DISABLED)
            configure(substrateDerivationPathField, FieldState.HIDDEN)
            configureHint(substrateDerivationPathHintView, FieldState.HIDDEN)
            configureEthereum(FieldState.HIDDEN)
        }

        exportJsonConfirmNetworkInput.isEnabled = false
        exportJsonConfirmNetworkInput.isVisible = !viewModel.isExportFromWallet
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

        viewModel.isEthereum.observe { isEthereum ->
            exportJsonConfirmAdvanced.isVisible = false
            when {
                viewModel.isExportFromWallet -> {
                    val hasEthereumAccount = viewModel.ethereumJson.isNullOrBlank().not()
                    exportSubstrateJsonConfirmValue.isVisible = true
                    exportEthereumJsonConfirmValue.isVisible = hasEthereumAccount
                    exportJsonConfirmAdvanced.isVisible = false
                    exportSubstrateJsonConfirmExport.isVisible = true
                    exportEthereumJsonConfirmExport.isVisible = hasEthereumAccount
                }
                !viewModel.isExportFromWallet && !isEthereum -> {
                    exportSubstrateJsonConfirmValue.isVisible = true
                    exportEthereumJsonConfirmValue.isVisible = false
                    exportJsonConfirmAdvanced.isVisible = true
                    exportSubstrateJsonConfirmExport.isVisible = true
                    exportEthereumJsonConfirmExport.isVisible = false
                }
                !viewModel.isExportFromWallet && isEthereum -> {
                    exportSubstrateJsonConfirmValue.isVisible = false
                    exportEthereumJsonConfirmValue.isVisible = true
                    exportSubstrateJsonConfirmExport.isVisible = false
                    exportEthereumJsonConfirmExport.isVisible = true
                }
            }
        }

        viewModel.substrateJson?.let { exportSubstrateJsonConfirmValue.setMessage(it) }
        viewModel.ethereumJson?.let { exportEthereumJsonConfirmValue.setMessage(it) }

        viewModel.shareEvent.observeEvent(::shareJson)
        viewModel.showJsonImportTypeEvent.observeEvent(::showExportTypeSheet)
    }

    private fun showExportTypeSheet(isEthereum: Boolean) {
        JsonExportTypeSheet(
            requireContext(),
            { viewModel.onExportByText(isEthereum) },
            { viewModel.onExportByFile(isEthereum) }
        ).show()
    }

    private fun shareJson(file: File) {
        val jsonUri = FileProvider.getUriForFile(activity!!, "${activity!!.packageName}.provider", file)

        if (jsonUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, jsonUri)
            }

            startActivity(Intent.createChooser(intent, "Json"))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CHOOSER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            viewModel.shareCompleted()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
