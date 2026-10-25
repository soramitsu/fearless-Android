package jp.co.soramitsu.staking.impl.scenarios.parachain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.*
import jp.co.soramitsu.staking.api.domain.model.*
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual available-unbond quote with local flow/repository edges; no RPC or builder spy. */
class ParachainUnbondQuoteContextTest {
    private class ReadCancelled(val marker: Any) : CancellationException("quote cancelled")
    private class Harness {
        val account = ByteArray(32) { 1 }
        val collator = ByteArray(32) { 2 }
        fun chain(id: String) = mock<Chain> {
            on { this.id } doReturn id
            on { ecosystem } doReturn Ecosystem.Substrate
            on { addressPrefix } doReturn 42
            on { externalApi } doReturn null
        }
        fun wallet(id: Long = 7L, accountId: ByteArray = account) = mock<MetaAccount> {
            on { this.id } doReturn id
            on { substrateAccountId } doReturn accountId
            on { chainAccounts } doReturn emptyMap()
        }
        val originalChain = chain("original")
        val chains = MutableStateFlow(originalChain)
        var selectedWallet = wallet()
        val config = mock<ChainAsset> {
            on { chainId } doReturn "original"
            on { id } doReturn "native"
            on { precision } doReturn 2
            on { staking } doReturn ChainAsset.StakingType.PARACHAIN
        }
        val asset = Asset.createEmpty(config, 7L, account, minSupportedVersion = null)
        fun state(accountId: ByteArray = account) = StakingState.Parachain.Delegator(originalChain, accountId,
            listOf(CollatorDelegation(collator, BigInteger.valueOf(1_000), BigInteger.ZERO, DelegatorStateStatus.ACTIVE)), BigDecimal.TEN)
        var states: Flow<StakingState> = flowOf(state())
        var requests: Flow<List<DelegationScheduledRequest>> = flowOf(emptyList())
        val interactor = mock<StakingInteractor> {
            on { selectedChainFlow() } doReturn chains
            on { currentAssetFlow() } doReturn MutableStateFlow(asset)
        }
        val accounts = mock<AccountRepository> {
            onBlocking { getSelectedMetaAccount() } doAnswer { selectedWallet }
        }
        val repository = mock<StakingParachainScenarioRepository> {
            on { stakingStateFlow(any(), any()) } doAnswer { states }
            on { getDelegationScheduledRequestsFlow(any(), any()) } doAnswer { requests }
            onBlocking { getCurrentRound(any()) } doReturn Round(BigInteger.valueOf(5), BigInteger.ZERO, BigInteger.ONE)
        }
        val revision = StakingSelectionRevision()
        val selection = StakingAssetSelection.ParachainStaking("original", "native")
        val shared = mock<StakingSharedState> { on { selectionRevision } doReturn revision }
        val scenario = StakingParachainScenarioInteractor(interactor, accounts, mock(), repository, mock(), shared, mock(), mock(), mock(), mock())
        init { revision.changed(selection) }
        suspend fun quote() = withTimeout(5_000) { scenario.getUnstakeAvailableAmount(asset, collator) }
        suspend fun assertRejected() { assertRefused(runCatching { quote() }.exceptionOrNull()) }
    }

    private companion object {
        fun assertRefused(error: Throwable?) {
            assertNotNull(error)
            assertFalse("A timeout is not an explicit unavailable quote", error is TimeoutCancellationException)
            assertTrue(error is IllegalStateException || error is IllegalArgumentException || error is CancellationException)
        }
    }

    @Test fun knownQuoteSubtractsAllOwnScheduledRequestsAndRetainsKnownZero() = runBlocking<Unit> {
        for (total in listOf(300L, 1_000L)) {
            val h = Harness()
            h.requests = flowOf(listOf(
                DelegationScheduledRequest(h.account, BigInteger.valueOf(4), DelegationAction.UNSTAKE, BigInteger.valueOf(100)),
                DelegationScheduledRequest(h.account, BigInteger.valueOf(6), DelegationAction.UNSTAKE, BigInteger.valueOf(total - 100)),
                DelegationScheduledRequest(ByteArray(32) { 3 }, BigInteger.ONE, DelegationAction.UNSTAKE, BigInteger.valueOf(999))))
            assertEquals(0, BigDecimal.valueOf(1_000 - total, 2).compareTo(h.quote()))
        }
    }

    @Test fun anotherSelectedChainCannotPriceACapturedAsset() = runBlocking<Unit> {
        val h = Harness(); h.chains.value = h.chain("replacement")
        h.assertRejected()
    }

    @Test fun anotherWalletWithTheSameAccountCannotSupplyTheCapturedQuote() = runBlocking<Unit> {
        val h = Harness(); h.selectedWallet = h.wallet(id = 8L)
        h.assertRejected()
    }

    @Test fun anotherSelectedAccountCannotSupplyTheCapturedQuote() = runBlocking<Unit> {
        val h = Harness(); h.selectedWallet = h.wallet(accountId = ByteArray(32) { 3 })
        h.assertRejected()
    }

    @Test fun foreignDelegatorStateCannotBeAcceptedForTheCapturedAccount() = runBlocking<Unit> {
        val h = Harness(); h.states = flowOf(h.state(ByteArray(32) { 3 }))
        h.assertRejected()
    }

    @Test fun walletAndStakingSelectionABAChangesRevokeDelayedQuotes() = runBlocking<Unit> {
        for (wallet in listOf(false, true)) {
            val h = Harness(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            h.states = flow { entered.complete(Unit); release.await(); emit(h.state()) }
            val pending = async(Dispatchers.Default) { runCatching { h.quote() } }
            try {
                withTimeout(5_000) { entered.await() }
                if (wallet) repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
                else { h.revision.changed(StakingAssetSelection.ParachainStaking("other", "native")); h.revision.changed(h.selection) }
                release.complete(Unit)
                assertRefused(withTimeout(5_000) { pending.await() }.exceptionOrNull())
            } finally { release.complete(Unit); pending.cancelAndJoin() }
        }
    }

    @Test fun inconsistentScheduledAmountsCannotCreateANegativeAvailableQuote() = runBlocking<Unit> {
        val h = Harness()
        h.requests = flowOf(listOf(DelegationScheduledRequest(h.account, BigInteger.ONE,
            DelegationAction.UNSTAKE, BigInteger.valueOf(1_001))))
        h.assertRejected()
    }

    @Test fun requiredStateReadCancellationRetainsItsExactReason() = runBlocking<Unit> {
        val h = Harness(); val reason = ReadCancelled(Any())
        h.states = flow { throw reason }
        assertSame(reason, runCatching { h.quote() }.exceptionOrNull())
    }

    @Test fun nonDelegatorStateIsExplicitlyUnavailableInsteadOfWaitingForever() = runBlocking<Unit> {
        val h = Harness(); h.states = flowOf(StakingState.Parachain.None(h.originalChain, h.account))
        h.assertRejected()
    }

    @Test fun foreignStateChainCannotSupplyACapturedDelegation() = runBlocking<Unit> {
        val h = Harness(); val other = h.chain("replacement")
        h.states = flowOf(StakingState.Parachain.Delegator(other, h.account,
            h.state().delegations, BigDecimal.TEN))
        h.assertRejected()
    }

    @Test fun negativeOwnScheduledRequestCannotIncreaseTheQuote() = runBlocking<Unit> {
        val h = Harness(); h.requests = flowOf(listOf(DelegationScheduledRequest(h.account,
            BigInteger.ONE, DelegationAction.UNSTAKE, BigInteger.valueOf(-1))))
        h.assertRejected()
    }

    @Test fun negativeDelegationAmountIsUnavailable() = runBlocking<Unit> {
        val h = Harness(); h.states = flowOf(StakingState.Parachain.Delegator(h.originalChain, h.account,
            listOf(CollatorDelegation(h.collator, BigInteger.valueOf(-1), BigInteger.ZERO, DelegatorStateStatus.ACTIVE)),
            BigDecimal.valueOf(-1)))
        h.assertRejected()
    }

    @Test fun quoteDoesNotNeedARoundLookupToSubtractAllScheduledRequests() = runBlocking<Unit> {
        val h = Harness()
        assertEquals(0, BigDecimal.TEN.compareTo(h.quote()))
        verify(h.repository, never()).getCurrentRound(any())
    }

    @Test fun requestReadCancellationRetainsItsExactReason() = runBlocking<Unit> {
        val h = Harness(); val reason = ReadCancelled(Any())
        h.requests = flow { throw reason }
        assertSame(reason, runCatching { h.quote() }.exceptionOrNull())
    }

    @Test fun walletChangeWhileReadingRequestsCannotPublishAnOldQuote() = runBlocking<Unit> {
        val h = Harness(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        h.requests = flow { entered.complete(Unit); release.await(); emit(emptyList()) }
        val pending = async(Dispatchers.Default) { runCatching { h.quote() } }
        try {
            withTimeout(5_000) { entered.await() }
            WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
            release.complete(Unit)
            assertRefused(withTimeout(5_000) { pending.await() }.exceptionOrNull())
        } finally { release.complete(Unit); pending.cancelAndJoin() }
    }

    @Test fun invalidPrecisionCannotConvertADelegationQuote() = runBlocking<Unit> {
        for (precision in listOf(-1, 256, Int.MAX_VALUE)) {
            val h = Harness(); whenever(h.config.precision).thenReturn(precision)
            h.assertRejected()
        }
        for (precision in listOf(0, 255)) {
            val h = Harness(); whenever(h.config.precision).thenReturn(precision)
            assertEquals(0, BigDecimal(BigInteger.valueOf(1_000), precision).compareTo(h.quote()))
        }
    }

    @Test fun malformedCandidateCannotBePricedAsAZeroDelegation() = runBlocking<Unit> {
        for (size in listOf(0, 19, 21, 31, 33)) {
            val h = Harness()
            assertRefused(runCatching { withTimeout(5_000) {
                h.scenario.getUnstakeAvailableAmount(h.asset, ByteArray(size))
            } }.exceptionOrNull())
        }
    }

    @Test fun cancelledCallerCannotReturnThroughTheNoSelectionFastPath() = runBlocking<Unit> {
        val h = Harness(); val reason = ReadCancelled(Any()); val outcome = CompletableDeferred<Throwable?>()
        val caller = launch {
            currentCoroutineContext().job.cancel(reason)
            outcome.complete(runCatching { h.scenario.getUnstakeAvailableAmount(h.asset, null) }.exceptionOrNull())
        }
        try {
            assertSame(reason, withTimeout(5_000) { outcome.await() })
            verifyNoInteractions(h.repository, h.accounts, h.interactor)
        } finally { caller.cancelAndJoin() }
    }

    @Test fun newerDelegationStateIsUsedWhileTheRequestReadWaits() = runBlocking<Unit> {
        val h = Harness(); val requestsEntered = CompletableDeferred<Unit>(); val newerPublished = CompletableDeferred<Unit>()
        h.states = flow {
            emit(h.state()); requestsEntered.await()
            emit(StakingState.Parachain.Delegator(h.originalChain, h.account,
                listOf(CollatorDelegation(h.collator, BigInteger.valueOf(900), BigInteger.ZERO, DelegatorStateStatus.ACTIVE)),
                BigDecimal("9")))
            newerPublished.complete(Unit); awaitCancellation()
        }
        h.requests = flow { requestsEntered.complete(Unit); newerPublished.await(); emit(emptyList()) }
        assertEquals(0, BigDecimal("9").compareTo(h.quote()))
    }

    @Test fun sourceCancellationDoesNotWaitForAnUnemittedPeer() = runBlocking<Unit> {
        for (stateFails in listOf(false, true)) {
            val h = Harness(); val reason = ReadCancelled(Any())
            h.states = if (stateFails) flow { throw reason } else flow { awaitCancellation() }
            h.requests = if (stateFails) flow { awaitCancellation() } else flow { throw reason }
            assertSame(reason, runCatching { h.quote() }.exceptionOrNull())
        }
    }

    @Test fun sourceJobSelfCancellationDoesNotWaitForAnUnemittedPeer() = runBlocking<Unit> {
        for (stateFails in listOf(false, true)) {
            val h = Harness(); val reason = ReadCancelled(Any())
            h.states = if (stateFails) flow {
                currentCoroutineContext().job.cancel(reason); awaitCancellation()
            } else flow { awaitCancellation() }
            h.requests = if (stateFails) flow { awaitCancellation() } else flow {
                currentCoroutineContext().job.cancel(reason); awaitCancellation()
            }
            assertSame(reason, runCatching { h.quote() }.exceptionOrNull())
        }
    }
}
