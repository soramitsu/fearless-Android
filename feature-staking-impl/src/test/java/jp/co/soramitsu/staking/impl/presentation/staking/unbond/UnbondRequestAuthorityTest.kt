package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual authority and selection locks, with counted primitive boundaries; no keys or transport. */
class UnbondRequestAuthorityTest {
    private class Harness : AutoCloseable {
        val owner = SupervisorJob()
        val action = Job(owner)
        val execution = Job(action)
        val account = ByteArray(32) { 1 }
        val chain = mock<Chain> {
            on { id } doReturn "fixture"
            on { ecosystem } doReturn Ecosystem.Substrate
            on { addressPrefix } doReturn 42
            on { externalApi } doReturn null
        }
        val config = mock<ChainAsset> {
            on { chainId } doReturn "fixture"
            on { id } doReturn "native"
            on { precision } doReturn 2
            on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
        }
        val asset = Asset.createEmpty(config, 7L, account, minSupportedVersion = null)
            .copy(bondedInPlanksState = AssetAmountState.Known(BigInteger.valueOf(1_000)))
        fun state() = StakingState.Stash.None(chain, account, account, account)
        val state = state()
        val wallet = mock<MetaAccount> {
            on { id } doReturn 7L
            on { substrateAccountId } doReturn account
            on { chainAccounts } doReturn emptyMap()
        }
        val selection = StakingSelectionRevision()
        val requested = StakingAssetSelection.RelayChainStaking("fixture", "native")
        val authority = UnbondRequestAuthority(owner, selection)
        init { selection.changed(requested); publish() }
        fun publish() { authority.assetChanged(asset); authority.stateChanged(state) }
        fun context() = requireNotNull(authority.capture(null)).also { check(it.admitWallet(wallet)) }
        fun attempt(context: UnbondRequestAuthority.Context = context()) = requireNotNull(context.claim(action))
        fun guard(attempt: UnbondRequestAuthority.Attempt = attempt()) = attempt.authorize("intent", execution)
        fun review(context: UnbondRequestAuthority.Context) = context.confirmation(
            UnbondValidationPayload(state, BigDecimal.ONE, BigDecimal.ONE, asset, null))
        override fun close() { owner.cancel() }
    }

    private fun refused(action: () -> Unit) {
        val error = runCatching(action).exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is CancellationException || error is IllegalStateException || error is IllegalArgumentException)
    }

    @Test fun knownContextAllowsPrimitivesAndExactlyOnePhysicalAdmission() {
        Harness().use { h ->
            val context = h.context()
            val attempt = h.attempt(context)
            val guard = h.guard(attempt)
            var primitives = 0
            repeat(2) { guard.runIfAuthorized("intent") { primitives++ } }
            var writes = 0
            assertEquals("ack", guard.runSubmissionIfAuthorized("intent") { writes++; "ack" })
            refused { guard.runSubmissionIfAuthorized("intent") { writes++ } }
            refused { h.guard(attempt).runSubmissionIfAuthorized("intent") { writes++ } }
            refused { guard.runIfAuthorized("intent") { primitives++ } }
            assertNull(context.claim(h.action))
            assertEquals(2, primitives)
            assertEquals(1, writes)
        }
    }

    @Test fun wrongIntentCannotReachAPrimitiveOrConsumeAValidAdmission() {
        Harness().use { h ->
            val guard = h.guard()
            var writes = 0
            refused { guard.runIfAuthorized("different") { writes++ } }
            refused { guard.runSubmissionIfAuthorized("different") { writes++ } }
            assertEquals(0, writes)
            guard.runSubmissionIfAuthorized("intent") { writes++ }
            assertEquals(1, writes)
        }
    }

    @Test fun everyRevocationBlocksTheFinalPrimitive() {
        val changes: List<(Harness, UnbondRequestAuthority.Attempt) -> Unit> = listOf(
            { h, _ -> h.authority.amountInput.value = "2" },
            { h, _ -> h.authority.assetChanged(h.asset.copy(bondedInPlanksState = AssetAmountState.Missing)) },
            { h, _ -> h.authority.stateChanged(h.state()) },
            { h, _ -> h.selection.changed(h.requested) },
            { _, _ -> WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() },
            { h, _ -> h.owner.cancel() },
            { h, _ -> h.action.cancel() },
            { h, _ -> h.execution.cancel() },
            { h, _ -> h.authority.revoke() },
            { _, attempt -> attempt.release() },
            { h, _ -> h.attempt() }
        )
        for (change in changes) Harness().use { h ->
            val attempt = h.attempt()
            val guard = h.guard(attempt)
            var calls = 0
            change(h, attempt)
            refused { guard.runIfAuthorized("intent") { calls++ } }
            refused { guard.runSubmissionIfAuthorized("intent") { calls++ } }
            assertEquals(0, calls)
        }
    }

    @Test fun anAdmittedPrimitiveSerializesBeforeEachRevocation() = runBlocking<Unit> {
        val changes: List<(Harness) -> Unit> = listOf(
            { it.authority.amountInput.value = "2" },
            { it.authority.assetChanged(it.asset.copy(bondedInPlanksState = AssetAmountState.Missing)) },
            { it.authority.stateChanged(it.state()) },
            { it.selection.changed(it.requested) },
            { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        )
        for (change in changes) Harness().use { h ->
            val guard = h.guard()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val failed = AtomicReference<Throwable?>()
            val primitive = Thread {
                try {
                    guard.runIfAuthorized("intent") {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                } catch (error: Throwable) { failed.set(error) }
            }
            val invalidator = Thread {
                try { change(h) } catch (error: Throwable) { failed.set(error) }
            }
            try {
                primitive.start()
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                invalidator.start()
                withTimeout(5_000) {
                    while (invalidator.state != Thread.State.BLOCKED && invalidator.isAlive) delay(1)
                }
                assertEquals(Thread.State.BLOCKED, invalidator.state)
                release.countDown()
                primitive.join(5_000); invalidator.join(5_000)
                assertFalse(primitive.isAlive); assertFalse(invalidator.isAlive)
                assertNull(failed.get())
                refused { guard.runIfAuthorized("intent") { fail("revocation must prevent the next primitive") } }
            } finally {
                release.countDown()
                primitive.join(5_000); invalidator.join(5_000)
                if (primitive.isAlive) primitive.interrupt()
                if (invalidator.isAlive) invalidator.interrupt()
            }
        }
    }

    @Test fun physicalWriterRunsOutsideSelectionAndAuthorityLocks() = runBlocking<Unit> {
        Harness().use { h ->
            val guard = h.guard()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val write = async(Dispatchers.Default) {
                guard.runSubmissionIfAuthorized("intent") {
                    entered.complete(Unit)
                    runBlocking { withTimeout(5_000) { release.await() } }
                    "ack"
                }
            }
            try {
                withTimeout(5_000) { entered.await() }
                withTimeout(5_000) {
                    withContext(Dispatchers.Default) {
                        h.authority.amountInput.value = "2"
                        h.selection.changed(h.requested)
                        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
                    }
                }
                release.complete(Unit)
                assertEquals("ack", withTimeout(5_000) { write.await() })
            } finally { release.complete(Unit); write.cancelAndJoin() }
        }
    }

    @Test fun unchangedInputAndSourcesRetainAuthorityButAllInputMutationApisRevoke() = runBlocking<Unit> {
        Harness().use { h ->
            var context = h.context()
            h.publish()
            h.authority.amountInput.value = "1"
            assertFalse(h.authority.amountInput.compareAndSet("other", "2"))
            assertTrue(context.isCurrent())
            assertTrue(h.authority.amountInput.compareAndSet("1", "2"))
            assertFalse(context.isCurrent())
            context = h.context()
            h.authority.amountInput.emit("3")
            assertFalse(context.isCurrent())
            context = h.context()
            assertTrue(h.authority.amountInput.tryEmit("4"))
            assertFalse(context.isCurrent())
        }
    }

    @Test fun walletAndSelectionChangesRequireFreshPublicationsFromBothSources() {
        for (wallet in listOf(false, true)) Harness().use { h ->
            val old = h.context()
            if (wallet) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
            else h.selection.changed(h.requested)
            assertFalse(old.isCurrent())
            assertNull(h.authority.capture(null))
            h.authority.assetChanged(h.asset)
            assertNull(h.authority.capture(null))
            h.authority.stateChanged(h.state)
            val refreshed = h.context()
            assertTrue(refreshed.isCurrent())
            assertFalse(old.isCurrent())
        }
    }

    @Test fun missingSourceAndForeignWalletCannotAdmitAContext() {
        Harness().use { h ->
            h.authority.assetChanged(null)
            assertNull(h.authority.capture(null))
            h.authority.assetChanged(h.asset)
            h.authority.stateChanged(null)
            assertNull(h.authority.capture(null))
            h.publish()
            val context = requireNotNull(h.authority.capture(null))
            val foreign = mock<MetaAccount> { on { id } doReturn 8L }
            assertFalse(context.admitWallet(foreign))
            assertFalse(context.isCurrent())
            assertNull(context.claim(h.action))
            assertTrue(context.admitWallet(h.wallet))
            assertTrue(context.isCurrent())
        }
    }

    @Test fun confirmationAttachesOnceAndRevokesOnReviewOrOwnerTeardown() {
        for (ownerTeardown in listOf(false, true)) Harness().use { h ->
            val context = h.context()
            val review = h.review(context)
            val confirmationOwner = Job(h.owner)
            assertFalse(review.isCurrent())
            assertTrue(review.attach(confirmationOwner))
            assertFalse(review.attach(Job(h.owner)))
            val attempt = requireNotNull(context.claim(confirmationOwner, review))
            val guard = attempt.authorize("intent", h.execution)
            if (ownerTeardown) confirmationOwner.cancel() else review.revoke()
            assertFalse(review.isCurrent())
            refused { guard.runSubmissionIfAuthorized("intent") { fail("revoked review must not write") } }
        }
    }

    @Test fun fractionalPlanksAndUnboundedExponentsCannotBeReviewed() {
        Harness().use { h ->
            for (text in listOf("0", "0.01", "1.23", "1.2300")) {
                h.authority.amountInput.value = text
                assertNotNull("Exactly representable amount $text", h.authority.capture(null))
            }
            for (text in listOf("0.001", "1.239", "1E-2147483647", "1E+2147483647")) {
                h.authority.amountInput.value = text
                assertNull("Amount must be safely and exactly representable in token planks: $text", h.authority.capture(null))
            }
        }
    }

    @Test fun invalidTokenPrecisionCannotAdmitAnAmountReview() {
        Harness().use { h ->
            for (precision in listOf(0, 255)) {
                whenever(h.config.precision).thenReturn(precision)
                assertNotNull("Supported precision $precision", h.authority.capture(null))
            }
            for (precision in listOf(-1, 256, Int.MAX_VALUE)) {
                whenever(h.config.precision).thenReturn(precision)
                assertNull("Unsupported precision $precision", h.authority.capture(null))
            }
        }
    }

    @Test fun confirmationRejectsAnAmountThatWasNotTheCapturedFeeInput() {
        Harness().use { h ->
            val context = h.context()
            val payload = UnbondValidationPayload(h.state, BigDecimal.ONE, BigDecimal.TEN, h.asset, null)
            refused { context.confirmation(payload) }
            assertTrue(context.isCurrent())
            assertNotNull(context.confirmation(payload.copy(amount = BigDecimal("1.00"))))
        }
    }

    @Test fun onlyCurrentAttemptCanRestartWithAnExactlyRepresentableChangedAmount() {
        Harness().use { h ->
            val context = h.context()
            val stale = h.attempt(context)
            val attempt = h.attempt(context)
            assertFalse(stale.requestAmountReview(BigDecimal.TEN))
            for (amount in listOf(BigDecimal.ONE, BigDecimal("-1"), BigDecimal("0.001"),
                BigDecimal("1E+2147483647"), BigDecimal("1E-2147483647"))) {
                assertFalse(attempt.requestAmountReview(amount))
                assertTrue(attempt.isCurrent())
                assertEquals("1", h.authority.amountInput.value)
            }
            assertTrue(attempt.requestAmountReview(BigDecimal.TEN))
            assertEquals("10", h.authority.amountInput.value)
            assertFalse(attempt.isCurrent()); assertFalse(context.isCurrent())
            assertFalse(attempt.requestAmountReview(BigDecimal("2")))
            assertEquals("10", h.authority.amountInput.value)
            assertEquals(0, BigDecimal.TEN.compareTo(h.context().amount))
        }
    }

    @Test fun decimalWorkBoundsRejectUnsafeInputsWithoutRoundingKnownZeroOrExactUnits() {
        for (text in listOf("0", "0.00", "1.2300", "1E2")) {
            assertNotNull(UnbondRequestAuthority.parseAmount(text, 2))
        }
        for (text in listOf("1".repeat(4097), "1E4096", "1E-4097", "0E-4097", "NaN", "-1")) {
            assertNull(UnbondRequestAuthority.parseAmount(text, 2))
        }
        assertNotNull(UnbondRequestAuthority.parseAmount("1E-255", 255))
        assertNull(UnbondRequestAuthority.parseAmount("1E-256", 255))
    }

    @Test fun walletOrSelectionRetirementRejectsWarningInputMutation() {
        for (wallet in listOf(false, true)) Harness().use { h ->
            val attempt = h.attempt()
            if (wallet) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
            else h.selection.changed(h.requested)
            assertFalse(attempt.requestAmountReview(BigDecimal.TEN))
            assertEquals("1", h.authority.amountInput.value)
            assertNull(h.authority.capture(null))
        }
    }

    @Test fun acceptedWarningInputWriteSerializesBeforeWalletAndSelectionRetirement() = runBlocking<Unit> {
        for (wallet in listOf(false, true)) Harness().use { h ->
            val attempt = h.attempt()
            val observedInput = AtomicReference<String?>()
            val writerFailure = AtomicReference<Throwable?>()
            val entered = CountDownLatch(1)
            val invalidator = Thread {
                try {
                    entered.countDown()
                    if (wallet) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
                    else h.selection.changed(h.requested)
                    observedInput.set(h.authority.amountInput.value)
                } catch (error: Throwable) { writerFailure.set(error) }
            }
            // Synchronous publication exposes the interval inside the input write, before text is replaced.
            val observer = launch(Dispatchers.Unconfined) {
                h.authority.changes.drop(1).take(1).collect {
                    invalidator.start()
                    check(entered.await(5, TimeUnit.SECONDS))
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (invalidator.isAlive && invalidator.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                        Thread.sleep(1)
                    }
                    assertEquals(Thread.State.BLOCKED, invalidator.state)
                }
            }
            try {
                assertTrue(attempt.requestAmountReview(BigDecimal.TEN))
                invalidator.join(5_000)
                assertFalse(invalidator.isAlive)
                assertNull(writerFailure.get())
                assertEquals("10", observedInput.get())
                assertFalse(attempt.isCurrent())
                assertNull(h.authority.capture(null))
            } finally {
                observer.cancelAndJoin()
                if (invalidator.state != Thread.State.NEW) invalidator.join(5_000)
                if (invalidator.isAlive) invalidator.interrupt()
            }
        }
    }
}
