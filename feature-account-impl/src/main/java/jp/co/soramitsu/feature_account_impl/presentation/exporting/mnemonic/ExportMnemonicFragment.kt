package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent

private const val ACCOUNT_ADDRESS_KEY = "ACCOUNT_ADDRESS_KEY"

class ExportMnemonicFragment : BaseFragment<ExportMnemonicViewModel>() {

    companion object {
        fun getBundle(accountAddress: String): Bundle {
            return Bundle().apply {
                putString(ACCOUNT_ADDRESS_KEY, accountAddress)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_mnemonic, container, false)
    }

    override fun initViews() {
    }

    override fun inject() {
        val accountAddress = argument<String>(ACCOUNT_ADDRESS_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportMnemonicFactory()
            .create(this, accountAddress)
            .inject(this)
    }

    override fun subscribe(viewModel: ExportMnemonicViewModel) {
    }
}