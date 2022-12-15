package jp.co.soramitsu.common.qrScanner

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.journeyapps.barcodescanner.CaptureActivity

class QrScannerActivity : CaptureActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val decorView = window.decorView
        decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        window.statusBarColor = Color.TRANSPARENT
    }
}
