package jp.co.soramitsu.androidfoundation.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher

open class CoroutineManager(
    open val io: CoroutineDispatcher = Dispatchers.IO,
    open val default: CoroutineDispatcher = Dispatchers.Default,
    open val main: MainCoroutineDispatcher = Dispatchers.Main
)
