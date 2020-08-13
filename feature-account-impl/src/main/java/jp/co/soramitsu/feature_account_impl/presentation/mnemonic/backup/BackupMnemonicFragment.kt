package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup.mnemonic.MnemonicWordsAdapter
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.NetworkChooserBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.advancedBlockView
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.mnemonicRv
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.nextBtn
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.toolbar

class BackupMnemonicFragment : BaseFragment<BackupMnemonicViewModel>() {

    companion object {
        private const val KEY_ACCOUNT_NAME = "account_name"

        fun getBundle(accountName: String): Bundle {
            return Bundle().apply {
                putString(KEY_ACCOUNT_NAME, accountName)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_backup_mnemonic, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener {
            viewModel.homeButtonClicked()
        }

        toolbar.setRightIconClickListener {
            viewModel.infoClicked()
        }

        advancedBlockView.setOnEncryptionTypeClickListener {
            viewModel.encryptionTypeInputClicked()
        }

        advancedBlockView.setOnNetworkClickListener {
            viewModel.networkInputClicked()
        }

        nextBtn.setOnClickListener {
            viewModel.nextClicked(advancedBlockView.getDerivationPath())
        }
    }

    override fun inject() {
        val accountName = arguments!!.getString(KEY_ACCOUNT_NAME, "")
        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .backupMnemonicComponentFactory()
            .create(this, accountName)
            .inject(this)
    }

    override fun subscribe(viewModel: BackupMnemonicViewModel) {
        observe(viewModel.mnemonicLiveData, Observer {
            if (mnemonicRv.adapter == null) {
                mnemonicRv.layoutManager = GridLayoutManager(activity!!, it.first, GridLayoutManager.HORIZONTAL, false)
                mnemonicRv.adapter = MnemonicWordsAdapter()
            }
            (mnemonicRv.adapter as MnemonicWordsAdapter).submitList(it.second)
            mnemonicRv.makeVisible()
        })

        observe(viewModel.encryptionTypeChooserEvent, EventObserver {
            EncryptionTypeChooserBottomSheetDialog(requireActivity(), it) {
                viewModel.encryptionTypeChanged(it)
            }.show()
        })

        observe(viewModel.networkChooserEvent, EventObserver {
            NetworkChooserBottomSheetDialog(requireActivity(), it) {
                viewModel.networkChanged(it)
            }.show()
        })

        observe(viewModel.selectedEncryptionTypeLiveData, Observer {
            advancedBlockView.setEncryption(it.name)
        })

        observe(viewModel.selectedNetworkLiveData, Observer {
            advancedBlockView.setNetworkIconResource(it.icon)
            advancedBlockView.setNetworkName(it.name)
        })

        observe(viewModel.showInfoEvent, EventObserver {
            MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.common_info)
                .setMessage(R.string.account_creation_info)
                .setPositiveButton(R.string.common_ok) { dialog, _ -> dialog?.dismiss() }
                .show()
        })
    }
}