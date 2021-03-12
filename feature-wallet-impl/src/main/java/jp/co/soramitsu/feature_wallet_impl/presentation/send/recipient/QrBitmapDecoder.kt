package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QrBitmapDecoder(
    private val contentResolver: ContentResolver
) {
    class DecodeException : Exception()

    suspend fun decodeQrCodeFromUri(data: Uri) = runCatching {
        decode(data)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun decode(data: Uri): String {
        return withContext(Dispatchers.IO) {
            val qrBitmap = MediaStore.Images.Media.getBitmap(contentResolver, data)

            val pixels = IntArray(qrBitmap.height * qrBitmap.width)
            qrBitmap.getPixels(pixels, 0, qrBitmap.width, 0, 0, qrBitmap.width, qrBitmap.height)
            qrBitmap.recycle()
            val source = RGBLuminanceSource(qrBitmap.width, qrBitmap.height, pixels)
            val bBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()

            val textResult = reader.decode(bBitmap).text

            if (textResult.isNullOrEmpty()) {
                throw DecodeException()
            } else {
                textResult
            }
        }
    }
}
