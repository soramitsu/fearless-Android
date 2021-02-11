package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.setDrawableStart
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordConfirmField
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordMatchingError
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordNetworkInput
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordNewField
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordNext
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordToolbar

private const val ACCOUNT_ADDRESS_KEY = "ACCOUNT_ADDRESS_KEY"

class ExportJsonPasswordFragment : BaseFragment<ExportJsonPasswordViewModel>() {

    companion object {
        fun getBundle(accountAddress: String): Bundle {
            return Bundle().apply {
                putString(ACCOUNT_ADDRESS_KEY, accountAddress)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_json_password, container, false)
    }

    override fun initViews() {
        exportJsonPasswordToolbar.setHomeButtonListener { viewModel.back() }

        exportJsonPasswordNext.setOnClickListener { viewModel.nextClicked() }

        exportJsonPasswordMatchingError.setDrawableStart(R.drawable.ic_red_cross, 24)

        exportJsonPasswordNetworkInput.isEnabled = false
    }

    override fun inject() {
        val accountAddress = argument<String>(ACCOUNT_ADDRESS_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportJsonPasswordFactory()
            .create(this, accountAddress)
            .inject(this)
    }

    override fun subscribe(viewModel: ExportJsonPasswordViewModel) {
        exportJsonPasswordNewField.content.bindTo(viewModel.passwordLiveData)
        exportJsonPasswordConfirmField.content.bindTo(viewModel.passwordConfirmationLiveData)

        viewModel.nextEnabled.observe(exportJsonPasswordNext::setEnabled)

        viewModel.showDoNotMatchingErrorLiveData.observe {
            exportJsonPasswordMatchingError.setVisible(it, falseState = View.INVISIBLE)
        }

        viewModel.networkTypeLiveData.observe {
            exportJsonPasswordNetworkInput.setTextIcon(it.networkTypeUI.icon)
            exportJsonPasswordNetworkInput.setMessage(it.name)
        }
    }
}