package jp.co.soramitsu.feature_account_impl.presentation.exporting.seed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.common.utils.mediateWith
import jp.co.soramitsu.core.BuildConfig
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportSeedBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class ExportSeedFragment : ExportFragment<ExportSeedViewModel>() {

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(metaId: Long, chainId: ChainId, isExportWallet: Boolean = false) =
            bundleOf(PAYLOAD_KEY to ExportSeedPayload(metaId, chainId, isExportWallet))
    }

    private lateinit var binding: FragmentExportSeedBinding

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

            exportSeedAdvanced.configureSubstrate(FieldState.DISABLED)
            exportSeedAdvanced.configureEthereum(FieldState.HIDDEN)

            exportSeedExport.setOnClickListener { viewModel.exportClicked() }

            exportSubstrateSeedCopyButton.setOnClickListener { viewModel.substrateSeedClicked() }
            exportSubstrateSeedCopyButton.isVisible = BuildConfig.DEBUG

            exportEthereumSeedCopyButton.setOnClickListener { viewModel.ethereumSeedClicked() }
            exportEthereumSeedCopyButton.isVisible = BuildConfig.DEBUG
        }
    }

    override fun inject() {
        val payload = argument<ExportSeedPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportSeedFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ExportSeedViewModel) {
        super.subscribe(viewModel)

        val typeNameRes = viewModel.exportSource.nameRes

        binding.exportSeedType.setMessage(typeNameRes)

        mediateWith(
            viewModel.isEthereum,
            viewModel.derivationPathLiveData,
            viewModel.seedLiveData
        ) { (isEthereum: Boolean?, derivationPath: String?, seedInfo: ComponentHolder?) ->
            val substrateSeed: String? = seedInfo?.component1()
            val ethereumSeed: String? = seedInfo?.component2()

            val derivationPathFieldState = if (derivationPath.isNullOrBlank()) FieldState.HIDDEN else FieldState.DISABLED
            val ethereumEncryptionTypeFieldState = if (ethereumSeed.isNullOrBlank()) FieldState.HIDDEN else FieldState.DISABLED

            substrateSeed?.let { binding.exportSeedSubstrateValue.setMessage(it) }
            ethereumSeed?.let { binding.exportSeedEthereumValue.setMessage(it) }

            with(binding.exportSeedAdvanced) {
                derivationPathFieldState.applyTo(substrateDerivationPathField)
                setSubstrateDerivationPath(derivationPath)
            }

            when {
                viewModel.isExportFromWallet -> {
                    with(binding) {
                        exportSeedSubstrateLayout.isVisible = true
                        exportSeedEthereumLayout.isGone = ethereumSeed.isNullOrBlank()
                        ethereumEncryptionTypeFieldState.applyTo(exportSeedAdvanced.ethereumEncryptionTypeField)
                    }
                }
                isEthereum == true -> {
                    with(binding) {
                        exportSeedSubstrateLayout.isVisible = false
                        exportSeedEthereumLayout.isVisible = true
                        exportSeedAdvanced.configureSubstrate(FieldState.HIDDEN)
                        FieldState.DISABLED.applyTo(exportSeedAdvanced.ethereumEncryptionTypeField)
                    }
                }
                isEthereum == false -> {
                    binding.exportSeedSubstrateLayout.isVisible = true
                    binding.exportSeedEthereumLayout.isVisible = false
                }
            }
        }.observe { }

        viewModel.cryptoTypeLiveData.observe {
            binding.exportSeedAdvanced.setSubstrateEncryption(it.name)
        }
    }
}
