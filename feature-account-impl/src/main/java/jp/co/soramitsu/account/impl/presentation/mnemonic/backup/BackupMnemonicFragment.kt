package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.text.method.DigitsKeyListener
import androidx.core.os.bundleOf
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
        const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(accountName: String, payload: ChainAccountCreatePayload?) = bundleOf(PAYLOAD_KEY to BackupMnemonicPayload(accountName, payload))
    }

    private val binding by viewBinding(FragmentBackupMnemonicBinding::bind)

    override val viewModel: BackupMnemonicViewModel by viewModels()

    override fun initViews() {
        with(binding) {
            toolbar.setHomeButtonListener {
                viewModel.homeButtonClicked()
            }

            toolbar.setRightActionClickListener {
                viewModel.infoClicked()
            }

            advancedBlockView.setOnSubstrateEncryptionTypeClickListener {
                viewModel.chooseEncryptionClicked()
            }
            advancedBlockView.ethereumDerivationPathEditText.keyListener = DigitsKeyListener.getInstance("0123456789/")

            advancedBlockView.ethereumDerivationPathEditText.addTextChangedListener(EthereumDerivationPathTransformer)

            nextBtn.setOnClickListener {
                viewModel.nextClicked(advancedBlockView.getSubstrateDerivationPath(), advancedBlockView.getEthereumDerivationPath())
            }
        }
    }

    override fun subscribe(viewModel: BackupMnemonicViewModel) {
        viewModel.mnemonicLiveData.observe {
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
            title = res.getString(jp.co.soramitsu.common.R.string.common_info),
            message = res.getString(jp.co.soramitsu.common.R.string.account_creation_info),
            positiveButtonText = res.getString(R.string.common_ok)
        ).show(childFragmentManager)
    }
}
