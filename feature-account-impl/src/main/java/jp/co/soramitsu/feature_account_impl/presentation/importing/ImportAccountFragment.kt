package jp.co.soramitsu.feature_account_impl.presentation.importing

import android.content.Intent
import android.os.Bundle
import android.text.method.DigitsKeyListener
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.mediateWith
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.feature_account_api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_api.presentation.importing.ImportAccountType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentImportAccountBinding
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
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.EthereumDerivationPathTransformer
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog

@AndroidEntryPoint
class ImportAccountFragment : BaseFragment<ImportAccountViewModel>() {
    companion object {
        const val BLOCKCHAIN_TYPE_KEY = "BLOCKCHAIN_TYPE_KEY"
        const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(blockChainType: Int = 0) = bundleOf(BLOCKCHAIN_TYPE_KEY to blockChainType)
        fun getBundle(chainAccountData: ChainAccountCreatePayload) = bundleOf(PAYLOAD_KEY to chainAccountData)
    }

    private lateinit var binding: FragmentImportAccountBinding

    override val viewModel: ImportAccountViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentImportAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }

            sourceTypeInput.setWholeClickListener { viewModel.openSourceChooserClicked() }

            advancedBlockView.setOnSubstrateEncryptionTypeClickListener {
                viewModel.chooseEncryptionClicked()
            }

            nextBtn.setOnClickListener { viewModel.nextClicked() }

            nextBtn.prepareForProgress(viewLifecycleOwner)

            advancedBlockView.ethereumDerivationPathEditText.keyListener = DigitsKeyListener.getInstance("0123456789/")

            advancedBlockView.ethereumDerivationPathEditText.addTextChangedListener(EthereumDerivationPathTransformer)
        }
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
            binding.advancedBlockView.setSubstrateEncryption(it.name)
        }

        viewModel.nextButtonState.observe(binding.nextBtn::setState)

        binding.advancedBlockView.substrateDerivationPathEditText.bindTo(viewModel.substrateDerivationPathLiveData, viewLifecycleOwner)
        binding.advancedBlockView.ethereumDerivationPathEditText.bindTo(viewModel.ethereumDerivationPathLiveData, viewLifecycleOwner)

        viewModel.showEthAccountsDialog.observeEvent { showEthDialog() }

        if (viewModel.isChainAccount) {
            binding.toolbar.setTitle(R.string.onboarding_restore_account)
        }

        mediateWith(
            viewModel.blockchainLiveData,
            viewModel.selectedSourceLiveData
        ) { (blockchainType: ImportAccountType?, sourceType: ImportSource) ->
            blockchainType?.let {
                val sourceViews = buildSourceTypesViews(blockchainType)
                setupSourceTypes(sourceType, sourceViews)
                val isChainAccount = viewModel.isChainAccount
                setupAdvancedBlock(blockchainType, sourceType, isChainAccount)
            }
        }.observe { }

        viewModel.showInvalidSubstrateDerivationPathError.observeEvent {
            showError(resources.getString(R.string.common_invalid_hard_soft_numeric_password_message))
        }
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

        with(binding) {
            sourceTypeContainer.removeAllViews()
            sourceTypeContainer.addView(sourceViews[index])

            sourceTypeInput.setMessage(sourceType.nameRes)
        }
    }

    private fun setupAdvancedBlock(blockchainType: ImportAccountType, sourceType: ImportSource, isChainAccount: Boolean) {
        binding.advancedBlockView.makeVisible()
        binding.advancedBlockView.apply {
            when {
                sourceType is MnemonicImportSource && isChainAccount -> {
                    configureForMnemonic(blockchainType)
                }
                sourceType is MnemonicImportSource -> {
                    configure(FieldState.NORMAL)
                    FieldState.DISABLED.applyTo(ethereumEncryptionTypeField)
                }
                sourceType is RawSeedImportSource -> {
                    configureForSeed(blockchainType)
                }
                sourceType is JsonImportSource -> {
                    binding.advancedBlockView.makeGone()
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

        val isChainAccount = viewModel.isChainAccount

        return when (source) {
            is JsonImportSource -> JsonImportView(context, blockchainType, isChainAccount)
            is MnemonicImportSource -> MnemonicImportView(context, isChainAccount)
            is RawSeedImportSource -> SeedImportView(context, blockchainType, isChainAccount)
        }
    }

    private fun showEthDialog() {
        AlertDialog.Builder(ContextThemeWrapper(context, jp.co.soramitsu.common.R.style.WhiteOverlay))
            .setTitle(R.string.alert_add_ethereum_title)
            .setMessage(R.string.alert_add_ethereum_message)
            .setPositiveButton(R.string.common_yes) { _, _ -> viewModel.onAddEthAccountConfirmed() }
            .setNegativeButton(R.string.common_no) { _, _ -> viewModel.onAddEthAccountDeclined() }
            .create()
            .show()
    }
}
