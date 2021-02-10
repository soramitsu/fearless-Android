package jp.co.soramitsu.test_shared

import org.mockito.Mockito

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