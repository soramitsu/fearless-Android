package jp.co.soramitsu.account.impl.presentation.exporting.seed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.account.impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportSeedBinding
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@AndroidEntryPoint
class ExportSeedFragment : ExportFragment<ExportSeedViewModel>() {

    companion object {
        const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(metaId: Long, chainId: ChainId, isExportWallet: Boolean = false) =
            bundleOf(PAYLOAD_KEY to ExportSeedPayload(metaId, chainId, isExportWallet))
    }

    private lateinit var binding: FragmentExportSeedBinding

    override val viewModel: ExportSeedViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentExportSeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            exportSeedToolbar.setHomeButtonListener { viewModel.back() }

            exportSeedToolbar.setTitle(
                when {
                    viewModel.isExportFromWallet -> R.string.export_wallet
                    else -> R.string.account_export
                }
            )

            exportSeedAdvanced.configure(FieldState.DISABLED)

            exportSeedExport.setOnClickListener { viewModel.exportClicked() }

            exportSubstrateSeedCopyButton.setOnClickListener { viewModel.substrateSeedClicked() }
            exportSubstrateSeedCopyButton.isVisible = BuildConfig.DEBUG

            exportEthereumSeedCopyButton.setOnClickListener { viewModel.ethereumSeedClicked() }
            exportEthereumSeedCopyButton.isVisible = BuildConfig.DEBUG
        }
    }

    override fun subscribe(viewModel: ExportSeedViewModel) {
        super.subscribe(viewModel)

        val typeNameRes = viewModel.exportSource.nameRes
        binding.exportSeedType.setMessage(typeNameRes)

        viewModel.exportSeedLiveData.observe { (substrateSeed: String?, ethereumSeed: String?) ->
            binding.exportSeedSubstrateLayout.isGone = substrateSeed.isNullOrEmpty()
            binding.exportSeedEthereumLayout.isGone = ethereumSeed.isNullOrEmpty()
            substrateSeed?.let { binding.exportSeedSubstrateValue.setMessage(it) }
            ethereumSeed?.let { binding.exportSeedEthereumValue.setMessage(it) }
        }

        viewModel.derivationPathLiveData.observe { (substrateDerivationPath: String?, ethereumDerivationPath: String?) ->
            if (substrateDerivationPath.isNullOrBlank() && ethereumDerivationPath.isNullOrBlank()) {
                binding.exportSeedAdvanced.isVisible = false
                return@observe
            }

            val substrateState = if (substrateDerivationPath.isNullOrBlank()) FieldState.HIDDEN else FieldState.DISABLED
            val ethereumState = if (ethereumDerivationPath.isNullOrBlank()) FieldState.HIDDEN else FieldState.DISABLED

            with(binding.exportSeedAdvanced) {
                expand()
                configureSubstrate(substrateState)
                configureEthereum(ethereumState)
                setSubstrateDerivationPath(substrateDerivationPath)
                setEthereumDerivationPath(ethereumDerivationPath)
            }
        }

        viewModel.cryptoTypeLiveData.observe {
            it?.name?.let { it1 -> binding.exportSeedAdvanced.setSubstrateEncryption(it1) }
        }
    }
}
