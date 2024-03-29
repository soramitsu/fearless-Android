package jp.co.soramitsu.account.impl.presentation.importing.source.model

import android.net.Uri
import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.utils.Event

typealias RequestCode = Int

interface FileRequester {
    val chooseJsonFileEvent: LiveData<Event<RequestCode>>

    fun fileChosen(uri: Uri)
}
