package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.FileProvider
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_receive.accountView
import kotlinx.android.synthetic.main.fragment_receive.fearlessToolbar
import kotlinx.android.synthetic.main.fragment_receive.qrImg
import java.io.File
import java.io.FileOutputStream

class ReceiveFragment : BaseFragment<ReceiveViewModel>() {

    companion object {
        private const val IMAGE_NAME = "image.png"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_receive, container, false)

    override fun initViews() {
        accountView.setActionListener { viewModel.addressCopyClicked() }

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

        viewModel.shareEvent.observeEvent {
            val mediaStorageDir: File? = saveToTempFile(activity!!, it.first)

            if (mediaStorageDir != null) {
                val imageUri = FileProvider.getUriForFile(activity!!, "${activity!!.packageName}.provider", mediaStorageDir)

                if (imageUri != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        putExtra(Intent.EXTRA_TEXT, it.second)
                    }

                    startActivity(Intent.createChooser(intent, getString(R.string.wallet_receive_description)))
                }
            }
        }
    }

    private fun saveToTempFile(context: Context, bitmap: Bitmap): File? {
        val mediaStorageDir = File(context.externalCacheDir!!.absolutePath + IMAGE_NAME)

        val outputStream = FileOutputStream(mediaStorageDir)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.close()
        return mediaStorageDir
    }
}