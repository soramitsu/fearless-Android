package jp.co.soramitsu.common.presentation

sealed class LoadingState<T> {

    class Loading<T> : LoadingState<T>()

    class Loaded<T>(val data: T) : LoadingState<T>()
}
