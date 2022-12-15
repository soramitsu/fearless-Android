package jp.co.soramitsu.common.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanOptions

class ScanTextContract : ActivityResultContract<ScanOptions, String?>() {
    override fun createIntent(context: Context, input: ScanOptions): Intent {
        return input.createScanIntent(context)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        return if (resultCode == Activity.RESULT_OK) {
            intent?.getStringExtra(Intents.Scan.RESULT)
        } else {
            null
        }
    }
}
