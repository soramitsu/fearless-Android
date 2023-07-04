package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.app.Activity
import android.os.Bundle
import android.text.method.DigitsKeyListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentBackupMnemonicBinding

@AndroidEntryPoint
class BackupMnemonicFragment : BaseFragment<BackupMnemonicViewModel>(R.layout.fragment_backup_mnemonic) {

    companion object {
        fun getBundle(
            isFromGoogleBackup: Boolean,
            accountName: String,
            payload: ChainAccountCreatePayload?
        ): Bundle {
            return bundleOf(
                BackupMnemonicScreenKeys.PAYLOAD_KEY to BackupMnemonicPayload(isFromGoogleBackup, accountName, payload)
            )
        }
    }

    private val binding by viewBinding(FragmentBackupMnemonicBinding::bind)

    override val viewModel: BackupMnemonicViewModel by viewModels()

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.onGoogleLoginError()
        } else {
            with(binding) {
                viewModel.onGoogleSignInSuccess(
                    advancedBlockView.getSubstrateDerivationPath(),
                    advancedBlockView.getEthereumDerivationPath()
                )
            }
        }
    }

    override fun initViews() {
        with(binding) {
            toolbar.setHomeButtonListener {
                viewModel.homeButtonClicked()
            }

            toolbar.setRightActionClickListener {
                viewModel.infoClicked()
            }

            advancedBlockView.isVisible = !viewModel.isFromGoogleBackup
            advancedBlockView.setOnSubstrateEncryptionTypeClickListener {
                viewModel.chooseEncryptionClicked()
            }
            advancedBlockView.ethereumDerivationPathEditText.keyListener = DigitsKeyListener.getInstance("0123456789/")

            advancedBlockView.ethereumDerivationPathEditText.addTextChangedListener(EthereumDerivationPathTransformer)

            nextBtn.setOnClickListener {
                viewModel.onNextClick(
                    advancedBlockView.getSubstrateDerivationPath(),
                    advancedBlockView.getEthereumDerivationPath(),
                    launcher
                )
            }
            googleBackupButton.isVisible = !viewModel.isFromGoogleBackup
            googleBackupButton.setOnClickListener {
                viewModel.onGoogleBackupClick(
                    advancedBlockView.getSubstrateDerivationPath(),
                    advancedBlockView.getEthereumDerivationPath(),
                    launcher
                )
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

        viewModel.showInvalidSubstrateDerivationPathError.observeEvent {
            showError(resources.getString(R.string.common_invalid_hard_soft_numeric_password_message))
        }

        viewModel.chainAccountImportType.observe(binding.advancedBlockView::configureForMnemonic)
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
