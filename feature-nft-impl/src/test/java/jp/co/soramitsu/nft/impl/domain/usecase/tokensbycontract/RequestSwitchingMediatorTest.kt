package jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract

import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RequestSwitchingMediatorTest {
    @Test
    fun switchingForwardStartsNextSourceAndSwitchingBackResumesLastPage() = runTest {
        val requests = MutableSharedFlow<PaginationRequest>(extraBufferCapacity = 1)
        val observed = mutableListOf<Pair<Int, PaginationRequest>>()
        lateinit var switch: RequestSwitchingMediator.SwitchFlowHandle
        val first = RequestSwitchingMediator.Node(0) { input, handle ->
            switch = handle
            input.map { 0 to it }
        }
        val second = RequestSwitchingMediator.Node(1) { input, _ -> input.map { 1 to it } }
        backgroundScope.launch { RequestSwitchingMediator()(requests, first, second).collect { observed += it } }
        runCurrent()
        requests.emit(PaginationRequest.Start(5))
        runCurrent()
        assertEquals(listOf(0 to PaginationRequest.Start(5)), observed)
        switch.switchToFlowWithFlag(1)
        runCurrent()
        assertEquals(1 to PaginationRequest.Start(100), observed.last())
        requests.emit(PaginationRequest.Next(5))
        runCurrent()
        assertEquals(1 to PaginationRequest.Next(5), observed.last())
        switch.switchToFlowWithFlag(0)
        runCurrent()
        assertEquals(0 to PaginationRequest.ProceedFromLastPage, observed.last())
        assertEquals(4, observed.size)
    }
}
