package jp.co.soramitsu.common.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

inline fun <T, R> Flow<List<T>>.mapList(crossinline mapper: suspend (T) -> R) = map { list ->
    list.map { item -> mapper(item) }
}

fun <T> Flow<T>.asLiveData(scope: CoroutineScope): LiveData<T> {
    val liveData = MutableLiveData<T>()

    onEach {
        liveData.value = it
    }.launchIn(scope)

    return liveData
}