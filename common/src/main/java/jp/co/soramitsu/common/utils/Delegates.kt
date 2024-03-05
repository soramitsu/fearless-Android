package jp.co.soramitsu.common.utils

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

inline fun <T> Delegates.cachedOrNew(
    crossinline isCorrupted: (T) -> Boolean,
    crossinline factory: () -> T
): ReadOnlyProperty<Any?, T> = object : ReadOnlyProperty<Any?, T> {
    private val mutex = Mutex()
    private var _value: T = factory()

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = cachedOrNew()

    private fun cachedOrNew(): T {
        if (isCorrupted(_value)) {
            runBlocking {
                mutex.withLock {
                    if (isCorrupted(_value)) {
                        _value = factory()
                    }
                }
            }
        }
        return _value
    }
}