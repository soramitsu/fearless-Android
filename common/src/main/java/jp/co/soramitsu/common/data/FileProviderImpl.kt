package jp.co.soramitsu.common.data

import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.FileProvider
import java.io.File

class FileProviderImpl(
    private val cacheDir: String
) : FileProvider {

    override fun createFileInTempStorage(fileName: String): Single<File> {
        return Single.fromCallable {
            File(cacheDir, fileName)
        }
    }
}