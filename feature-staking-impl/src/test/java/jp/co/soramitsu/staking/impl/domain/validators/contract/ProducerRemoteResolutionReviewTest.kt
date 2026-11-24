package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.validators.current.search.BlockedValidatorException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProducerRemoteResolutionReviewTest {
    @get:Rule val environment: TestRule = SearchContractEnvironment()

    @Test fun remoteInfoUsesFreshMatchingMetadataWithoutChangingSelection() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val before = f.process.setupStakingProcess.value
        val remote = f.relay.validator(2)
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenReturn(remote)
        val opened = mutableListOf<Validator>()
        f.interactor.navigateBlockProducerInfo(remote.address, f.lifecycle, { fail("Wrong route") }, opened::add)
        assertSame(remote, opened.single())
        assertSame(before, f.process.setupStakingProcess.value)
        verify(f.validatorProvider).getValidatorWithoutElectedInfo(f.chain, remote.address)
    }

    @Test fun blockedRemoteValidatorCannotChangeExistingTargets() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val before = f.process.setupStakingProcess.value
        val remote = f.relay.validator(2, blocked = true)
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenReturn(remote)
        assertSame(BlockedValidatorException, f.choose(remote.address).exceptionOrNull())
        assertSame(before, f.process.setupStakingProcess.value)
    }

    @Test fun matchingRemoteAddressWithoutValidatorPreferencesCannotBeSelected() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val before = f.process.setupStakingProcess.value
        val remote = Validator(f.relay.addresses[2], false, f.relay.hex[2], null, null, null)
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenReturn(remote)
        assertEquals("cannot find validator", runCatching { f.choose(remote.address) }.exceptionOrNull()?.message)
        assertSame(before, f.process.setupStakingProcess.value)
        verify(f.validatorProvider).getValidatorWithoutElectedInfo(f.chain, remote.address)
    }

    @Test fun remoteSelectionFailurePreservesItsCauseAndSetupPayload() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val before = f.process.setupStakingProcess.value
        val failure = IllegalStateException("Validator RPC offline")
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.choose(f.relay.addresses[2]) }.exceptionOrNull())
        assertSame(before, f.process.setupStakingProcess.value)
    }

    @Test fun remoteSelectionCancellationCannotMutateReadyState() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val before = f.process.setupStakingProcess.value
        val failure = CancellationException("Search screen closed")
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenThrow(failure)
        assertCatalogueFailure(failure, runCatching { f.choose(f.relay.addresses[2]) }.exceptionOrNull())
        assertSame(before, f.process.setupStakingProcess.value)
    }

    @Test fun invalidNonCatalogueAddressCannotStartFreshProviderLookup() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val before = f.process.setupStakingProcess.value
        assertEquals("cannot find validator", runCatching { f.choose("not-an-address") }.exceptionOrNull()?.message)
        verify(f.validatorProvider, never()).getValidatorWithoutElectedInfo(any(), any())
        assertSame(before, f.process.setupStakingProcess.value)
    }

    @Test fun refetchedRemoteAccountTogglesExistingTargetInsteadOfDuplicatingIt() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        f.readyRelay(listOf(f.relay.validator(2)))
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenReturn(f.relay.validator(2))
        assertTrue(f.choose(f.relay.addresses[2]).isSuccess)
        assertTrue(f.relayPayload().blockProducers.isEmpty())
    }

    @Test fun suspendedOldChainSelectionCannotModifyTheNewChainsSetup() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        val started = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Validator>()
        whenever(f.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).doSuspendableAnswer {
            started.complete(Unit)
            reply.await()
        }
        val pending = async { runCatching { f.choose(f.relay.addresses[2]) } }
        try {
            withContext(Dispatchers.Default) { withTimeout(3_000) { started.await() } }
            val other = ValidatorCatalogueFixture("new-chain-during-selection")
            f.selection.value = SingleAssetSharedState.AssetWithChain(other.chain, other.asset)
            f.readyRelay(listOf(other.validator(1)))
            val newSetup = f.process.setupStakingProcess.value
            reply.complete(f.relay.validator(2))
            val outcome = withContext(Dispatchers.Default) { withTimeout(3_000) { pending.await() } }
            assertTrue(outcome.exceptionOrNull() is CancellationException)
            assertEquals("Staking selection context changed", outcome.exceptionOrNull()?.message)
            assertSame(newSetup, f.process.setupStakingProcess.value)
        } finally {
            pending.cancelAndJoin()
        }
    }
}
