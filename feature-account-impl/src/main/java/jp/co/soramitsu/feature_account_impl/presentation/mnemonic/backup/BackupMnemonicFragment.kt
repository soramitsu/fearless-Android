package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.feature_account_impl.R

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
    }

    override fun inject() {

    }

    override fun subscribe(viewModel: BackupMnemonicViewModel) {

    }
}