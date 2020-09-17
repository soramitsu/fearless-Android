package jp.co.soramitsu.feature_account_impl.presentation.importing

import android.content.Context
import android.net.Uri

class FileReader(private val context: Context) {
    fun readFile(uri: Uri) : String? {
        val inputString = context.contentResolver.openInputStream(uri)

        return inputString?.reader()?.readText()
    }
}