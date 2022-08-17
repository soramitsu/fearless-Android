package jp.co.soramitsu.testshared

import org.mockito.ArgumentMatcher
import org.mockito.Mockito
import org.mockito.stubbing.OngoingStubbing

/**
 * Returns Mockito.eq() as nullable type to avoid java.lang.IllegalStateException when
 * null is returned.
 *
 * Generic T is nullable because implicitly bounded by Any?.
 */
fun <T> eq(obj: T): T = Mockito.eq<T>(obj)

/**
 * Returns Mockito.any() as nullable type to avoid java.lang.IllegalStateException when
 * null is returned.
 */
fun <T> any(): T = Mockito.any<T>()

/**
 * Returns Mockito.isA() as nullable type to avoid java.lang.IllegalStateException when
 * null is returned.
 */
fun <T> isA(classRef: Class<T>): T = Mockito.isA<T>(classRef)

fun <T> argThat(argumentMatcher: ArgumentMatcher<T>): T = Mockito.argThat<T>(argumentMatcher)

fun <T> whenever(methodCall: T): OngoingStubbing<T> =
    Mockito.`when`(methodCall)

fun <T> OngoingStubbing<T>.thenThrowUnsafe(exception: Exception) = thenAnswer {
    throw exception
}
