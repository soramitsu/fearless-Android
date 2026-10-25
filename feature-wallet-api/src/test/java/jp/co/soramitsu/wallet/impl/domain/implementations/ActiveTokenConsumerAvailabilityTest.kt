package jp.co.soramitsu.wallet.impl.domain.implementations

import android.content.SharedPreferences
import android.util.Log
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
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

class ActiveTokenConsumerAvailabilityTest {
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
    fun healthySelectionKeepsItsTokenObserver() = verifyLifetime({}, 1, 1)

    @Test
    fun changedMetadataReplacesTheTokenObserver() = verifyLifetime({ fixture ->
        fixture.rows.emit(listOf(row("a", assetName = "Refreshed public asset"), row("healthy")))
    }, 2, 1)

    @Test
    fun removingSelectedChainCancelsItsTokenObserver() = verifyLifetime({ fixture ->
        fixture.rows.emit(listOf(row("healthy")))
    }, 1, 0)

    @Test
    fun unavailableExplicitSelectionCancelsPriorTokenObserver() = verifyLifetime({ fixture ->
        fixture.state.update("missing", "0")
    }, 1, 0)

    @Test
    fun identicalRestoredSelectionStartsAFreshTokenObserver() = verifyLifetime({ fixture ->
        fixture.rows.emit(listOf(row("healthy")))
        delay(250)
        fixture.rows.emit(listOf(row("a"), row("healthy")))
    }, 2, 1)

    private fun verifyLifetime(
        change: suspend (Fixture) -> Unit,
        expectedStarts: Int,
        expectedActive: Int
    ) = runBlocking {
        Fixture(listOf(row("a"), row("healthy")), "a:0").use { fixture ->
            var starts = 0
            var active = 0
            val tokens = mock(TokenRepository::class.java)
            whenever(tokens.observeToken(any())).thenReturn(flow {
                starts += 1
                active += 1
                try {
                    emit(mock(Token::class.java))
                    awaitCancellation()
                } finally {
                    active -= 1
                }
            })
            val useCase = TokenUseCaseImpl(tokens, fixture.state)
            val observed = mutableListOf<Token>()
            val job = launch { useCase.currentTokenFlow().collect { observed += it } }
            try {
                withTimeout(1500) { while (starts == 0) delay(10) }
                assertEquals(1, starts)
                assertEquals(1, active)
                change(fixture)
                delay(500)
                assertEquals("active token observer after selection transition", expectedActive, active)
                assertEquals("total token observer subscriptions", expectedStarts, starts)
                assertEquals(expectedStarts, observed.size)
                assertEquals("public-retained-value", fixture.values["unrelated-preference"])
                assertEquals(if (fixture.values[key] == "missing:0") listOf(key to "missing:0") else emptyList(), fixture.writes)
            } finally {
                job.cancelAndJoin()
                assertEquals(0, active)
            }
        }
    }
}
