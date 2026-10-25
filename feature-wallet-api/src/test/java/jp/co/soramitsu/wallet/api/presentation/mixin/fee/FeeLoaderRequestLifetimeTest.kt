package jp.co.soramitsu.wallet.api.presentation.mixin.fee

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import io.mockk.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class FeeLoaderRequestLifetimeTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @Before fun main() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }
    private class ReadFailure(val marker: Any) : RuntimeException("fee fixture read failed")
    private class ReadCancelled(val marker: Any) : CancellationException("fee fixture cancelled")

    private class Fixture {
        private val releases = CopyOnWriteArrayList<CompletableDeferred<Unit>>()
        fun gate() = CompletableDeferred<Unit>().also { releases += it }
        fun releaseAll() { releases.forEach { it.complete(Unit) } }
        val unhandled = CopyOnWriteArrayList<Throwable>()
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + Dispatchers.Unconfined + CoroutineExceptionHandler { _, failure -> unhandled += failure })
        val config = mockk<Asset> {
            every { chainId } returns "fixture"
            every { id } returns "native"
            every { precision } returns 2
            every { symbol } returns "UNIT"
        }
        val token = Token(config, null, null, null)
        val tokens = mockk<TokenUseCase> { coEvery { currentToken() } returns token }
        val resources = mockk<ResourceManager>(relaxed = true) {
            every { getString(any()) } answers { "text-${firstArg<Int>()}" }
        }
        val provider = FeeLoaderProvider(resources, tokens)
        suspend fun joinCurrent() {
            while (true) {
                val children = owner.children.toList()
                if (children.isEmpty()) return
                children.joinAll()
            }
        }
        fun fee(): BigDecimal? {
            var fee: BigDecimal? = null
            provider.requireFee({ fee = it }, { _, _ -> })
            return fee
        }
    }
    private fun exercise(body: suspend Fixture.() -> Unit) = runBlocking<Unit> {
        val fixture = Fixture()
        try { withTimeout(5_000) { fixture.body() } }
        finally {
            fixture.releaseAll()
            withTimeout(5_000) { fixture.owner.cancelAndJoin() }
        }
    }

    @Test fun lateOldRequestCannotReplaceCurrentFeeOrInvokeItsCompletion() = exercise {
        val entered = CompletableDeferred<Unit>(); val release = gate()
        val currentDone = CompletableDeferred<Unit>(); val oldCallbacks = AtomicInteger()
        provider.loadFee(scope, {
            withContext(NonCancellable) { entered.complete(Unit); release.await() }
            BigInteger.valueOf(100)
        }, {}, { oldCallbacks.incrementAndGet() })
        entered.await()
        provider.loadFee(scope, { BigInteger.valueOf(200) }, {}, { currentDone.complete(Unit) })
        currentDone.await()
        assertEquals(0, BigDecimal("2").compareTo(requireNotNull(fee())))
        release.complete(Unit); joinCurrent()
        assertEquals(0, BigDecimal("2").compareTo(requireNotNull(fee())))
        assertEquals(0, oldCallbacks.get())
        assertTrue(unhandled.isEmpty())
    }

    @Test fun tokenLookupFailurePublishesUnavailableAndCanRecoverOnExplicitReload() = exercise {
        coEvery { tokens.currentToken() } throws ReadFailure(Any())
        provider.loadFee(scope, { error("A missing token must not reach fee construction") }, {})
        joinCurrent()
        assertSame(FeeStatus.Error, provider.feeLiveData.value)
        assertNull(fee())
        assertTrue(unhandled.isEmpty())
        coEvery { tokens.currentToken() } returns token
        provider.loadFee(scope, { BigInteger.ZERO }, {})
        joinCurrent()
        assertEquals(0, BigDecimal.ZERO.compareTo(requireNotNull(fee())))
    }

    @Test fun independentConstructorCancellationRetainsReasonAndSkipsCompletion() = exercise {
        val reason = ReadCancelled(Any()); val entered = CompletableDeferred<Unit>(); val release = gate()
        val callbacks = AtomicInteger(); var completion: Throwable? = null
        provider.loadFee(scope, { entered.complete(Unit); release.await(); throw reason }, {}, { callbacks.incrementAndGet() })
        entered.await()
        val child = owner.children.single()
        child.invokeOnCompletion { completion = it }
        release.complete(Unit); child.join()
        assertSame(reason, completion)
        assertEquals(0, callbacks.get())
        assertNull(fee())
        assertTrue(unhandled.isEmpty())
    }

    @Test fun cancelledOwnerCannotPublishALateNonCancellableResult() = exercise {
        val entered = CompletableDeferred<Unit>(); val release = gate(); val callbacks = AtomicInteger()
        provider.loadFee(scope, {
            withContext(NonCancellable) { entered.complete(Unit); release.await() }
            BigInteger.valueOf(100)
        }, {}, { callbacks.incrementAndGet() })
        entered.await(); owner.cancel(ReadCancelled(Any())); release.complete(Unit); owner.join()
        assertFalse(provider.feeLiveData.value is FeeStatus.Loaded)
        assertNull(fee())
        assertEquals(0, callbacks.get())
        assertTrue(unhandled.isEmpty())
    }

    @Test fun ordinaryFeeFailureRemainsAnErrorAndCannotAuthorizeAValue() = exercise {
        provider.loadFee(scope, { throw ReadFailure(Any()) }, {})
        joinCurrent()
        assertSame(FeeStatus.Error, provider.feeLiveData.value)
        assertNull(fee())
        assertNotNull(provider.retryEvent.value)
        assertTrue(unhandled.isEmpty())
    }

    @Test fun explicitlyKnownZeroFeeRemainsAvailable() = exercise {
        val callbacks = AtomicInteger()
        provider.loadFee(scope, { BigInteger.ZERO }, {}, { callbacks.incrementAndGet() })
        joinCurrent()
        assertTrue(provider.feeLiveData.value is FeeStatus.Loaded)
        assertEquals(0, BigDecimal.ZERO.compareTo(requireNotNull(fee())))
        assertEquals(1, callbacks.get())
        assertTrue(unhandled.isEmpty())
    }

    @Test fun oldRetryAndCancelCallbacksCannotSupersedeANewerSuccessfulRequest() = exercise {
        val calls = AtomicInteger(); val cancelled = AtomicInteger()
        provider.loadFee(scope, { calls.incrementAndGet(); throw ReadFailure(Any()) }, { cancelled.incrementAndGet() })
        joinCurrent()
        val old = requireNotNull(provider.retryEvent.value).peekContent()
        provider.loadFee(scope, { BigInteger.valueOf(200) }, {})
        joinCurrent()
        old.onRetry?.invoke(); old.onCancel?.invoke(); joinCurrent()
        assertEquals(1, calls.get())
        assertEquals(0, cancelled.get())
        assertEquals(0, BigDecimal("2").compareTo(requireNotNull(fee())))
        assertTrue(unhandled.isEmpty())
    }

    @Test fun negativeFeeCannotAuthorizeAValue() = exercise {
        provider.loadFee(scope, { BigInteger.valueOf(-1) }, {})
        joinCurrent()
        assertSame(FeeStatus.Error, provider.feeLiveData.value)
        assertNull(fee())
        assertTrue(unhandled.isEmpty())
    }

    @Test fun tokenLookupCancellationRetainsItsReasonAndSkipsCompletion() = exercise {
        val reason = ReadCancelled(Any()); val entered = CompletableDeferred<Unit>(); val release = gate()
        val callbacks = AtomicInteger(); var completion: Throwable? = null
        coEvery { tokens.currentToken() } coAnswers { entered.complete(Unit); release.await(); throw reason }
        provider.loadFee(scope, { error("A cancelled token read must not construct a fee") }, {}, { callbacks.incrementAndGet() })
        entered.await()
        val child = owner.children.single()
        child.invokeOnCompletion { completion = it }
        release.complete(Unit); child.join()
        assertSame(reason, completion)
        assertEquals(0, callbacks.get())
        assertNull(fee())
        assertTrue(unhandled.isEmpty())
    }


    @Test fun invalidationClearsAKnownFeeBeforeAnyReplacementRequest() = exercise {
        provider.loadFee(scope, { BigInteger.valueOf(100) }, {})
        joinCurrent()
        assertNotNull(fee())
        provider.invalidate()
        assertSame(FeeStatus.Loading, provider.feeLiveData.value)
        assertNull(fee())
    }

    @Test fun invalidationRejectsLateResultsAndRetainedRetryCallbacks() = exercise {
        val cancelled = AtomicInteger(); val calls = AtomicInteger()
        provider.loadFee(scope, { calls.incrementAndGet(); throw ReadFailure(Any()) }, { cancelled.incrementAndGet() })
        joinCurrent()
        val old = requireNotNull(provider.retryEvent.value).peekContent()
        val entered = CompletableDeferred<Unit>(); val release = gate(); val callbacks = AtomicInteger()
        provider.loadFee(scope, {
            withContext(NonCancellable) { entered.complete(Unit); release.await() }
            BigInteger.ONE
        }, {}, { callbacks.incrementAndGet() })
        entered.await(); provider.invalidate()
        old.onRetry?.invoke(); old.onCancel?.invoke()
        release.complete(Unit); joinCurrent()
        assertSame(FeeStatus.Loading, provider.feeLiveData.value)
        assertNull(fee()); assertEquals(0, callbacks.get())
        assertEquals(1, calls.get()); assertEquals(0, cancelled.get())
        assertTrue(unhandled.isEmpty())
    }


    @Test fun observerStartingANewRequestRevokesTheOldCompletion() = exercise {
        val oldCallbacks = AtomicInteger(); val newCallbacks = AtomicInteger(); var replaced = false
        val observer = Observer<FeeStatus> { status ->
            if (status is FeeStatus.Loaded && !replaced) {
                replaced = true
                provider.loadFee(scope, { BigInteger.valueOf(200) }, {}, { newCallbacks.incrementAndGet() })
            }
        }
        provider.feeLiveData.observeForever(observer)
        try {
            provider.loadFee(scope, { BigInteger.valueOf(100) }, {}, { oldCallbacks.incrementAndGet() })
            joinCurrent()
            assertEquals(0, BigDecimal("2").compareTo(requireNotNull(fee())))
            assertEquals(0, oldCallbacks.get()); assertEquals(1, newCallbacks.get())
            assertTrue(unhandled.isEmpty())
        } finally { provider.feeLiveData.removeObserver(observer) }
    }

}
