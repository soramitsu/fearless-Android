package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.advancedBlockView
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.backupMnemonicViewer
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.nextBtn
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.toolbar

class BackupMnemonicFragment : BaseFragment<BackupMnemonicViewModel>() {

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(accountName: String, payload: ChainAccountCreatePayload?) = bundleOf(PAYLOAD_KEY to BackupMnemonicPayload(accountName, payload))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_backup_mnemonic, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener {
            viewModel.homeButtonClicked()
        }

        toolbar.setRightActionClickListener {
            viewModel.infoClicked()
        }

        advancedBlockView.setOnSubstrateEncryptionTypeClickListener {
            viewModel.chooseEncryptionClicked()
        }

        nextBtn.setOnClickListener {
            viewModel.nextClicked(advancedBlockView.getSubstrateDerivationPath(), advancedBlockView.getEthereumDerivationPath())
        }
    }

    override fun inject() {
        val payload = argument<BackupMnemonicPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .backupMnemonicComponentFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: BackupMnemonicViewModel) {
        viewModel.mnemonicLiveData.observe {
            backupMnemonicViewer.submitList(it)
        }

        viewModel.encryptionTypeChooserEvent.observeEvent(::showEncryptionChooser)

        viewModel.selectedEncryptionTypeLiveData.observe {
            advancedBlockView.setSubstrateEncryption(it.name)
        }

        viewModel.showInfoEvent.observeEvent {
            showMnemonicInfoDialog()
        }
    }

    private fun showEncryptionChooser(payload: Payload<CryptoTypeModel>) {
        EncryptionTypeChooserBottomSheetDialog(
            requireActivity(), payload,
            viewModel.selectedEncryptionTypeLiveData::setValue
        ).show()
    }

    private fun showMnemonicInfoDialog() {
        MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.common_info)
            .setMessage(R.string.account_creation_info)
            .setPositiveButton(R.string.common_ok) { dialog, _ -> dialog?.dismiss() }
            .show()
    }
}
