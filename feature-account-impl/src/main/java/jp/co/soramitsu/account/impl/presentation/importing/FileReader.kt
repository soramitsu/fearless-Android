package jp.co.soramitsu.account.impl.presentation.importing

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileReader(private val context: Context) {

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun readFile(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.reader()?.use(::readBoundedJson)
        }
    }
    companion object {
        const val MAX_JSON_CHARS = 262144

        internal fun readBoundedJson(reader: java.io.Reader): String {
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer, 0, minOf(buffer.size, MAX_JSON_CHARS + 1 - result.length))
                if (count < 0) return result.toString()
                result.append(buffer, 0, count)
                require(result.length <= MAX_JSON_CHARS) { "Wallet import file exceeds the safe limit" }
            }
        }
    }
}
