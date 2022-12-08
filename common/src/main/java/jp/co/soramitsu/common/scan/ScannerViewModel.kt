package jp.co.soramitsu.common.scan

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.QrBitmapDecoder
import jp.co.soramitsu.common.utils.requireValue
import kotlinx.coroutines.launch

@HiltViewModel
class ScannerViewModel
@Inject constructor(
    private val resourceManager: ResourceManager,
    private val qrBitmapDecoder: QrBitmapDecoder
) : BaseViewModel() {
    private val _scanResultEvent = MutableLiveData<Event<String>>()
    val scanResultEvent: LiveData<Event<String>> = _scanResultEvent

    fun qrFileChosen(uri: Uri) {
        viewModelScope.launch {
            val result = qrBitmapDecoder.decodeQrCodeFromUri(uri)

            if (result.isSuccess) {
                _scanResultEvent.value = Event(result.requireValue())
            } else {
                showError(resourceManager.getString(R.string.invoice_scan_error_no_info))
            }
        }
    }
}
