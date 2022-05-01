package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import coil.ImageLoader
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.utils.setDrawableStart
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordConfirmField
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordMatchingError
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordNetworkInput
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordNewField
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordNext
import kotlinx.android.synthetic.main.fragment_export_json_password.exportJsonPasswordToolbar
import javax.inject.Inject

class ExportJsonPasswordFragment : ExportFragment<ExportJsonPasswordViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(metaId: Long, chainId: ChainId, isExportWallet: Boolean) =
            bundleOf(PAYLOAD_KEY to ExportJsonPasswordPayload(metaId, chainId, isExportWallet))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_json_password, container, false)
    }

    override fun onResume() {
        super.onResume()
        viewModel.resetProgress()
    }

    override fun initViews() {
        exportJsonPasswordToolbar.setHomeButtonListener {
            hideKeyboard()
            viewModel.back()
        }

        exportJsonPasswordToolbar.setTitle(
            when {
                viewModel.isExportWallet -> R.string.export_wallet
                else -> R.string.account_export
            }
        )

        exportJsonPasswordNext.prepareForProgress(viewLifecycleOwner)
        exportJsonPasswordNext.setOnClickListener { viewModel.nextClicked() }

        exportJsonPasswordMatchingError.setDrawableStart(R.drawable.ic_red_cross, 24)

        exportJsonPasswordNetworkInput.isEnabled = false
        exportJsonPasswordNetworkInput.isVisible = viewModel.isExportWallet.not()
    }

    override fun inject() {
        val payload = argument<ExportJsonPasswordPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportJsonPasswordFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ExportJsonPasswordViewModel) {
        super.subscribe(viewModel)
        exportJsonPasswordNewField.content.bindTo(viewModel.passwordLiveData)
        exportJsonPasswordConfirmField.content.bindTo(viewModel.passwordConfirmationLiveData)

        viewModel.nextButtonState.observe(exportJsonPasswordNext::setState)

        viewModel.showDoNotMatchingErrorLiveData.observe {
            exportJsonPasswordMatchingError.setVisible(it, falseState = View.INVISIBLE)
        }

        viewModel.chainLiveData.observe {
            exportJsonPasswordNetworkInput.loadIcon(it.icon, imageLoader)
            exportJsonPasswordNetworkInput.setMessage(it.name)
        }
    }
}
