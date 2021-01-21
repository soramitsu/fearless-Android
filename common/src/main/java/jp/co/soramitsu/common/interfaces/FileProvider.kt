package jp.co.soramitsu.common.interfaces

import java.io.File

interface FileProvider {

    suspend fun createFileInTempStorage(fileName: String): File
}