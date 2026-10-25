package jp.co.soramitsu.staking.impl.domain.rewards

import com.google.gson.Gson
import java.math.BigInteger
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcError
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SoraConversionQuoteTest {
    private class SocketFailure(val marker: Any) : RuntimeException("quote socket failed")
    private class QuoteCancelled(val marker: Any) : CancellationException("quote cancelled")
    private class Fixture(result: Any? = null, failure: Throwable? = null, error: RpcError? = null, held: Boolean = false) {
        lateinit var request: RuntimeRequest
        lateinit var delivery: DeliveryType
        val cancelled = mock<SocketService.Cancellable>()
        val socket = mock<SocketService> { on { jsonMapper } doReturn Gson() }
        init {
            whenever(socket.executeRequest(any(), any(), any())).thenAnswer {
                request = it.getArgument(0)
                delivery = it.getArgument(1)
                val callback = it.getArgument<SocketService.ResponseListener<RpcResponse>>(2)
                if (!held) {
                    if (failure != null) callback.onError(failure)
                    else callback.onNext(RpcResponse("2.0", result, request.id, error))
                }
                cancelled
            }
        }
        val connection = mock<ChainConnection> { on { socketService } doReturn socket }
        val registry = mock<IChainRegistry> { on { getConnection(CHAIN) } doReturn connection }
        val scenario = SoraStakingRewardsScenario(RpcCalls(registry), mock(), mock())
    }

    @Test fun positiveQuoteRetainsExactWireRequestAndPlankValue() = runTest {
        val planks = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(2))
        val f = Fixture(mapOf("amount" to planks.toString(), "route" to listOf("XYKPool")))
        assertEquals(planks, f.scenario.mainAssetToRewardAssetRate())
        assertEquals("liquidityProxy_quote", f.request.method)
        assertEquals(DeliveryType.AT_LEAST_ONCE, f.delivery)
        assertEquals(listOf(0, SoraStakingRewardsScenario.STAKING_ASSET_CURRENCY_ID,
            SoraStakingRewardsScenario.REWARD_ASSET_CURRENCY_ID, "1000000000000000000",
            "WithDesiredInput", emptyList<String>(), "Disabled"), f.request.params)
        verify(f.registry, times(1)).getConnection(CHAIN)
    }

    @Test fun absentQuoteOrMissingAmountNeverBecomesZeroRate() = runTest {
        for (result in listOf(null, emptyMap<String, Any>(), mapOf("amount" to null))) {
            val error = runCatching { Fixture(result).scenario.mainAssetToRewardAssetRate() }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        }
    }

    @Test fun malformedAndNonpositiveAmountsAreUnavailable() = runTest {
        for (amount in listOf("not-a-number", "0", "-1")) {
            assertNotNull(runCatching { Fixture(mapOf("amount" to amount)).scenario.mainAssetToRewardAssetRate() }.exceptionOrNull())
        }
    }

    @Test fun ordinarySocketFailurePreservesItsExactIdentity() = runTest {
        val failure = SocketFailure(Any())
        assertSame(failure, runCatching { Fixture(failure = failure).scenario.mainAssetToRewardAssetRate() }.exceptionOrNull())
    }

    @Test fun rpcErrorRemainsAFailure() = runTest {
        val failure = runCatching { Fixture(error = RpcError(-32601, "Quote unavailable")).scenario.mainAssetToRewardAssetRate() }.exceptionOrNull()
        assertTrue(failure is RpcException)
    }

    @Test fun callbackCancellationPreservesItsExactIdentity() = runTest {
        val reason = QuoteCancelled(Any())
        assertSame(reason, runCatching { Fixture(failure = reason).scenario.mainAssetToRewardAssetRate() }.exceptionOrNull())
    }

    @Test fun callerCancellationCancelsTheHeldSocketRequest() = runTest {
        val f = Fixture(held = true)
        val reason = QuoteCancelled(Any())
        val job = launch { f.scenario.mainAssetToRewardAssetRate() }
        var completion: Throwable? = null
        job.invokeOnCompletion { completion = it }
        runCurrent()
        verify(f.socket, times(1)).executeRequest(any(), any(), any())
        job.cancel(reason); runCurrent(); job.join()
        assertSame(reason, completion)
        verify(f.cancelled, times(1)).cancel()
    }

    private companion object {
        const val CHAIN = "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
    }
}
