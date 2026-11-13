package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class ProducerSelectionContextReviewTest {
    @get:Rule val environment: TestRule = SearchContractEnvironment()

    @Test fun sameChainWalletSwitchCancelsSuspendedSelectionWithoutChangingSetup() = runTest {
        val f = ProducerSuspendedSelectionFixture()
        f.start()
        val before = f.data.process.setupStakingProcess.value
        val pending = async { runCatching { f.choose() } }
        try {
            f.await(f.started)
            val replacement = mock<MetaAccount> {
                on { id } doReturn 99L
                on { substrateAccountId } doReturn ByteArray(32) { 8 }
                on { chainAccounts } doReturn emptyMap()
            }
            whenever(f.data.accounts.getSelectedMetaAccount()).thenReturn(replacement)
            f.complete()
            assertTrue(f.await(pending).exceptionOrNull() is CancellationException)
            assertSame(before, f.data.process.setupStakingProcess.value)
        } finally {
            pending.cancelAndJoin()
            f.close()
        }
    }

    @Test fun compareAndSetPreservesInterveningSelectionOnTheSameChainAndWallet() = runTest {
        val f = ProducerSuspendedSelectionFixture()
        f.start()
        val pending = async { runCatching { f.choose() } }
        try {
            f.await(f.started)
            f.data.readyRelay(listOf(f.data.validators[1]))
            val newer = f.data.process.setupStakingProcess.value
            f.complete()
            val failure = f.await(pending).exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertEquals("Staking setup changed", failure?.message)
            assertSame(newer, f.data.process.setupStakingProcess.value)
            assertEquals(listOf(f.data.relay.addresses[1]), f.data.relayPayload().blockProducers.map { it.address })
        } finally {
            pending.cancelAndJoin()
            f.close()
        }
    }

    @Test fun changingAssetWithinSameChainCancelsSuspendedSelection() = runTest {
        val f = ProducerSuspendedSelectionFixture()
        f.start()
        val before = f.data.process.setupStakingProcess.value
        val pending = async { runCatching { f.choose() } }
        try {
            f.await(f.started)
            val replacement = mock<Asset> {
                on { id } doReturn "replacement-asset"
                on { staking } doReturn Asset.StakingType.RELAYCHAIN
            }
            f.data.selection.value = SingleAssetSharedState.AssetWithChain(f.data.chain, replacement)
            f.complete()
            assertTrue(f.await(pending).exceptionOrNull() is CancellationException)
            assertSame(before, f.data.process.setupStakingProcess.value)
        } finally {
            pending.cancelAndJoin()
            f.close()
        }
    }

    @Test fun staleBlockedResponseDoesNotReplaceANewerSelectionWithAnOldWarning() = runTest {
        val f = ProducerSuspendedSelectionFixture()
        f.start()
        val pending = async { runCatching { f.choose() } }
        try {
            f.await(f.started)
            f.data.readyRelay(listOf(f.data.validators[1]))
            val newer = f.data.process.setupStakingProcess.value
            f.complete(blocked = true)
            val failure = f.await(pending).exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertEquals("Staking setup changed", failure?.message)
            assertSame(newer, f.data.process.setupStakingProcess.value)
        } finally {
            pending.cancelAndJoin()
            f.close()
        }
    }

    @Test fun suspendedOldChainInfoCannotNavigateAfterChainReplacement() = runTest {
        val f = ProducerSuspendedSelectionFixture()
        f.start()
        val opened = mutableListOf<Validator>()
        val pending = async {
            runCatching {
                f.data.interactor.navigateBlockProducerInfo(
                    f.data.relay.addresses[2], f.data.lifecycle, { fail("Wrong route") }, opened::add
                )
            }
        }
        try {
            f.await(f.started)
            val other = ValidatorCatalogueFixture("new-chain-for-info")
            f.data.selection.value = SingleAssetSharedState.AssetWithChain(other.chain, other.asset)
            f.complete()
            assertTrue(f.await(pending).exceptionOrNull() is CancellationException)
            assertTrue(opened.isEmpty())
        } finally {
            pending.cancelAndJoin()
            f.close()
        }
    }

    @Test fun parachainSelectionStillAdvancesAnInitialSelectionStepWithExactBondContext() = runTest {
        val f = ProducerSearchFixture(true)
        f.defaults()
        f.process.setupStakingProcess.value = SetupStakingProcess.SelectBlockProducersStep.Collators(
            SetupStakingProcess.SelectBlockProducersStep.Payload.Parachain(BigDecimal("42.125"), "exact-delegator")
        )
        assertTrue(f.choose(f.para.hex[0]).isSuccess)
        val payload = f.paraPayload() as SetupStakingProcess.ReadyToSubmit.Payload.Parachain
        assertEquals(listOf(f.para.hex[0]), payload.blockProducers.map { it.address })
        assertEquals(BigDecimal("42.125"), payload.amount)
        assertEquals("exact-delegator", payload.currentAccountAddress)
        assertEquals(SetupStakingProcess.ReadyToSubmit.SelectionMethod.CUSTOM, payload.selectionMethod)
    }
}
