package jp.co.soramitsu.common.scan

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.CaptureManager
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.emptyClick
import javax.inject.Inject
import jp.co.soramitsu.common.databinding.ActivityScannerBinding
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.EventObserver

@AndroidEntryPoint
class ScannerActivity : AppCompatActivity() {
    companion object {
        private const val QR_CODE_IMAGE_TYPE = "image/*"
    }

    @Inject
    lateinit var viewModel: ScannerViewModel

    private var capture: CaptureManager? = null

    private val startForResultFromGallery: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { selectedImageUri ->
                    viewModel.qrFileChosen(selectedImageUri)
                }
            }
        }

    private val binding by lazy {
        ActivityScannerBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val barcodeScannerView = binding.zxingBarcodeScanner
        barcodeScannerView.viewFinder?.setMaskColor(Color.TRANSPARENT)
        barcodeScannerView.viewFinder?.setLaserVisibility(false)

        capture = CaptureManager(this, barcodeScannerView)
        capture?.initializeFromIntent(intent, savedInstanceState)
        capture?.decode()

        binding.uploadFromGallery.setOnClickListener {
            selectQrFromGallery()
        }
        viewModel.scanResultEvent.observeEvent {
            onScanResult(it)
        }
        viewModel.errorLiveData.observeEvent {
            showErrorDialog(it)
        }
    }

    private fun showErrorDialog(
        message: String,
        positiveButtonText: String? = resources.getString(R.string.common_ok),
        negativeButtonText: String? = null,
        positiveClick: () -> Unit = emptyClick
    ) {
        ErrorDialog(
            title = resources.getString(R.string.common_error_general_title),
            message = message,
            positiveButtonText = positiveButtonText,
            negativeButtonText = negativeButtonText,
            positiveClick = positiveClick
        ).show(supportFragmentManager)
    }

    private fun onScanResult(value: String) {
        setResult(RESULT_OK, Intent().putExtra(Intents.Scan.RESULT, value))
        finish()
    }

    private fun selectQrFromGallery() {
        val intent = Intent().apply {
            type = QR_CODE_IMAGE_TYPE
            action = Intent.ACTION_GET_CONTENT
        }
        startForResultFromGallery.launch(intent)
    }

    inline fun <V> LiveData<Event<V>>.observeEvent(crossinline observer: (V) -> Unit) {
        observe(ProcessLifecycleOwner.get(), EventObserver { observer.invoke(it) })
    }

    override fun onResume() {
        super.onResume()
        capture?.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture?.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return binding.zxingBarcodeScanner.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }
}
