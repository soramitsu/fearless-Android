package jp.co.soramitsu.feature_account_impl.presentation.exporting.seed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.core.BuildConfig
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedAdvanced
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedCopyButton
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedExport
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedToolbar
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedType
import kotlinx.android.synthetic.main.fragment_export_seed.exportSeedValue

class ExportSeedFragment : ExportFragment<ExportSeedViewModel>() {

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(metaId: Long, chainId: ChainId) = bundleOf(PAYLOAD_KEY to ExportSeedPayload(metaId, chainId))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_seed, container, false)
    }

    override fun initViews() {
        exportSeedToolbar.setHomeButtonListener { viewModel.back() }

        configureAdvancedBlock()

        exportSeedExport.setOnClickListener { viewModel.exportClicked() }

        exportSeedCopyButton.setOnClickListener { viewModel.seedClicked() }
        exportSeedCopyButton.isVisible = BuildConfig.DEBUG
    }

    private fun configureAdvancedBlock() {
        with(exportSeedAdvanced) {
            configure(FieldState.DISABLED)
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

        exportSeedType.setMessage(typeNameRes)

        viewModel.derivationPathLiveData.observe {
            val state = if (it.isNullOrBlank()) FieldState.HIDDEN else FieldState.DISABLED

            with(exportSeedAdvanced) {
                configure(substrateDerivationPathField, state)

                setSubstrateDerivationPath(it)
            }
        }

        viewModel.seedLiveData.observe {
            it?.let { exportSeedValue.setMessage(it) }
        }

        viewModel.cryptoTypeLiveData.observe {
            exportSeedAdvanced.setSubstrateEncryption(it.name)
        }
    }
}
