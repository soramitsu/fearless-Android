package jp.co.soramitsu.feature_account_impl.presentation.importing

import android.content.Context
import android.net.Uri
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileReader(private val context: Context) {

    suspend fun readFile(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            val inputString = context.contentResolver.openInputStream(uri)

            inputString?.reader()?.readText()
        }
    }
}