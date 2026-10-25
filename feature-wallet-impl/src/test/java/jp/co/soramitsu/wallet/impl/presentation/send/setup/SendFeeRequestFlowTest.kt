package jp.co.soramitsu.wallet.impl.presentation.send.setup

import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class SendFeeRequestFlowTest {
    @Test fun `empty native amount skips quote and an edit after fee failure still loads`() = runTest {
        val asset = BitcoinTransferServiceProviderTest.bitcoinChain().assets.single()
        val initial = Transfer("sender", "recipient", BigDecimal.ZERO, asset)
        val requests = MutableStateFlow<Transfer?>(initial); val results = mutableListOf<BigDecimal?>(); var calls = 0; var failures = 0
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            SendFeeRequestFlow.observe(requests, { request -> flow {
                calls++
                if (request.amount == BigDecimal.ONE) throw java.io.IOException("temporary provider failure")
                emit(BigDecimal.TEN)
            } }) { failures++ }.collect { results += it }
        }
        runCurrent(); assertEquals(0, calls); assertEquals(listOf<BigDecimal?>(null), results)
        requests.value = initial.copy(amount = BigDecimal.ONE); runCurrent()
        assertEquals(4, calls); assertEquals(1, failures); assertEquals(null, results.last())
        requests.value = initial.copy(amount = BigDecimal("2")); runCurrent()
        assertEquals(BigDecimal.TEN, results.last()); assertEquals(5, calls)
        job.cancel()
    }

    @Test fun `recipient edits cancel a suspended old estimate instead of publishing it later`() = runTest {
        val asset = BitcoinTransferServiceProviderTest.solanaChain().assets.single()
        val initial = Transfer("sender", "old", BigDecimal.ONE, asset)
        val requests = MutableStateFlow<Transfer?>(initial); val release = CompletableDeferred<Unit>(); val results = mutableListOf<BigDecimal?>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            SendFeeRequestFlow.observe(requests, { request -> flow {
                if (request.recipient == "old") { release.await(); emit(BigDecimal.ONE) } else emit(BigDecimal.TEN)
            } }) { error("Canceled quote must not show a fee error") }.collect { results += it }
        }
        runCurrent(); requests.value = initial.copy(recipient = "new"); runCurrent(); release.complete(Unit); runCurrent()
        assertEquals(listOf(BigDecimal.TEN), results); job.cancel()
    }
}
