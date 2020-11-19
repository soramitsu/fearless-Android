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

class Optional<M>(val value: M?)

inline fun <T, R> Observable<T>.mapExcludingNull(crossinline mapper: (T) -> R?): Observable<R> = mapNullable {
    mapper(it)
}
    .filterNonNull()

inline fun <T, R> Observable<T>.mapNullable(crossinline mapper: (T) -> R?): Observable<Optional<R>> = map {
    Optional(mapper(it))
}

fun <T> Observable<Optional<T>>.filterNonNull(): Observable<T> = filter { it.value != null }
    .map { it.value!! }

fun <T> Observable<Optional<T>>.asOptionalLiveData(
    disposable: CompositeDisposable,
    errorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
): LiveData<T?> = asLiveData(disposable, errorHandler)
    .map { it.value }

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

fun <T, R> Observable<List<T>>.mapList(mapper: (T) -> R): Observable<List<R>> {
    return map { list -> list.map(mapper) }
}

fun <T, R> Single<List<T>>.mapList(mapper: (T) -> R): Single<List<R>> {
    return map { list -> list.map(mapper) }
}

fun List<Single<*>>.zip() = Single.zip(this) { values ->
    ComponentHolder(values.toList())
}

@Suppress("UNCHECKED_CAST") fun <R> List<Single<out R>>.zipSimilar(): Single<List<R>> {
    if (isEmpty()) return Single.just(emptyList())

    return Single.zip(this) { values ->
        val casted = values as Array<out R>

        casted.toList()
    }
}