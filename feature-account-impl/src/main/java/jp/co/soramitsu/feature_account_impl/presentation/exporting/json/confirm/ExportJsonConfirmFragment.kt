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
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportJsonConfirmBinding
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ExportJsonConfirmFragment : ExportFragment<ExportJsonConfirmViewModel>() {

    @Inject
    lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentExportJsonConfirmBinding

    override val viewModel: ExportJsonConfirmViewModel by viewModels()

    companion object {
        const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(payload: ExportJsonConfirmPayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentExportJsonConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
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
    }

    override fun subscribe(viewModel: ExportJsonConfirmViewModel) {
        super.subscribe(viewModel)

        viewModel.cryptoTypeLiveData.observe {
            binding.exportJsonConfirmAdvanced.setSubstrateEncryption(it.name)
        }

        viewModel.chainLiveData.observe {
            binding.exportJsonConfirmNetworkInput.loadIcon(it.icon, imageLoader)
            binding.exportJsonConfirmNetworkInput.setMessage(it.name)
        }

        viewModel.isEthereum.observe { isEthereum ->
            binding.exportJsonConfirmAdvanced.isVisible = false
            when {
                viewModel.isExportFromWallet -> {
                    val hasEthereumAccount = viewModel.ethereumJson.isNullOrBlank().not()
                    with(binding) {
                        exportSubstrateJsonConfirmValue.isVisible = true
                        exportEthereumJsonConfirmValue.isVisible = hasEthereumAccount
                        exportJsonConfirmAdvanced.isVisible = false
                        exportSubstrateJsonConfirmExport.isVisible = true
                        exportEthereumJsonConfirmExport.isVisible = hasEthereumAccount
                    }
                }
                !viewModel.isExportFromWallet && !isEthereum -> {
                    with(binding) {
                        exportSubstrateJsonConfirmValue.isVisible = true
                        exportEthereumJsonConfirmValue.isVisible = false
                        exportJsonConfirmAdvanced.isVisible = true
                        exportSubstrateJsonConfirmExport.isVisible = true
                        exportEthereumJsonConfirmExport.isVisible = false
                    }
                }
                !viewModel.isExportFromWallet && isEthereum -> {
                    with(binding) {
                        exportSubstrateJsonConfirmValue.isVisible = false
                        exportEthereumJsonConfirmValue.isVisible = true
                        exportSubstrateJsonConfirmExport.isVisible = false
                        exportEthereumJsonConfirmExport.isVisible = true
                    }
                }
            }
        }

        viewModel.substrateJson?.let { binding.exportSubstrateJsonConfirmValue.setMessage(it) }
        viewModel.ethereumJson?.let { binding.exportEthereumJsonConfirmValue.setMessage(it) }

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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CHOOSER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            viewModel.shareCompleted()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
