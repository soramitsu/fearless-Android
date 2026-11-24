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

    override suspend fun getFileInInternalShareStorage(fileName: String): File {
        require(fileName !in RESERVED_FILE_NAMES && File(fileName).name == fileName) {
            "A shared file name must not contain a path"
        }

        return withContext(Dispatchers.IO) {
            val cacheDir = context.cacheDir ?: directoryNotAvailable()
            val shareDir = File(cacheDir, INTERNAL_SHARE_DIRECTORY)
            if (!shareDir.isDirectory && !shareDir.mkdirs()) directoryNotAvailable()

            File(shareDir, fileName)
        }
    }

    private fun directoryNotAvailable(): Nothing {
        throw IllegalStateException("Cache directory is unavailable")
    }

    companion object {
        /**
         * The only internal directory the app's FileProvider serves; it must
         * match the cache-path entry in app/src/main/res/xml/provider_paths.xml.
         */
        const val INTERNAL_SHARE_DIRECTORY = "shared_exports"

        private val RESERVED_FILE_NAMES = setOf("", ".", "..")
    }
}
