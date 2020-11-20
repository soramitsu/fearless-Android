package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.zxing.integration.android.IntentIntegrator
import com.tbruyelle.rxpermissions2.RxPermissions
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.onDoneClicked
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_choose_recipient.searchRecipientField
import kotlinx.android.synthetic.main.fragment_choose_recipient.searchRecipientFlipper
import kotlinx.android.synthetic.main.fragment_choose_recipient.searchRecipientList
import kotlinx.android.synthetic.main.fragment_choose_recipient.searchRecipientToolbar
import javax.inject.Inject

private const val INDEX_WELCOME = 0
private const val INDEX_CONTENT = 1
private const val INDEX_EMPTY = 2

class ChooseRecipientFragment : BaseFragment<ChooseRecipientViewModel>(), ChooseRecipientAdapter.NodeItemHandler {

    companion object {
        private const val PICK_IMAGE_REQUEST = 101
        private const val QR_CODE_IMAGE_TYPE = "image/*"
    }

    private lateinit var adapter: ChooseRecipientAdapter

    @Inject lateinit var qrBitmapDecoder: QrBitmapDecoder

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_choose_recipient, container, false)

    override fun initViews() {
        adapter = ChooseRecipientAdapter(this)

        searchRecipientList.setHasFixedSize(true)
        searchRecipientList.adapter = adapter

        searchRecipientToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        searchRecipientToolbar.setRightActionClickListener {
            viewModel.scanClicked()
        }

        searchRecipientField.onDoneClicked {
            viewModel.enterClicked()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .chooseRecipientComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ChooseRecipientViewModel) {
        viewModel.screenStateLiveData.observe {
            val index = when (it) {
                State.WELCOME -> INDEX_WELCOME
                State.CONTENT -> INDEX_CONTENT
                State.EMPTY -> INDEX_EMPTY
            }

            searchRecipientFlipper.displayedChild = index
        }

        viewModel.searchResultLiveData.observe(adapter::submitList)

        viewModel.showChooserEvent.observeEvent {
            QrCodeSourceChooserBottomSheet(requireContext(), ::requestCameraPermission, ::selectQrFromGallery)
                .show()
        }

        viewModel.cameraPermissionGrantedEvent.observeEvent {
            initiateCameraScanner()
        }

        viewModel.decodeAddressResult.observeEvent {
            searchRecipientField.setText(it)
        }

        searchRecipientField.onTextChanged(viewModel::queryChanged)
    }

    private fun requestCameraPermission() {
        viewModel.observePermissionRequest(RxPermissions(this).request(Manifest.permission.CAMERA))
    }

    private fun selectQrFromGallery() {
        val intent = Intent().apply {
            type = QR_CODE_IMAGE_TYPE
            action = Intent.ACTION_GET_CONTENT
        }

        startActivityForResult(Intent.createChooser(intent, getString(R.string.common_options_title)), PICK_IMAGE_REQUEST)
    }

    private fun initiateCameraScanner() {
        val integrator = IntentIntegrator.forSupportFragment(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES)
            setPrompt("")
            setBeepEnabled(false)
        }
        integrator.initiateScan()
    }

    override fun contactClicked(addressModel: AddressModel) {
        viewModel.recipientSelected(addressModel.address)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            viewModel.observeQrCodeDecoding(qrBitmapDecoder.decodeQrCodeFromUri(data.data!!))
        } else {
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            result?.contents?.let {
                viewModel.qrCodeScanned(it)
            }
        }
    }
}