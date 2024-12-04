package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.app.Activity
import android.os.Bundle
import android.text.method.DigitsKeyListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.isGooglePlayServicesAvailable
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentBackupMnemonicBinding
import jp.co.soramitsu.shared_utils.encrypt.junction.BIP32JunctionDecoder

@AndroidEntryPoint
class BackupMnemonicFragment : BaseFragment<BackupMnemonicViewModel>(R.layout.fragment_backup_mnemonic) {

    companion object {
        fun getBundle(
            isFromGoogleBackup: Boolean,
            accountName: String,
            accountType: AccountType
        ): Bundle {
            return bundleOf(
                BackupMnemonicScreenKeys.PAYLOAD_KEY to BackupMnemonicPayload(isFromGoogleBackup, accountName, accountType)
            )
        }
    }

    private val binding by viewBinding(FragmentBackupMnemonicBinding::bind)

    override val viewModel: BackupMnemonicViewModel by viewModels()

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> viewModel.onGoogleSignInSuccess()
            Activity.RESULT_CANCELED -> { /* no action */ }
            else -> {
                val googleSignInStatus = result.data?.extras?.get("googleSignInStatus")
                viewModel.onGoogleLoginError(googleSignInStatus.toString())
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

            advancedBlockView.isVisible = viewModel.isShowAdvancedBlock
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
