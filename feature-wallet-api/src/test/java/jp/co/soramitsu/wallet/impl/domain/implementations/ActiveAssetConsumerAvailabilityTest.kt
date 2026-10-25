package jp.co.soramitsu.wallet.impl.domain.implementations

import android.content.SharedPreferences
import android.util.Log
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfigurator
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfiguratorProvider
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

class ActiveAssetConsumerAvailabilityTest {
    private val key = "PUBLIC_ASSET_SELECTION"

    private fun row(id: String, ecosystem: String = "Substrate", assetName: String = "Public asset", assetId: String = "0") =
        JoinedChainInfo(
            ChainLocal(
                id,
                null,
                null,
                1,
                id,
                null,
                "public-icon",
                null,
                42,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                ecosystem,
                null,
                null,
                null,
                null
            ),
            listOf(ChainNodeLocal(id, "wss://public.invalid/$id", "Public endpoint", true, false)),
            listOf(
                ChainAssetLocal(
                    assetId,
                    assetName,
                    "PUB",
                    id,
                    "public-icon",
                    null,
                    "RELAYCHAIN",
                    12,
                    null,
                    true,
                    null,
                    null,
                    "0",
                    null,
                    true,
                    null,
                    null
                )
            ),
            emptyList()
        )

    private inner class Fixture(initial: List<JoinedChainInfo>, saved: String? = null) : AutoCloseable {
        private val androidLog = mockStatic(Log::class.java)
        val values = mutableMapOf("unrelated-preference" to "public-retained-value")
        val writes = mutableListOf<Pair<String, String?>>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        private val changes = mutableMapOf<String, String?>()
        private val storage = mock(SharedPreferences::class.java)
        private val editor = mock(SharedPreferences.Editor::class.java)
        val preferences = PreferencesImpl(storage)
        val rows = MutableSharedFlow<List<JoinedChainInfo>>(replay = 1).also { it.tryEmit(initial) }
        val registry: ChainRegistry
        val state: SingleAssetSharedState

        init {
            if (saved != null) values[key] = saved
            whenever(storage.contains(any())).thenAnswer { values.containsKey(it.getArgument<String>(0)) }
            whenever(storage.getString(any(), anyOrNull())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<String?>(1) }
            whenever(storage.edit()).thenReturn(editor)
            whenever(editor.putString(any(), anyOrNull())).thenAnswer {
                changes[it.getArgument(0)] = it.getArgument(1)
                editor
            }
            doAnswer {
                val snapshot = changes.toMap()
                changes.clear()
                snapshot.forEach { (field, value) ->
                    writes += field to value
                    if (value == null) values.remove(field) else values[field] = value
                    listeners.toList().forEach { it.onSharedPreferenceChanged(storage, field) }
                }
                null
            }.whenever(editor).apply()
            doAnswer {
                listeners += it.getArgument<SharedPreferences.OnSharedPreferenceChangeListener>(0)
                null
            }
                .whenever(storage).registerOnSharedPreferenceChangeListener(any())
            doAnswer {
                listeners -= it.getArgument<SharedPreferences.OnSharedPreferenceChangeListener>(0)
                null
            }
                .whenever(storage).unregisterOnSharedPreferenceChangeListener(any())

            val dao = mock(ChainDao::class.java)
            whenever(dao.endpointMutationLane).thenReturn(EndpointMutationLane())
            whenever(dao.joinChainInfoFlow()).thenReturn(rows)
            val assets = mock(AssetReadOnlyCache::class.java)
            whenever(assets.observeChainIdsNeedingSync()).thenReturn(flowOf(emptySet()))
            val configurators = mock(ChainEnvironmentConfiguratorProvider::class.java)
            whenever(configurators.provide(any())).thenReturn(mock(ChainEnvironmentConfigurator::class.java))
            registry = ChainRegistry(
                mock(RuntimeProviderPool::class.java), mock(ConnectionPool::class.java),
                mock(RuntimeSubscriptionPool::class.java), dao, mock(ChainSyncService::class.java),
                mock(RuntimeSyncService::class.java), mock(NetworkStateService::class.java),
                mock(EthereumConnectionPool::class.java), assets, ChainsRepository(dao), configurators,
                mock(PortfolioVisibility::class.java), Dispatchers.Unconfined
            )
            state = object : SingleAssetSharedState(key, registry, { _, _ -> true }, preferences) {}
            registry.syncUp()
        }

        override fun close() {
            try {
                runBlocking { registry.scope.coroutineContext.job.cancelAndJoin() }
            } finally {
                androidLog.close()
            }
        }
    }

    @Test
    fun healthySelectionKeepsItsAssetObserver() = verifyLifetime({}, 1, 1)

    @Test
    fun changedMetadataReplacesTheAssetObserver() = verifyLifetime({ fixture ->
        fixture.rows.emit(listOf(row("a", assetName = "Refreshed public asset"), row("healthy")))
    }, 2, 1)

    @Test
    fun removingSelectedChainCancelsItsAssetObserver() = verifyLifetime({ fixture ->
        fixture.rows.emit(listOf(row("healthy")))
    }, 1, 0)

    @Test
    fun unavailableExplicitSelectionCancelsPriorAssetObserver() = verifyLifetime({ fixture ->
        fixture.state.update("missing", "0")
    }, 1, 0)

    @Test
    fun identicalRestoredSelectionStartsAFreshAssetObserver() = verifyLifetime({ fixture ->
        fixture.rows.emit(listOf(row("healthy")))
        delay(250)
        fixture.rows.emit(listOf(row("a"), row("healthy")))
    }, 2, 1)

    @Test
    fun changedChainWithoutAccountCancelsPriorAssetObserver() = verifyLifetime({ fixture ->
        fixture.rows.emit(listOf(row("a", ecosystem = "Ethereum"), row("healthy")))
    }, 1, 0)

    @Test
    fun selectedWalletChangeReplacesItsAssetObserver() = verifyLifetime(
        {}, 2, 1,
        accountChange = { it.value = makeAccount(43L, true) },
        expectedMetaIds = listOf(42L, 43L)
    )

    @Test
    fun selectedWalletWithoutChainAccountCancelsItsAssetObserver() = verifyLifetime(
        {}, 1, 0,
        accountChange = { it.value = makeAccount(43L, false) }
    )

    @Test
    fun restoredWalletStartsFreshObserverWithoutSelectionChange() = verifyLifetime(
        {}, 2, 1,
        accountChange = {
            val initial = it.value
            it.value = makeAccount(43L, false)
            delay(250)
            it.value = initial
        }
    )

    @Test
    fun sameWalletTonKeyChangeReplacesItsAssetObserver() = verifyTonLifetime(listOf(8), listOf(7, 8), 1)

    @Test
    fun sameWalletTonKeyRemovalCancelsItsAssetObserver() = verifyTonLifetime(listOf(null), listOf(7), 0)

    @Test
    fun sameWalletTonKeyRestorationStartsAFreshAssetObserver() = verifyTonLifetime(listOf(null, 7), listOf(7, 7), 1)

    private fun verifyTonLifetime(changes: List<Int?>, expectedKeys: List<Int>, expectedActive: Int) = runBlocking {
        Fixture(listOf(row("a", ecosystem = "Ton")), "a:0").use { fixture ->
            fun account(key: Int?) = MetaAccount(
                id = 42L, chainAccounts = emptyMap(), favoriteChains = emptyMap(),
                substratePublicKey = null, substrateCryptoType = null, substrateAccountId = null,
                ethereumAddress = null, ethereumPublicKey = null,
                tonPublicKey = key?.let { ByteArray(32) { key.toByte() } },
                isSelected = true, isBackedUp = false, googleBackupAddress = null,
                name = "Public TON wallet", initialized = true
            )
            val initial = account(7)
            val accountsFlow = MutableSharedFlow<MetaAccount>(replay = 1).also { it.tryEmit(initial) }
            val accounts = mock(AccountRepository::class.java)
            whenever(accounts.selectedMetaAccountFlow()).thenReturn(accountsFlow)
            whenever(accounts.getSelectedMetaAccount()).thenAnswer { accountsFlow.replayCache.last() }
            var active = 0
            val observedKeys = mutableListOf<List<Byte>>()
            val wallets = mock(WalletRepository::class.java)
            whenever(wallets.assetFlow(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
                assertEquals(42L, invocation.getArgument<Long>(0))
                val accountId = invocation.getArgument<ByteArray>(1).toList()
                flow {
                    observedKeys += accountId
                    active++
                    try {
                        emit(mock(Asset::class.java))
                        awaitCancellation()
                    } finally {
                        active--
                    }
                }
            }
            val job = launch { AssetUseCaseImpl(wallets, accounts, fixture.state).currentAssetFlow().collect {} }
            try {
                withTimeout(1500) { while (observedKeys.isEmpty()) delay(10) }
                assertEquals(1, active)
                for (key in changes) {
                    val next = account(key)
                    // The current domain equality intentionally omits TON, so the consumer must not use it to drop this event.
                    assertEquals(initial, next)
                    accountsFlow.emit(next)
                    delay(250)
                }
                assertEquals("active TON observer after key transition", expectedActive, active)
                assertEquals(expectedKeys.map { key -> List(32) { key.toByte() } }, observedKeys)
                assertTrue(fixture.writes.isEmpty())
                assertEquals("public-retained-value", fixture.values["unrelated-preference"])
            } finally {
                job.cancelAndJoin()
                assertEquals(0, active)
            }
        }
    }

    private fun makeAccount(id: Long, eligible: Boolean): MetaAccount {
        val account = mock(MetaAccount::class.java)
        whenever(account.id).thenReturn(id)
        whenever(account.substrateAccountId).thenReturn(if (eligible) ByteArray(32) { id.toByte() } else null)
        whenever(account.chainAccounts).thenReturn(emptyMap())
        return account
    }

    private fun verifyLifetime(
        change: suspend (Fixture) -> Unit,
        expectedStarts: Int,
        expectedActive: Int,
        accountChange: suspend (MutableStateFlow<MetaAccount>) -> Unit = {},
        expectedMetaIds: List<Long> = List(expectedStarts) { 42L }
    ) = runBlocking {
        Fixture(listOf(row("a"), row("healthy")), "a:0").use { fixture ->
            var starts = 0
            var active = 0
            val observedMetaIds = mutableListOf<Long>()
            val tokens = mock(WalletRepository::class.java)
            whenever(tokens.assetFlow(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
                val metaId = invocation.getArgument<Long>(0)
                flow {
                    starts += 1
                    observedMetaIds += metaId
                    active += 1
                    try {
                        emit(mock(Asset::class.java))
                        awaitCancellation()
                    } finally {
                        active -= 1
                    }
                }
            }
            val accounts = mock(AccountRepository::class.java)
            val account = makeAccount(42L, true)
            val accountFlow = MutableStateFlow(account)
            whenever(accounts.getSelectedMetaAccount()).thenAnswer { accountFlow.value }
            whenever(accounts.selectedMetaAccountFlow()).thenReturn(accountFlow)
            val useCase = AssetUseCaseImpl(tokens, accounts, fixture.state)
            val observed = mutableListOf<Asset>()
            val job = launch { useCase.currentAssetFlow().collect { observed += it } }
            try {
                withTimeout(1500) { while (starts == 0) delay(10) }
                assertEquals(1, starts)
                assertEquals(1, active)
                change(fixture)
                accountChange(accountFlow)
                delay(500)
                assertEquals("active asset observer after selection transition", expectedActive, active)
                assertEquals("total asset observer subscriptions", expectedStarts, starts)
                assertEquals(expectedStarts, observed.size)
                assertEquals("wallet IDs used by active subscriptions", expectedMetaIds, observedMetaIds)
                assertEquals("public-retained-value", fixture.values["unrelated-preference"])
                assertEquals(if (fixture.values[key] == "missing:0") listOf(key to "missing:0") else emptyList(), fixture.writes)
            } finally {
                job.cancelAndJoin()
                assertEquals(0, active)
            }
        }
    }
}
