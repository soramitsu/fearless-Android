package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentReceiveBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.model.QrSharingPayload

private const val KEY_ASSET_PAYLOAD = "assetPayload"

class ReceiveFragment : BaseFragment<ReceiveViewModel>(R.layout.fragment_receive) {
    companion object {
        fun getBundle(assetPayload: AssetPayload) = bundleOf(KEY_ASSET_PAYLOAD to assetPayload)
    }

    private val binding by viewBinding(FragmentReceiveBinding::bind)

    override fun initViews() {
        binding.accountView.setWholeClickListener { viewModel.recipientClicked() }

        binding.fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        binding.fearlessToolbar.setRightActionClickListener {
            viewModel.shareButtonClicked()
        }
    }

    override fun inject() {
        val assetPayload = arguments!![KEY_ASSET_PAYLOAD] as AssetPayload

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .receiveComponentFactory()
            .create(this, assetPayload)
            .inject(this)
    }

    override fun subscribe(viewModel: ReceiveViewModel) {
        setupExternalActions(viewModel)

        viewModel.qrBitmapLiveData.observe {
            binding.qrImg.setImageBitmap(it)
        }

        viewModel.accountLiveData.observe { account ->
            account.name?.let(binding.accountView::setTitle)
            binding.accountView.setText(account.address)
        }

        viewModel.accountIconLiveData.observe {
            binding.accountView.setAccountIcon(it.image)
        }

        viewModel.shareEvent.observeEvent(::startQrSharingIntent)

        binding.fearlessToolbar.setTitle(getString(R.string.wallet_asset_receive_template, viewModel.assetSymbol))
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
