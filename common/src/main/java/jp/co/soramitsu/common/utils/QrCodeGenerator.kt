package jp.co.soramitsu.common.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.core.graphics.drawable.toBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resources.ResourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QrCodeGenerator(
    private val firstColor: Int,
    private val secondColor: Int,
    private val resourceManager: ResourceManager
) {

    companion object {
        private const val RECEIVE_QR_SCALE_SIZE = 1024
        private const val PADDING_SIZE = 2
    }

    suspend fun generateQrBitmap(input: String): Bitmap {
        return withContext(Dispatchers.Default) {
            val hints = HashMap<EncodeHintType, String>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            val qrCode = Encoder.encode(input, ErrorCorrectionLevel.H, hints)
            val byteMatrix = qrCode.matrix
            val width = byteMatrix.width + PADDING_SIZE
            val height = byteMatrix.height + PADDING_SIZE
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val overlayBitmap = resourceManager.getDrawable(R.drawable.ic_qr_code_logo).toBitmap(
                height = 200,
                width = 400
            )
            val overlayBitmapBackground = resourceManager.getDrawable(R.drawable.bg_corner_white_overlay).toBitmap(
                height = 220,
                width = 430
            )
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (y == 0 || y > byteMatrix.height || x == 0 || x > byteMatrix.width) {
                        bitmap.setPixel(x, y, secondColor)
                    } else {
                        bitmap.setPixel(x, y, if (byteMatrix.get(x - PADDING_SIZE / 2, y - PADDING_SIZE / 2).toInt() == 1) firstColor else secondColor)
                    }
                }
            }
            Bitmap.createScaledBitmap(bitmap, RECEIVE_QR_SCALE_SIZE, RECEIVE_QR_SCALE_SIZE, false).apply {
                addOverlayToCenter(overlayBitmapBackground)
                addOverlayToCenter(overlayBitmap)
            }
        }
    }

    private fun Bitmap.addOverlayToCenter(overlayBitmap: Bitmap): Bitmap {
        val bitmap2Width = overlayBitmap.width
        val bitmap2Height = overlayBitmap.height
        val marginLeft = (this.width * 0.5 - bitmap2Width * 0.5).toFloat()
        val marginTop = (this.height * 0.5 - bitmap2Height * 0.5).toFloat()
        val canvas = Canvas(this)
        canvas.drawBitmap(this, Matrix(), null)
        canvas.drawBitmap(overlayBitmap, marginLeft, marginTop, null)
        return this
    }
}
