package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.FileProvider
import jp.co.soramitsu.common.account.external.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.model.QrSharingPayload
import kotlinx.android.synthetic.main.fragment_receive.accountView
import kotlinx.android.synthetic.main.fragment_receive.fearlessToolbar
import kotlinx.android.synthetic.main.fragment_receive.qrImg

class ReceiveFragment : BaseFragment<ReceiveViewModel>() {

    companion object {
        private const val QR_TEMP_IMAGE_NAME = "address.png"
        private const val QR_TEMP_IMAGE_QUALITY = 100
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_receive, container, false)

    override fun initViews() {
        accountView.setWholeClickListener { viewModel.recipientClicked() }

        fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        fearlessToolbar.setRightActionClickListener {
            viewModel.shareButtonClicked()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .receiveComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ReceiveViewModel) {
        setupExternalActions(viewModel)

        viewModel.qrBitmapLiveData.observe {
            qrImg.setImageBitmap(it)
        }

        viewModel.accountLiveData.observe { account ->
            account.name?.let(accountView::setTitle)
            accountView.setText(account.address)
        }

        viewModel.accountIconLiveData.observe {
            accountView.setAccountIcon(it.image)
        }

        viewModel.shareEvent.observeEvent(::startQrSharingIntent)
    }

    private fun startQrSharingIntent(qrSharingPayload: QrSharingPayload) {
        val imageUri = FileProvider.getUriForFile(activity!!, "${activity!!.packageName}.provider", qrSharingPayload.qrFile)

        if (imageUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, qrSharingPayload.shareMessage)
            }

            startActivity(Intent.createChooser(intent, getString(R.string.wallet_receive_description)))
        }
    }
}