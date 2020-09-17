package jp.co.soramitsu.common.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

typealias ErrorHandler = (Throwable) -> Unit

val DEFAULT_ERROR_HANDLER: ErrorHandler = Throwable::printStackTrace

fun <T> Single<T>.asLiveData(
    disposable: CompositeDisposable,
    errorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
): LiveData<T> {
    val liveData = MutableLiveData<T>()

    disposable.add(subscribe({
        liveData.value = it
    }, errorHandler))

    return liveData
}

fun <T> Single<T>.asMutableLiveData(
    disposable: CompositeDisposable,
    errorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
): MutableLiveData<T> {
    val liveData = MutableLiveData<T>()

    disposable.add(subscribe({
        liveData.value = it
    }, errorHandler))

    return liveData
}

fun <T> Observable<T>.asLiveData(
    disposable: CompositeDisposable,
    errorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
): LiveData<T> {
    val liveData = MutableLiveData<T>()

    disposable.add(subscribe({
        liveData.value = it
    }, errorHandler))

    return liveData
}

fun <T> Observable<T>.asMutableLiveData(
    disposable: CompositeDisposable,
    errorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
): MutableLiveData<T> {
    val liveData = MutableLiveData<T>()

    disposable.add(subscribe({
        liveData.value = it
    }, errorHandler))

    return liveData
}

operator fun CompositeDisposable.plusAssign(child: Disposable) {
    add(child)
}

fun Completable.subscribeToError(onError: (Throwable) -> Unit) = subscribe({ }, onError)