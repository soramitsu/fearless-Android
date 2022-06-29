package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportMnemonicBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class ExportMnemonicFragment : ExportFragment<ExportMnemonicViewModel>() {

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(metaId: Long, chainId: ChainId, isExportWallet: Boolean) = bundleOf(
            PAYLOAD_KEY to ExportMnemonicPayload(metaId, chainId, isExportWallet)
        )
    }

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

    override fun inject() {
        val payload = argument<ExportMnemonicPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportMnemonicFactory()
            .create(this, payload)
            .inject(this)
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
