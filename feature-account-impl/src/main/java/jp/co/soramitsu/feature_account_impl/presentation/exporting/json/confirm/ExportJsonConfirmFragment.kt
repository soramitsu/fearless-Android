package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import kotlinx.android.synthetic.main.fragment_export_json_confirm.*


private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class ExportJsonConfirmFragment : BaseFragment<ExportJsonConfirmViewModel>() {

    companion object {
        fun getBundle(payload: ExportJsonConfirmPayload): Bundle {
            return Bundle().apply {
                putParcelable(PAYLOAD_KEY, payload)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_export_json_confirm, container, false)
    }

    override fun initViews() {
        exportJsonConfirmToolbar.setHomeButtonListener { viewModel.back() }

        exportJsonConfirmExport.setOnClickListener { viewModel.confirmClicked() }

        exportJsonConfirmChangePassword.setOnClickListener { viewModel.changePasswordClicked() }

        with(exportJsonConfirmAdvanced) {
            configure(networkTypeField, FieldState.DISABLED)
            configure(encryptionTypeField, FieldState.DISABLED)
            configure(derivationPathField, FieldState.HIDDEN)
        }
    }

    override fun inject() {
        val payload = argument<ExportJsonConfirmPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportJsonConfirmFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ExportJsonConfirmViewModel) {
        viewModel.exportEvent.observeEvent {
            shareTextWithCallback(it)
        }

        viewModel.cryptoTypeLiveData.observe {
            exportJsonConfirmAdvanced.setEncryption(it.name)
        }

        viewModel.networkTypeLiveData.observe {
            exportJsonConfirmAdvanced.setNetworkName(it.name)
            exportJsonConfirmAdvanced.setNetworkIconResource(it.networkTypeUI.icon)
        }

        exportJsonConfirmValue.setMessage(viewModel.json)
    }

    private fun shareTextWithCallback(text: String) {
        val title = getString(R.string.common_share)

        val intent = Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, text)
                .setType("text/plain")

        val receiver = Intent(requireContext(), ShareCompletedReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, receiver, PendingIntent.FLAG_UPDATE_CURRENT)

        val chooser = Intent.createChooser(intent, title, pendingIntent.intentSender)

        startActivity(chooser)
    }
}