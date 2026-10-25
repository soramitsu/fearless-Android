package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Runs the actual stakeLess branch and builder extensions with a known delegation quote, no RPC. */
class ParachainUnbondBuilderAuthorityTest {
    private class Fixture(val holdAccountRead: Boolean = false) {
        val originalAccountId = ByteArray(32) { 1 }
        val collatorId = ByteArray(32) { 2 }
        val collator = "0x" + "02".repeat(32)
        fun chain(id: String) = mock<Chain> {
            on { this.id } doReturn id
            on { ecosystem } doReturn Ecosystem.Substrate
            on { addressPrefix } doReturn 42
            on { externalApi } doReturn null
        }
        fun wallet(accountId: ByteArray) = mock<MetaAccount> {
            on { id } doReturn 7L
            on { substrateAccountId } doReturn accountId
            on { chainAccounts } doReturn emptyMap()
        }
        val originalChain = chain("original")
        val chains = MutableStateFlow(originalChain)
        var selectedWallet = wallet(originalAccountId)
        val config = mock<ChainAsset> {
            on { chainId } doReturn "original"
            on { id } doReturn "native"
            on { precision } doReturn 2
        }
        val asset = Asset.createEmpty(config, 7, originalAccountId, minSupportedVersion = null)
        val assets = MutableStateFlow(asset)
        val selection = StakingSelectionRevision()
        val interactor = mock<StakingInteractor> {
            on { selectionRevision } doReturn selection
            on { selectedChainFlow() } doReturn chains
            on { currentAssetFlow() } doReturn assets
        }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>().apply { if (!holdAccountRead) complete(Unit) }
        val accounts = mock<AccountRepository> {
            onBlocking { getSelectedMetaAccount() } doSuspendableAnswer {
                entered.complete(Unit); release.await(); selectedWallet
            }
        }
        val state = StakingState.Parachain.None(originalChain, originalAccountId)
        val calls = CopyOnWriteArrayList<Pair<String, Map<String, Any?>>>()
        val builder = mock<ExtrinsicBuilder> {
            on { call(any<String>(), any<String>(), any()) } doAnswer {
                assertEquals("ParachainStaking", it.getArgument<String>(0))
                calls += it.getArgument<String>(1) to it.getArgument<Map<String, Any?>>(2)
                it.mock as ExtrinsicBuilder
            }
        }
        val scenario = spy(StakingParachainScenarioInteractor(interactor, accounts, mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock()))
        init {
            // Isolate the builder's own selected-wallet/chain reads from the separately scoped quote implementation.
            runBlocking { doReturn(BigDecimal.TEN).whenever(scenario).getUnstakeAvailableAmount(any(), anyOrNull()) }
        }
        suspend fun build() = scenario.stakeLess(builder, BigInteger.valueOf(100), state, BigInteger.ZERO, collator)
    }

    @Test fun knownParachainDelegationBuildsWithoutUsingARelayBondedBalance() = runBlocking {
        val f = Fixture()
        f.build()
        assertEquals("schedule_delegator_bond_less", f.calls.single().first)
        assertEquals(BigInteger.valueOf(100), f.calls.single().second["less"])
        assertArrayEquals(f.collatorId, f.calls.single().second["candidate"] as ByteArray)
    }

    @Test fun changedSelectedChainCannotBeMixedIntoACapturedParachainState() = runBlocking {
        val f = Fixture()
        f.chains.value = f.chain("replacement")
        val failure = runCatching { f.build() }.exceptionOrNull()
        assertTrue("A different global chain cannot authorize this captured builder", f.calls.isEmpty())
        assertNotNull(failure)
    }

    @Test fun walletChangeDuringLookupCannotTurnADelegatorIntentIntoACollatorCall() = runBlocking {
        val f = Fixture(holdAccountRead = true)
        val build = async(Dispatchers.Default) { runCatching { f.build() } }
        try {
            withTimeout(5_000) { f.entered.await() }
            f.selectedWallet = f.wallet(f.collatorId)
            f.release.complete(Unit)
            val result = withTimeout(5_000) { build.await() }
            assertTrue("Changed selected wallet must revoke the captured delegator operation", f.calls.isEmpty())
            assertTrue(result.isFailure)
        } finally { f.release.complete(Unit); build.cancelAndJoin() }
    }
}
