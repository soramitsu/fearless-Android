package jp.co.soramitsu.common.data.network.nomis

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.testshared.whenever
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PreferenceAwareNomisApiTest {
    private val response = mock(NomisResponse::class.java)

    @Test
    fun `disabled scoring never forwards a wallet or recipient address`() = runTest {
        val enabled = MutableStateFlow(false)
        var calls = 0
        val api = api(enabled) {
            calls++
            response
        }

        for (address in listOf("wallet-address", "recipient-address")) {
            val result = runCatching { api.getNomisScore(address) }
            assertTrue(result.exceptionOrNull() is CancellationException)
        }
        assertEquals(0, calls)
    }

    @Test
    fun `disabling scoring cancels an in flight provider request`() = runTest {
        val enabled = MutableStateFlow(true)
        var cancelled = false
        val api = api(enabled) {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val request = async { api.getNomisScore("wallet-address") }
        runCurrent()
        enabled.value = false
        runCurrent()

        assertTrue(request.isCancelled)
        assertTrue(cancelled)
    }

    @Test
    fun `enabled requests preserve response and can resume after opt in`() = runTest {
        val enabled = MutableStateFlow(false)
        val addresses = mutableListOf<String>()
        val api = api(enabled) { address ->
            addresses.add(address)
            response
        }
        assertTrue(runCatching { api.getNomisScore("disabled") }.isFailure)

        enabled.value = true
        assertSame(response, api.getNomisScore("enabled"))
        enabled.value = false
        assertTrue(runCatching { api.getNomisScore("disabled-again") }.isFailure)
        assertEquals(listOf("enabled"), addresses)
    }

    @Test
    fun `provider errors propagate without leaking an observer`() = runTest {
        val enabled = MutableStateFlow(true)
        val failure = IllegalStateException("provider unavailable")
        val api = api(enabled) { throw failure }

        val propagated = runCatching { api.getNomisScore("wallet-address") }.exceptionOrNull()
        assertTrue(propagated is IllegalStateException)
        assertEquals(failure.message, propagated?.message)
        enabled.value = false
        runCurrent()
    }

    private fun api(enabled: MutableStateFlow<Boolean>, fetch: suspend (String) -> NomisResponse): NomisApi {
        val preferences = mock(Preferences::class.java)
        whenever(preferences.getBoolean(NOMIS_SCORING_ENABLED_KEY, true)).thenAnswer { enabled.value }
        whenever(preferences.booleanFlow(NOMIS_SCORING_ENABLED_KEY, true)).thenReturn(enabled)
        return PreferenceAwareNomisApi(
            object : NomisApi {
                override suspend fun getNomisScore(address: String) = fetch(address)
            },
            preferences
        )
    }
}
