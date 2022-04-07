package jp.co.soramitsu.feature_account_impl.presentation.exporting.seed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.core.BuildConfig
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.synthetic.main.fragment_export_seed.exportEthereumSeedCopyButton
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedAdvanced
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedEthereumLayout
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedEthereumValue
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedExport
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedSubstrateLayout
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedSubstrateValue
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedToolbar
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedType
import kotlinx.android.synthetic.main.fragment_export_seed.exportSubstrateSeedCopyButton

class ExportSeedFragment : ExportFragment<ExportSeedViewModel>() {

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(metaId: Long, chainId: ChainId, isExportWallet: Boolean = false) =
            bundleOf(PAYLOAD_KEY to ExportSeedPayload(metaId, chainId, isExportWallet))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_seed, container, false)
    }

    override fun initViews() {
        exportSeedToolbar.setHomeButtonListener { viewModel.back() }

        configureAdvancedBlock()

        exportSeedExport.setOnClickListener { viewModel.exportClicked() }

        exportSubstrateSeedCopyButton.setOnClickListener { viewModel.substrateSeedClicked() }
        exportSubstrateSeedCopyButton.isVisible = BuildConfig.DEBUG

        exportEthereumSeedCopyButton.setOnClickListener { viewModel.ethereumSeedClicked() }
        exportEthereumSeedCopyButton.isVisible = BuildConfig.DEBUG
    }

    private fun configureAdvancedBlock() {
        exportSeedAdvanced.configure(FieldState.DISABLED)
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

        exportSeedType.setMessage(typeNameRes)

        combine(viewModel.isEthereum, viewModel.derivationPathLiveData) { it }
            .observe { (isEthereum: Boolean, derivationPath: String?) ->
                val state = if (derivationPath.isNullOrBlank()) FieldState.HIDDEN else FieldState.DISABLED

                with(exportSeedAdvanced) {
                    configure(substrateDerivationPathField, state)
                    setSubstrateDerivationPath(derivationPath)
                    configureEthereum(FieldState.HIDDEN)
                }

                when {
                    viewModel.isExportFromWallet -> {
                        exportSeedSubstrateLayout.isVisible = true
                        exportSeedEthereumLayout.isVisible = true
                    }
                    isEthereum -> {
                        exportSeedSubstrateLayout.isVisible = false
                        exportSeedEthereumLayout.isVisible = true
                        exportSeedAdvanced.configureSubstrate(FieldState.HIDDEN)
                        FieldState.DISABLED.applyTo(exportSeedAdvanced.ethereumEncryptionTypeField)
                    }
                    isEthereum.not() -> {
                        exportSeedSubstrateLayout.isVisible = true
                        exportSeedEthereumLayout.isVisible = false
                    }
                }
            }

        viewModel.seedLiveData.observe { (substrateSeed: String?, ethereumSeed: String?) ->
            substrateSeed?.let { exportSeedSubstrateValue.setMessage(it) }
            ethereumSeed?.let { exportSeedEthereumValue.setMessage(it) }
            exportSeedEthereumLayout.isVisible = ethereumSeed.isNullOrBlank().not()
        }

        viewModel.cryptoTypeLiveData.observe {
            exportSeedAdvanced.setSubstrateEncryption(it.name)
        }
    }
}
