package jp.co.soramitsu.common.data

import android.content.Context
import jp.co.soramitsu.common.interfaces.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileProviderImpl(
    private val context: Context
) : FileProvider {

    override suspend fun getFileInExternalCacheStorage(fileName: String): File {
        return withContext(Dispatchers.IO) {
            val cacheDir = context.externalCacheDir?.absolutePath ?: directoryNotAvailable()

            File(cacheDir, fileName)
        }
    }

    override suspend fun getFileInInternalCacheStorage(fileName: String): File {
        return withContext(Dispatchers.IO) {
            val cacheDir = context.cacheDir?.absolutePath ?: directoryNotAvailable()

            File(cacheDir, fileName)
        }
    }

    private fun directoryNotAvailable(): Nothing {
        throw IllegalStateException("Cache directory is unavailable")
    }
}
