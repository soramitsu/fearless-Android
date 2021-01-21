@file:Suppress("BlockingMethodInNonBlockingContext")

package jp.co.soramitsu.common.utils

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * @param quality - integer between 1 and 100
 */
suspend fun File.write(bitmap: Bitmap, quality: Int = 100) {
    withContext(Dispatchers.IO) {
        val outputStream = FileOutputStream(this@write)

        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        outputStream.close()
    }
}