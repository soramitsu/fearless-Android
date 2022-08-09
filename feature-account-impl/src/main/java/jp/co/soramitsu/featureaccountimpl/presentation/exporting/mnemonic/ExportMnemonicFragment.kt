package jp.co.soramitsu.featureaccountimpl.presentation.exporting.mnemonic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportMnemonicBinding
import jp.co.soramitsu.featureaccountimpl.presentation.exporting.ExportFragment
import jp.co.soramitsu.featureaccountimpl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@AndroidEntryPoint
class ExportMnemonicFragment : ExportFragment<ExportMnemonicViewModel>() {

    companion object {
        const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(metaId: Long, chainId: ChainId, isExportWallet: Boolean) = bundleOf(
            PAYLOAD_KEY to ExportMnemonicPayload(metaId, chainId, isExportWallet)
        )
    }

    override val viewModel: ExportMnemonicViewModel by viewModels()

    private lateinit var binding: FragmentExportMnemonicBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentExportMnemonicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        configureAdvancedBlock()

        with(binding) {
            exportMnemonicToolbar.setHomeButtonListener { viewModel.back() }

            exportMnemonicToolbar.setTitle(
                when {
                    viewModel.isExportFromWallet -> R.string.export_wallet
                    else -> R.string.account_export
                }
            )

            exportMnemonicConfirm.setOnClickListener { viewModel.openConfirmMnemonic() }

            exportMnemonicExport.setOnClickListener { viewModel.exportClicked() }
        }
    }

    private fun configureAdvancedBlock() {
        with(binding.exportMnemonicAdvanced) {
            configure(FieldState.DISABLED)
        }
    }

    override fun subscribe(viewModel: ExportMnemonicViewModel) {
        super.subscribe(viewModel)

        val typeNameRes = viewModel.exportSource.nameRes

        binding.exportMnemonicType.setMessage(typeNameRes)

        viewModel.mnemonicWordsLiveData.observe {
            binding.exportMnemonicViewer.submitList(it)
        }

        viewModel.derivationPathLiveData.observe { (substrateDerivationPath: String?, ethereumDerivationPath: String?) ->
            if (substrateDerivationPath.isNullOrBlank() && ethereumDerivationPath.isNullOrBlank()) {
                binding.exportMnemonicAdvanced.isVisible = false
                return@observe
            }
            val substrateState = if (substrateDerivationPath.isNullOrBlank()) FieldState.HIDDEN else FieldState.DISABLED
            val ethereumState = if (ethereumDerivationPath.isNullOrBlank()) FieldState.HIDDEN else FieldState.DISABLED

            with(binding.exportMnemonicAdvanced) {
                configureSubstrate(substrateState)
                configureEthereum(ethereumState)
                setSubstrateDerivationPath(substrateDerivationPath)
                setEthereumDerivationPath(ethereumDerivationPath)
            }
        }
        viewModel.cryptoTypeLiveData.observe {
            binding.exportMnemonicAdvanced.setSubstrateEncryption(it.name)
        }
    }
}
