package jp.co.soramitsu.runtime.state

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
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfigurator
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfiguratorProvider
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

class SingleAssetAvailabilityTest {
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
    fun healthyInitialDefaultPublishesExactAssetAndPreservesUnrelatedPreference() = runBlocking {
        val local = row("a")
        Fixture(listOf(local)).use { fixture ->
            val result = withTimeout(1500) { fixture.state.assetWithChain.first() }
            val expected = mapChainLocalToChain(local)
            assertEquals(SingleAssetSharedState.AssetWithChain(expected, expected.assets.single()), result)
            assertEquals(mapOf("unrelated-preference" to "public-retained-value"), fixture.values)
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test
    fun exactSavedSelectionPublishesWithoutPreferenceWrites() = runBlocking {
        Fixture(listOf(row("a"), row("b")), "b:0").use { fixture ->
            assertEquals("b", withTimeout(1500) { fixture.state.assetWithChain.first() }.chain.id)
            assertEquals("b:0", fixture.values[key])
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test
    fun initialEmptyCatalogWaitsWithoutExceptionOrPreferenceMutation() = runBlocking {
        Fixture(emptyList()).use { fixture ->
            val before = fixture.values.toMap()
            assertNull(withTimeoutOrNull(300) { fixture.state.assetWithChain.first() })
            assertEquals(before, fixture.values)
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test
    fun initialUnsupportedCatalogWaitsWithoutExceptionOrPreferenceMutation() = runBlocking {
        Fixture(listOf(row("future", "FUTURE_UNSUPPORTED"))).use { fixture ->
            val before = fixture.values.toMap()
            assertNull(withTimeoutOrNull(300) { fixture.state.assetWithChain.first() })
            assertEquals(before, fixture.values)
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test
    fun missingSavedAssetNeverSubstitutesAnotherAssetOrOverwritesSavedChoice() = runBlocking {
        Fixture(listOf(row("a")), "a:temporarily-missing-asset").use { fixture ->
            val before = fixture.values.toMap()
            assertNull(withTimeoutOrNull(300) { fixture.state.chainAndAsset() })
            assertEquals(listOf("a" to "0"), withTimeout(500) { fixture.state.availableToSelect() }.map { it.chainId to it.id })
            assertEquals(before, fixture.values)
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test
    fun unsupportedSavedChainKeepsHealthyOptionsWithoutAuthorizingSubstitution() = runBlocking {
        Fixture(listOf(row("future", "FUTURE_UNSUPPORTED"), row("healthy")), "future:0").use { fixture ->
            val before = fixture.values.toMap()
            assertNull(withTimeoutOrNull(300) { fixture.state.chainAndAsset() })
            assertEquals(listOf("healthy" to "0"), withTimeout(500) { fixture.state.availableToSelect() }.map { it.chainId to it.id })
            assertEquals(before, fixture.values)
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test
    fun catalogAssetUpdateRefreshesCurrentSelectionWithoutPreferenceWrite() = runBlocking {
        Fixture(listOf(row("a")), "a:0").use { fixture ->
            val observations = mutableListOf<String?>()
            val job = launch { fixture.state.assetWithChain.collect { observations += it.asset.name } }
            try {
                withTimeout(1500) { while (observations.isEmpty()) delay(10) }
                fixture.rows.emit(listOf(row("a", assetName = "Updated public asset")))
                withTimeoutOrNull(500) { while (observations.last() != "Updated public asset") delay(10) }
                assertEquals(listOf("Public asset", "Updated public asset"), observations)
                assertEquals("a:0", fixture.values[key])
                assertTrue(fixture.writes.isEmpty())
            } finally {
                job.cancel()
            }
        }
    }

    @Test
    fun explicitSelectionUpdatePersistsOnlyRequestedChoice() = runBlocking {
        Fixture(listOf(row("a"), row("b")), "a:0").use { fixture ->
            fixture.state.update("b", "0")
            assertEquals("b", withTimeout(1500) { fixture.state.assetWithChain.first() }.chain.id)
            assertEquals(listOf(key to "b:0"), fixture.writes)
            assertEquals(mapOf("unrelated-preference" to "public-retained-value", key to "b:0"), fixture.values)
        }
    }

    @Test
    fun absentChoiceRecoversAfterEmptyCatalogWithoutPersistingDerivedDefault() = runBlocking {
        Fixture(emptyList()).use { fixture ->
            val observations = mutableListOf<String>()
            val job = launch { fixture.state.assetWithChain.collect { observations += it.chain.id } }
            try {
                delay(150)
                assertTrue(observations.isEmpty())
                fixture.rows.emit(listOf(row("healthy")))
                withTimeout(1500) { while (observations.isEmpty()) delay(10) }
                assertEquals(listOf("healthy"), observations)
                assertEquals(mapOf("unrelated-preference" to "public-retained-value"), fixture.values)
                assertTrue(fixture.writes.isEmpty())
            } finally {
                job.cancel()
            }
        }
    }

    @Test
    fun unavailableSavedAssetResumesOnlyWhenExactChoiceReturns() = runBlocking {
        Fixture(listOf(row("a"), row("healthy")), "a:preferred").use { fixture ->
            val observations = mutableListOf<Pair<String, String>>()
            val job = launch {
                fixture.state.assetWithChain.collect { observations += it.chain.id to it.asset.id }
            }
            try {
                delay(150)
                assertTrue(observations.isEmpty())
                fixture.rows.emit(listOf(row("a", assetId = "preferred"), row("healthy")))
                withTimeout(1500) { while (observations.isEmpty()) delay(10) }
                assertEquals(listOf("a" to "preferred"), observations)
                assertEquals("a:preferred", fixture.values[key])
                assertTrue(fixture.writes.isEmpty())
            } finally {
                job.cancel()
            }
        }
    }

    @Test
    fun malformedNonNullSavedChoicesPreserveBytesWithoutAuthorizingDefault() = runBlocking {
        listOf("", "missing-delimiter", ":0", "a:").forEach { saved ->
            Fixture(listOf(row("a")), saved).use { fixture ->
                val before = fixture.values.toMap()
                assertNull(withTimeoutOrNull(250) { fixture.state.chainAndAsset() })
                assertNull(withTimeoutOrNull(250) { fixture.state.chainId() })
                assertEquals(listOf("0"), withTimeout(500) { fixture.state.availableToSelect() }.map { it.id })
                assertEquals(before, fixture.values)
                assertTrue(fixture.writes.isEmpty())
            }
        }
    }

    @Test
    fun colonContainingAssetIdRoundTripsAndSelectsOnlyExactSuffix() = runBlocking {
        Fixture(listOf(row("a", assetId = "token:future:42")), "a:token:future:42").use { fixture ->
            val state = object : SingleAssetSharedState(key, fixture.registry, { _, _ -> true }, fixture.preferences) {
                fun roundTrip(chain: String, asset: String) = decode(encode(chain, asset))
            }
            assertEquals("a" to "token:future:42", state.roundTrip("a", "token:future:42"))
            assertEquals("token:future:42", withTimeout(1500) { state.chainAndAsset() }.asset.id)
            assertEquals("a:token:future:42", fixture.values[key])
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test
    fun freshSelectionAndChainIdSubscribersDoNotReuseDisappearedChoice() = runBlocking {
        Fixture(listOf(row("a"), row("healthy")), "a:0").use { fixture ->
            assertEquals("a", withTimeout(1500) { fixture.state.chainAndAsset() }.chain.id)
            fixture.rows.emit(listOf(row("healthy")))
            withTimeout(1500) { fixture.registry.currentChains.first { it.map { chain -> chain.id } == listOf("healthy") } }
            assertNull(withTimeoutOrNull(300) { fixture.state.chainAndAsset() })
            assertNull(withTimeoutOrNull(300) { fixture.state.chainId() })
            assertEquals("a:0", fixture.values[key])
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test
    fun explicitUpdateResumesUnavailableChoiceOnlyOnRequestedNetwork() = runBlocking {
        Fixture(listOf(row("a"), row("b")), "unavailable:0").use { fixture ->
            assertNull(withTimeoutOrNull(250) { fixture.state.chainAndAsset() })
            fixture.state.update("b", "0")
            val selected = withTimeout(1500) { fixture.state.chainAndAsset() }
            assertEquals("b" to "0", selected.chain.id to selected.asset.id)
            assertEquals(listOf(key to "b:0"), fixture.writes)
            assertEquals("b:0", fixture.values[key])
        }
    }

    @Test
    fun overridableDecodeFailuresPropagateWithoutFallbackOrPreferenceWrites() = runBlocking {
        listOf(
            CancellationException("public cancellation"),
            OutOfMemoryError("public allocation failure"),
            AssertionError("public assertion"),
            IllegalStateException("public decode failure")
        ).forEach { failure ->
            Fixture(listOf(row("a")), "a:0").use { fixture ->
                val state = object : SingleAssetSharedState(key, fixture.registry, { _, _ -> true }, fixture.preferences) {
                    override fun decode(value: String): Pair<String, String> = throw failure
                }
                val actual = try {
                    withTimeout(1500) { state.chainAndAsset() }
                    null
                } catch (caught: Throwable) {
                    caught
                }
                // Coroutine stacktrace recovery may copy the exception and retain its origin as cause.
                val origin = if (actual !== failure && actual != null &&
                    actual.javaClass == failure.javaClass && actual.message == failure.message
                ) actual.cause else actual
                assertSame(failure, origin)
                assertEquals("a:0", fixture.values[key])
                assertTrue(fixture.writes.isEmpty())
            }
        }
    }

    @Test
    fun filterFailuresPropagateForAbsentChoiceWithoutWritingPreference() = runBlocking {
        val failure = CancellationException("public filter cancellation")
        Fixture(listOf(row("a"))).use { fixture ->
            val state = object : SingleAssetSharedState(key, fixture.registry, { _, _ -> throw failure }, fixture.preferences) {}
            val actual = try {
                withTimeout(1500) { state.chainAndAsset() }
                null
            } catch (caught: Throwable) {
                caught
            }
            // Coroutine stacktrace recovery may copy the exception and retain its origin as cause.
            val origin = if (actual !== failure && actual != null &&
                actual.javaClass == failure.javaClass && actual.message == failure.message
            ) actual.cause else actual
            assertSame(failure, origin)
            assertEquals(mapOf("unrelated-preference" to "public-retained-value"), fixture.values)
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test
    fun encodingFailurePreventsExplicitPreferenceWrite() {
        Fixture(listOf(row("a")), "a:0").use { fixture ->
            val failure = AssertionError("public encode failure")
            val state = object : SingleAssetSharedState(key, fixture.registry, { _, _ -> true }, fixture.preferences) {
                override fun encode(chainId: String, chainAssetId: String): String = throw failure
            }
            val actual = try {
                state.update("a", "0")
                null
            } catch (caught: Throwable) {
                caught
            }
            assertSame(failure, actual)
            assertEquals("a:0", fixture.values[key])
            assertTrue(fixture.writes.isEmpty())
        }
    }
}
