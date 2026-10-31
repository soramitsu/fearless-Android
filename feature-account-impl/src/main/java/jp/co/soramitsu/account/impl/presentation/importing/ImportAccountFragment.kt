package jp.co.soramitsu.account.impl.presentation.importing

import android.content.Intent
import android.os.Bundle
import android.text.method.DigitsKeyListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.impl.presentation.common.SecureScreenGuard
import jp.co.soramitsu.account.impl.presentation.importing.source.model.FileRequester
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportError
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.RawSeedImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.RequestCode
import jp.co.soramitsu.account.impl.presentation.importing.source.view.ImportSourceView
import jp.co.soramitsu.account.impl.presentation.importing.source.view.JsonImportView
import jp.co.soramitsu.account.impl.presentation.importing.source.view.MnemonicImportView
import jp.co.soramitsu.account.impl.presentation.importing.source.view.SeedImportView
import jp.co.soramitsu.account.impl.presentation.mnemonic.backup.EthereumDerivationPathTransformer
import jp.co.soramitsu.account.impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.account.impl.presentation.recovery.excludeRecoveryInputFromPersistence
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.mediateWith
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentImportAccountBinding

@AndroidEntryPoint
class ImportAccountFragment : BaseFragment<ImportAccountViewModel>() {
    companion object {

        const val IMPORT_ACCOUNT_TYPE_KEY = "IMPORT_ACCOUNT_TYPE_KEY"
        const val IMPORT_MODE_KEY = "IMPORT_MODE_KEY"
        const val IMPORT_WALLET_ID_KEY = "IMPORT_WALLET_ID_KEY"
        const val IMPORT_REBIND_KEY = "IMPORT_REBIND_KEY"
        const val PIN_RECOVERY_CHALLENGE = "PIN_RECOVERY_CHALLENGE"

        fun getPinRecoveryBundle(target: RecoveryTarget, mode: ImportMode, challenge: String): Bundle =
            getRebindBundle(target, mode).apply { putString(PIN_RECOVERY_CHALLENGE, challenge) }

        fun getRebindBundle(target: RecoveryTarget, mode: ImportMode): Bundle =
            getBundle(null, target.ecosystem(), mode).apply { putParcelable(IMPORT_REBIND_KEY, target) }

        fun getBundle(
            walletId: Long?,
            accountType: WalletEcosystem = WalletEcosystem.Substrate,
            importMode: ImportMode = ImportMode.MnemonicPhrase
        ): Bundle {
            return bundleOf(
                IMPORT_ACCOUNT_TYPE_KEY to accountType,
                IMPORT_MODE_KEY to importMode,
                IMPORT_WALLET_ID_KEY to walletId
            )
        }
    }

    private lateinit var binding: FragmentImportAccountBinding

    // Every import mode shows a recovery phrase, raw seed or keystore JSON as it is entered, so
    // none of them may reach a screen capture, including during the PIN round trip of a recovery.
    private val secureScreen = SecureScreenGuard(this)

    override val viewModel: ImportAccountViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentImportAccountBinding.inflate(inflater, container, false)
        if (viewModel.isRebinding) {
            binding.root.excludeRecoveryInputFromPersistence()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        secureScreen.onViewCreated()
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        secureScreen.onStart()
    }

    override fun onStop() {
        viewModel.recoveryScreenStopped()
        secureScreen.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        secureScreen.onDestroyView()
        super.onDestroyView()
    }

    override fun initViews() {
        with(binding) {
            toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }
            if (viewModel.isRebinding) {
                toolbar.setTitle(getString(if (viewModel.pinRecoveryChallenge == null) R.string.wallet_recovery_restore_key else R.string.pin_recovery_verify_backup))
                nextBtn.setText(if (viewModel.pinRecoveryChallenge == null) R.string.wallet_recovery_restore_key else R.string.pin_recovery_verify_backup)
                recoveryExplanation.isVisible = true
            }

            if (viewModel.initialWalletEcosystem == WalletEcosystem.Ton) {
                sourceTypeInput.isEnabled = false
            } else {
                sourceTypeInput.setWholeClickListener { viewModel.openSourceChooserClicked() }
            }

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
        viewModel.recoveryError.observe {
            binding.recoveryError.isVisible = it != null
            binding.recoveryError.text = it
        }
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

        viewModel.substratePasswordChooserEvent.observeEvent {
            SubstratePasswordChooserBottomSheetDialog(
                requireActivity(),
                it,
                viewModel::substratePasswordAccountChosen
            )
                .show()
        }

        viewModel.nextButtonState.observe(binding.nextBtn::setState)

        binding.advancedBlockView.substrateDerivationPathEditText.bindTo(viewModel.substrateDerivationPathLiveData, viewLifecycleOwner)
        binding.advancedBlockView.ethereumDerivationPathEditText.bindTo(viewModel.ethereumDerivationPathLiveData, viewLifecycleOwner)

        viewModel.showEthAccountsDialog.observeEvent { showEthDialog() }

        mediateWith(
            viewModel.blockchainLiveData,
            viewModel.selectedSourceLiveData
        ) { (blockchainType: WalletEcosystem?, sourceType: ImportSource) ->
            blockchainType?.let {
                val sourceViews = buildSourceTypesViews(blockchainType)
                setupSourceTypes(sourceType, sourceViews)
                setupAdvancedBlock(blockchainType, sourceType)
            }
        }.observe { }
    }

    private fun buildSourceTypesViews(blockchainType: WalletEcosystem) = viewModel.sourceTypes.map {
        val view = createSourceView(it, blockchainType)
        if (viewModel.isRebinding) view.excludeRecoveryInputFromPersistence()

        view.observeSource(it, blockchainType, viewLifecycleOwner)
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

    private fun setupAdvancedBlock(blockchainType: WalletEcosystem, sourceType: ImportSource) {
        binding.advancedBlockView.makeVisible()
        binding.advancedBlockView.apply {
            if (viewModel.isRebinding) {
                configure(FieldState.HIDDEN)
                if (blockchainType == WalletEcosystem.Ton || sourceType is JsonImportSource ||
                    (blockchainType == WalletEcosystem.Ethereum && sourceType is RawSeedImportSource)) {
                    makeGone()
                } else {
                    val field = if (blockchainType == WalletEcosystem.Ethereum) ethereumDerivationPathField else substrateDerivationPathField
                    configure(field, FieldState.NORMAL)
                }
                return
            }
            when {
                blockchainType == WalletEcosystem.Ton -> {
                    binding.advancedBlockView.makeGone()
                }
                sourceType is MnemonicImportSource -> {
                    configure(FieldState.NORMAL)
                    FieldState.DISABLED.applyTo(ethereumEncryptionTypeField)
                }
                sourceType is RawSeedImportSource -> {
                    configureForAccountType(blockchainType)
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

    @Deprecated("Deprecated in Java")
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

    private fun createSourceView(source: ImportSource, blockchainType: WalletEcosystem): ImportSourceView {
        val context = requireContext()

        return when (source) {
            is JsonImportSource -> JsonImportView(context, blockchainType, ::onShowImportError)
            is MnemonicImportSource -> MnemonicImportView(context)
            is RawSeedImportSource -> SeedImportView(context, blockchainType)
        }
    }

    private fun onShowImportError(importError: ImportError) {
         ErrorDialog(
            title = resources.getString(importError.titleRes),
            message = resources.getString(importError.messageRes),
            positiveButtonText = resources.getString(R.string.common_ok)
        ).show(childFragmentManager)
    }

    private fun showEthDialog() {
        val res = requireContext().resources
        ErrorDialog(
            isHideable = false,
            title = res.getString(R.string.alert_add_ethereum_title),
            message = res.getString(R.string.alert_add_ethereum_message),
            positiveButtonText = res.getString(R.string.common_yes),
            negativeButtonText = res.getString(R.string.common_no),
            positiveClick = viewModel::onAddEthAccountConfirmed,
            negativeClick = viewModel::onAddEthAccountDeclined
        ).show(childFragmentManager)
    }
}
