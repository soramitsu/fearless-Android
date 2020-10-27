package jp.co.soramitsu.common.data

import android.content.Context
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.FileProvider
import java.io.File

class FileProviderImpl(
    private val context: Context
) : FileProvider {

    override fun createFileInTempStorage(fileName: String): Single<File> {
        return Single.fromCallable {
            val cacheDir = context.externalCacheDir?.absolutePath ?: throw IllegalStateException("cache directory is unavailable")
            File(cacheDir, fileName)
        }
    }
}