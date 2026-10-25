package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigInteger
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.select.SelectUnbondViewModel
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Desired safety invariants; this test-only baseline is expected to expose the unchanged unbond callers. */
@OptIn(ExperimentalCoroutinesApi::class)
class UnbondAvailabilityAuthorityTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<UnbondAuthorityFixture>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        // Revoke every fixture before awaiting one, and always restore Main even if cleanup fails.
        owned.forEach { it.releaseAndCancel() }
        try { runBlocking { owned.forEach { it.close() } } }
        finally { Dispatchers.resetMain() }
    }
    private suspend fun fixture(confirm: Boolean = false, parachain: Boolean = false,
        bonded: AssetAmountState<BigInteger> = AssetAmountState.Known(BigInteger.valueOf(1_000)),
        validationHeld: Boolean = false, builderHeld: Boolean = false): UnbondAuthorityFixture =
        UnbondAuthorityFixture(dispatcher, confirm, parachain, bonded, validationHeld, builderHeld)
            .also { owned += it; it.start() }

    @Test fun knownZeroAndPositiveRelayValuesReachTheBuilderWithoutSubstitutionWhenValidationAccepts() = runBlocking {
        for (confirm in listOf(false, true)) for (bonded in listOf(BigInteger.ZERO, BigInteger.valueOf(1_000))) {
            val f = fixture(confirm, bonded = AssetAmountState.Known(bonded))
            f.click(); f.finishActions()
            assertEquals(listOf("fixture-hash"), f.submitted)
            assertEquals(bonded, f.calls.single().bonded)
            assertEquals(BigInteger.valueOf(100), f.calls.single().amount)
            assertSame(f.initialState, f.calls.single().state)
        }
    }

    @Test fun unknownRelayBondedBalanceNeverReachesSubmissionAsZero() = runBlocking {
        for (confirm in listOf(false, true)) for (bonded in listOf(AssetAmountState.Missing, AssetAmountState.Unavailable("damaged"))) {
            val f = fixture(confirm, bonded = bonded)
            f.click(); f.finishActions()
            assertTrue("Unknown relay balance must not reach a builder", f.calls.isEmpty())
            assertTrue(f.submitted.isEmpty())
        }
    }

    @Test fun unknownRelayBondedBalanceNeverReachesFeeEstimationAsZero() = runBlocking {
        val f = fixture(bonded = AssetAmountState.Missing)
        dispatcher.scheduler.advanceTimeBy(501); dispatcher.scheduler.runCurrent()
        if (f.feeRequests.isNotEmpty()) {
            val result = runCatching { f.feeRequests.single()(f.initial.token) }
            assertTrue("An unavailable relay quote must fail explicitly", result.isFailure)
        }
        assertTrue(f.calls.isEmpty()); assertTrue(f.estimates.isEmpty())
    }

    @Test fun validFeeUsesTheSameAssetPrecisionAndCapturedBondedValue() = runBlocking {
        val f = fixture()
        dispatcher.scheduler.advanceTimeBy(501); dispatcher.scheduler.runCurrent()
        assertEquals(BigInteger.ONE, f.feeRequests.single()(f.initial.token))
        assertEquals(BigInteger.valueOf(100), f.calls.single().amount)
        assertEquals(BigInteger.valueOf(1_000), f.calls.single().bonded)
    }

    @Test fun feeTokenFromAnotherChainAssetOrMetadataRevisionCannotConstructAnExtrinsic() = runBlocking {
        for ((chain, id, precision) in listOf(Triple("other", "native", 2), Triple("fixture", "other", 2), Triple("fixture", "native", 18))) {
            val f = fixture()
            dispatcher.scheduler.advanceTimeBy(501); dispatcher.scheduler.runCurrent()
            val token = Token(f.configuration(chain, id, precision), null, null, null)
            assertTrue(runCatching { f.feeRequests.single()(token) }.isFailure)
            assertTrue(f.calls.isEmpty()); assertTrue(f.estimates.isEmpty())
        }
    }

    @Test fun changedAmountOrInvalidTextDuringValidationCannotOpenAnOldConfirmation() = runBlocking {
        for (next in listOf("2", "invalid")) {
            val f = fixture(validationHeld = true)
            f.click(submit = false); f.await { f.validationEntered.isCompleted }
            (f.vm as SelectUnbondViewModel).enteredAmountFlow.value = next
            dispatcher.scheduler.runCurrent()
            f.validationRelease.complete(Unit); f.finishActions()
            assertTrue(f.routed.isEmpty()); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test fun balanceInvalidationDuringValidationCannotAuthorizeEitherUnbondScreen() = runBlocking {
        for (confirm in listOf(false, true)) {
            val f = fixture(confirm, validationHeld = true)
            f.click(); f.await { f.validationEntered.isCompleted }
            f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Missing)
            dispatcher.scheduler.runCurrent()
            f.validationRelease.complete(Unit); f.finishActions()
            assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test fun changedStakingAccountDuringValidationCannotAuthorizeEitherUnbondScreen() = runBlocking {
        for (confirm in listOf(false, true)) {
            val f = fixture(confirm, validationHeld = true)
            f.click(); f.await { f.validationEntered.isCompleted }
            f.states.value = StakingState.Stash.None(f.chain, ByteArray(32) { 3 }, ByteArray(32) { 3 }, ByteArray(32) { 3 })
            dispatcher.scheduler.runCurrent()
            f.validationRelease.complete(Unit); f.finishActions()
            assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test fun walletChangeAndReturnDuringValidationCannotReviveThePriorIntent() = runBlocking {
        for (confirm in listOf(false, true)) {
            val f = fixture(confirm, validationHeld = true)
            f.click(); f.await { f.validationEntered.isCompleted }
            repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
            f.validationRelease.complete(Unit); f.finishActions()
            assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test fun invalidatedBalanceCannotRunAHeldExtrinsicBuilder() = runBlocking {
        for (confirm in listOf(false, true)) {
            val f = fixture(confirm, builderHeld = true)
            f.click(); f.await { f.builderEntered.isCompleted }
            f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Unavailable("revoked"))
            dispatcher.scheduler.runCurrent()
            f.builderRelease.complete(Unit); f.finishActions()
            assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test fun sameAccountAssetRevisionAndReturnCannotReviveAHeldBuilder() = runBlocking {
        for (confirm in listOf(false, true)) {
            val f = fixture(confirm, builderHeld = true)
            f.click(); f.await { f.builderEntered.isCompleted }
            f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Known(BigInteger.valueOf(900)))
            dispatcher.scheduler.runCurrent()
            f.assets.value = f.initial
            dispatcher.scheduler.runCurrent()
            f.builderRelease.complete(Unit); f.finishActions()
            assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test fun parachainDelegationDoesNotInventARelayBondedRequirementAndKeepsItsCollator() = runBlocking {
        for (confirm in listOf(false, true)) {
            val f = fixture(confirm, parachain = true, bonded = AssetAmountState.Missing)
            f.click(); f.finishActions()
            assertEquals(listOf("fixture-hash"), f.submitted)
            assertEquals(f.collator, f.calls.single().candidate)
            assertSame(f.initialState, f.calls.single().state)
        }
    }
}
