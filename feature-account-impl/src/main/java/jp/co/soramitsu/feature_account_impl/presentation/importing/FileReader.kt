package jp.co.soramitsu.feature_account_impl.presentation.importing

import android.content.Context
import android.net.Uri
import io.reactivex.Single

class FileReader(private val context: Context) {

    fun readFile(uri: Uri): Single<String> {
        return Single.fromCallable {
            val inputString = context.contentResolver.openInputStream(uri)

            inputString?.reader()?.readText()
        }
    }
}