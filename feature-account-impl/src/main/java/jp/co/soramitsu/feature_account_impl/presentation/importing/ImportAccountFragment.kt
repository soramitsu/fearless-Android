package jp.co.soramitsu.feature_account_impl.presentation.importing

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.mediateWith
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.feature_account_api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_api.presentation.importing.ImportAccountType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.FileRequester
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RequestCode
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.ImportSourceView
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.JsonImportView
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.MnemonicImportView
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.SeedImportView
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_import_account.advancedBlockView
import kotlinx.android.synthetic.main.fragment_import_account.nextBtn
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeContainer
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeInput
import kotlinx.android.synthetic.main.fragment_import_account.toolbar

class ImportAccountFragment : BaseFragment<ImportAccountViewModel>() {
    companion object {
        private const val BLOCKCHAIN_TYPE_KEY = "BLOCKCHAIN_TYPE_KEY"
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(blockChainType: Int = 0) = bundleOf(BLOCKCHAIN_TYPE_KEY to blockChainType)
        fun getBundle(chainAccountData: ChainAccountCreatePayload) = bundleOf(PAYLOAD_KEY to chainAccountData)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_import_account, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }

        sourceTypeInput.setWholeClickListener { viewModel.openSourceChooserClicked() }

        advancedBlockView.setOnSubstrateEncryptionTypeClickListener {
            viewModel.chooseEncryptionClicked()
        }

        nextBtn.setOnClickListener { viewModel.nextClicked() }

        nextBtn.prepareForProgress(viewLifecycleOwner)
    }

    override fun inject() {
        val blockChainType = arguments?.getInt(BLOCKCHAIN_TYPE_KEY)?.let { int ->
            ImportAccountType.values().getOrNull(int)
        }

        val chainCreateAccountData: ChainAccountCreatePayload? = arguments?.get(PAYLOAD_KEY) as? ChainAccountCreatePayload

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .importAccountComponentFactory()
            .create(this, blockChainType, chainCreateAccountData)
            .inject(this)
    }

    override fun subscribe(viewModel: ImportAccountViewModel) {
        viewModel.showSourceSelectorChooserLiveData.observeEvent(::showTypeChooser)

        viewModel.encryptionTypeChooserEvent.observeEvent {
            EncryptionTypeChooserBottomSheetDialog(
                requireActivity(),
                it,
                viewModel.selectedEncryptionTypeLiveData::setValue
            )
                .show()
        }

        viewModel.selectedEncryptionTypeLiveData.observe {
            advancedBlockView.setSubstrateEncryption(it.name)
        }

        viewModel.nextButtonState.observe(nextBtn::setState)

        advancedBlockView.substrateDerivationPathEditText.bindTo(viewModel.substrateDerivationPathLiveData, viewLifecycleOwner)
        advancedBlockView.ethereumDerivationPathEditText.bindTo(viewModel.ethereumDerivationPathLiveData, viewLifecycleOwner)

        viewModel.showEthAccountsDialog.observeEvent { showEthDialog() }

        mediateWith(
            viewModel.blockchainLiveData,
            viewModel.selectedSourceLiveData
        ) { (blockchainType: ImportAccountType?, sourceType: ImportSource) ->
            blockchainType?.let {
                val sourceViews = buildSourceTypesViews(blockchainType)
                setupSourceTypes(sourceType, sourceViews)

                setupAdvancedBlock(blockchainType, sourceType)
            }
        }.observe { }
    }

    private fun buildSourceTypesViews(blockchainType: ImportAccountType) = viewModel.sourceTypes.map {
        val view = createSourceView(it, blockchainType)

        view.observeSource(it, viewLifecycleOwner)
        view.observeCommon(viewModel, viewLifecycleOwner)

        observeFeatures(it)

        view
    }

    private fun setupSourceTypes(sourceType: ImportSource, sourceViews: List<ImportSourceView>) {
        val index = viewModel.sourceTypes.indexOf(sourceType)

        sourceTypeContainer.removeAllViews()
        sourceTypeContainer.addView(sourceViews[index])

        sourceTypeInput.setMessage(sourceType.nameRes)
    }

    private fun setupAdvancedBlock(blockchainType: ImportAccountType, sourceType: ImportSource) {
        advancedBlockView.makeVisible()
        advancedBlockView.apply {
            when {
                sourceType is MnemonicImportSource -> {
                    configure(FieldState.NORMAL)
                }
                sourceType is RawSeedImportSource && blockchainType == ImportAccountType.Substrate -> {
                    configureSubstrate(FieldState.NORMAL)
                    configureEthereum(FieldState.HIDDEN)
                }
                sourceType is RawSeedImportSource && blockchainType == ImportAccountType.Ethereum -> {
                    configureSubstrate(FieldState.HIDDEN)
                    configureEthereum(FieldState.NORMAL)
                }
                sourceType is JsonImportSource -> {
                    advancedBlockView.makeGone()
                }
            }
        }
    }

    private fun observeFeatures(source: ImportSource) {
        if (source is FileRequester) {
            source.chooseJsonFileEvent.observeEvent {
                openFilePicker(it)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        data?.let { viewModel.systemCallResultReceived(requestCode, it) }
    }

    private fun showTypeChooser(it: Payload<ImportSource>) {
        SourceTypeChooserBottomSheetDialog(
            context = requireActivity(),
            payload = it,
            onClicked = viewModel::sourceTypeChanged
        )
            .show()
    }

    private fun openFilePicker(it: RequestCode) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/json"
        startActivityForResult(intent, it)
    }

    private fun createSourceView(source: ImportSource, blockchainType: ImportAccountType): ImportSourceView {
        val context = requireContext()

        return when (source) {
            is JsonImportSource -> JsonImportView(context, blockchainType)
            is MnemonicImportSource -> MnemonicImportView(context)
            is RawSeedImportSource -> SeedImportView(context, blockchainType)
        }
    }

    private fun showEthDialog() {
        AlertDialog.Builder(ContextThemeWrapper(context, jp.co.soramitsu.common.R.style.WhiteOverlay))
            .setTitle(R.string.eth_import_title)
            .setMessage(R.string.eth_import_message)
            .setPositiveButton(R.string.common_yes) { _, _ -> viewModel.onAddEthAccountConfirmed() }
            .setNegativeButton(R.string.common_no) { _, _ -> viewModel.onAddEthAccountDeclined() }
            .create()
            .show()
    }
}
