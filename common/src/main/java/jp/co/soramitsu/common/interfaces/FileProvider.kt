package jp.co.soramitsu.common.interfaces

import io.reactivex.Single
import java.io.File

interface FileProvider {

    fun createFileInTempStorage(fileName: String): Single<File>
}