package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.app.Activity
import android.os.Bundle
import android.text.method.DigitsKeyListener
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.impl.presentation.common.SecureScreenGuard
import jp.co.soramitsu.account.impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.isGooglePlayServicesAvailable
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentBackupMnemonicBinding
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder

@AndroidEntryPoint
class BackupMnemonicFragment : BaseFragment<BackupMnemonicViewModel>(R.layout.fragment_backup_mnemonic) {

    companion object {
        fun getBundle(
            accountName: String,
            walletId: Long?,
            accountTypes: List<WalletEcosystem>
        ): Bundle {
            return bundleOf(
                BackupMnemonicScreenKeys.PAYLOAD_KEY to BackupMnemonicPayload(
                    isFromGoogleBackup = false,
                    accountName = accountName,
                    walletId = walletId,
                    accountTypes = accountTypes
                )
            )
        }
    }

    private val binding by viewBinding(FragmentBackupMnemonicBinding::bind)

    // The recovery phrase must never reach a screenshot, a recording, a cast
    // or the recents thumbnail.
    private val secureScreen = SecureScreenGuard(this)

    override val viewModel: BackupMnemonicViewModel by viewModels()

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onGoogleSignInSuccess()
        } else {
            viewModel.onGoogleSignInFailed(GoogleSignInResult.parse(result.resultCode, result.data))
        }
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
        secureScreen.onStop()
        super.onStop()
    }

    override fun onDestroyView() {
        secureScreen.onDestroyView()
        super.onDestroyView()
    }

    override fun initViews() {
        with(binding) {
            toolbar.setHomeButtonListener {
                viewModel.homeButtonClicked()
            }

            toolbar.setRightActionClickListener {
                viewModel.infoClicked()
            }

            advancedBlockView.isVisible = viewModel.isShowAdvancedBlock
            if (viewModel.walletId != null) {
                when {
                    viewModel.isSubstrateAndEthereumAccount -> {
                        advancedBlockView.configure(FieldState.NORMAL)
                    }

                    viewModel.isSubstrateAccount -> {
                        advancedBlockView.configureSubstrate(FieldState.NORMAL)
                        advancedBlockView.configureEthereum(FieldState.HIDDEN)
                    }

                    viewModel.isEthereumAccount -> {
                        advancedBlockView.configureSubstrate(FieldState.HIDDEN)
                        advancedBlockView.configureEthereum(FieldState.NORMAL)
                    }
                }
            }
            advancedBlockView.setOnSubstrateEncryptionTypeClickListener {
                viewModel.chooseEncryptionClicked()
            }
            advancedBlockView.ethereumDerivationPathEditText.keyListener = DigitsKeyListener.getInstance("0123456789/")

            advancedBlockView.ethereumDerivationPathEditText.addTextChangedListener(EthereumDerivationPathTransformer)

            nextBtn.setOnClickListener {
                viewModel.onNextClick(
                    advancedBlockView.getSubstrateDerivationPath(),
                    advancedBlockView.getEthereumDerivationPath().ifEmpty { BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH },
                    launcher
                )
            }
            val isGoogleAvailable = context?.isGooglePlayServicesAvailable() == true
            googleBackupLayout.isVisible = isGoogleAvailable && viewModel.isShowBackupWithGoogle
            googleBackupButton.setOnClickListener {
                viewModel.onGoogleBackupClick(
                    advancedBlockView.getSubstrateDerivationPath(),
                    advancedBlockView.getEthereumDerivationPath(),
                    launcher
                )
            }
            confirmMnemonicSkip.isVisible = viewModel.isShowSkipButton
            confirmMnemonicSkip.setOnClickListener {
                viewModel.skipClicked()
            }
        }
    }

    override fun subscribe(viewModel: BackupMnemonicViewModel) {
        viewModel.mnemonic.observe {
            binding.backupMnemonicViewer.submitList(it)
        }

        viewModel.encryptionTypeChooserEvent.observeEvent(::showEncryptionChooser)

        viewModel.selectedEncryptionTypeLiveData.observe {
            binding.advancedBlockView.setSubstrateEncryption(it.name)
        }

        viewModel.showInfoEvent.observeEvent {
            showMnemonicInfoDialog()
        }
    }

    private fun showEncryptionChooser(payload: Payload<CryptoTypeModel>) {
        EncryptionTypeChooserBottomSheetDialog(
            requireActivity(),
            payload,
            viewModel.selectedEncryptionTypeLiveData::setValue
        ).show()
    }

    private fun showMnemonicInfoDialog() {
        val res = requireContext()
        ErrorDialog(
            title = res.getString(R.string.common_info),
            message = res.getString(R.string.account_creation_info),
            positiveButtonText = res.getString(R.string.common_ok)
        ).show(childFragmentManager)
    }
}
