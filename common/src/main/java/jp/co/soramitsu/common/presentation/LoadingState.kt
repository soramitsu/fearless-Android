package jp.co.soramitsu.common.presentation

sealed class LoadingState<T> {

    class Loading<T> : LoadingState<T>()

    class Loaded<T>(val data: T) : LoadingState<T>()
}

@Suppress("UNCHECKED_CAST")
fun <T, R> LoadingState<T>.map(mapper: (T) -> R) : LoadingState<R> {
    return when(this) {
        is LoadingState.Loading<*> ->  this as LoadingState.Loading<R>
        is LoadingState.Loaded<T> -> LoadingState.Loaded(mapper(data))
    }
}
