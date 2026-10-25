package jp.co.soramitsu.fearless_utils.common

import org.junit.Assert
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <reified T> assertInstance(value: Any?) {
    contract {
        returns() implies (value is T)
    }

    Assert.assertTrue("$value is not a ${T::class}", value is T)
}

inline fun <reified T> assertNotInstance(value: Any?) {
    Assert.assertTrue("$value is a ${T::class}", value !is T)
}

inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
    var throwable: Throwable? = null

    try {
        block()
    } catch (t: Throwable) {
        throwable = t
    }

    Assert.assertNotNull("No error was thrown", throwable)
    Assert.assertTrue(
        "${T::class} expected, but ${throwable!!::class} thrown",
        throwable is T || throwable.cause is T
    )

    return throwable as? T ?: throwable.cause as T
}