package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic

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
import kotlinx.android.synthetic.main.fragment_export_mnemonic.exportMnemonicAdvanced
import kotlinx.android.synthetic.main.fragment_export_mnemonic.exportMnemonicConfirm
import kotlinx.android.synthetic.main.fragment_export_mnemonic.exportMnemonicExport
import kotlinx.android.synthetic.main.fragment_export_mnemonic.exportMnemonicToolbar
import kotlinx.android.synthetic.main.fragment_export_mnemonic.exportMnemonicType
import kotlinx.android.synthetic.main.fragment_export_mnemonic.exportMnemonicViewer

private const val ACCOUNT_ADDRESS_KEY = "ACCOUNT_ADDRESS_KEY"

class ExportMnemonicFragment : ExportFragment<ExportMnemonicViewModel>() {

    companion object {
        fun getBundle(accountAddress: String): Bundle {
            return Bundle().apply {
                putString(ACCOUNT_ADDRESS_KEY, accountAddress)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_mnemonic, container, false)
    }

    override fun initViews() {
        exportMnemonicToolbar.setHomeButtonListener { viewModel.back() }

        configureAdvancedBlock()

        exportMnemonicConfirm.setOnClickListener { viewModel.openConfirmMnemonic() }

        exportMnemonicExport.setOnClickListener { viewModel.exportClicked() }
    }

    private fun configureAdvancedBlock() {
        with(exportMnemonicAdvanced) {
            configure(FieldState.DISABLED)
        }
    }

    override fun inject() {
        val accountAddress = argument<String>(ACCOUNT_ADDRESS_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportMnemonicFactory()
            .create(this, accountAddress)
            .inject(this)
    }

    override fun subscribe(viewModel: ExportMnemonicViewModel) {
        super.subscribe(viewModel)

        val typeNameRes = viewModel.exportSource.nameRes

        exportMnemonicType.setMessage(typeNameRes)

        viewModel.mnemonicWordsLiveData.observe {
            exportMnemonicViewer.submitList(it)
        }

        viewModel.derivationPathLiveData.observe {
            val state = if (it.isNullOrBlank()) FieldState.HIDDEN else FieldState.DISABLED

            with(exportMnemonicAdvanced) {
                configure(derivationPathField, state)

                setDerivationPath(it)
            }
        }

        viewModel.cryptoTypeLiveData.observe {
            exportMnemonicAdvanced.setEncryption(it.name)
        }
    }
}