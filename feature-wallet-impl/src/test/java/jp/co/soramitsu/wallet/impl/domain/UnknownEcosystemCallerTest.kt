package jp.co.soramitsu.wallet.impl.domain

import com.google.gson.Gson
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.cache.*
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChainOrNull
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetReader
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetContext
import jp.co.soramitsu.wallet.impl.data.repository.SelectedAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsInitializer
import jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsSyncServiceProvider
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.wallet.impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class UnknownEcosystemCallerTest {
    private fun fixture(id: String, ecosystem: String = "Substrate") = JoinedChainInfo(
        ChainLocal(
            id, null, null, 1, id, null, "public-icon", null, 42, false, false, false,
            false, false, false, false, false, null, ecosystem, null, "OnChain", null, null
        ),
        emptyList(),
        listOf(
            ChainAssetLocal(
                "0", "Public", "PUB", id, "", null, "UNSUPPORTED", 12,
                null, true, null, null, null, null, true, null, null
            )
        ),
        emptyList()
    )

    @Test
    fun chainInteractorPreservesExactHealthyChainInBothOrders() = runBlocking {
        val healthy = fixture("healthy")
        for (reverse in listOf(false, true)) {
            val unknown = fixture("unknown", "FUTURE_UNSUPPORTED")
            val rows = if (reverse) listOf(healthy, unknown) else listOf(unknown, healthy)
            val before = Gson().toJson(rows)
            val dao = Mockito.mock(ChainDao::class.java)
            Mockito.`when`(dao.joinChainInfoFlow()).thenReturn(flowOf(rows))
            val interactor = ChainInteractor(
                dao,
                Mockito.mock(XcmEntitiesFetcher::class.java),
                Mockito.mock(CrossChainRouteProviderRegistry::class.java)
            )
            val result = interactor.getChainsFlow().first()
            assertEquals(Gson().toJson(listOf(mapChainLocalToChain(healthy))), Gson().toJson(result))
            assertEquals(before, Gson().toJson(rows))
            Mockito.verify(dao).joinChainInfoFlow()
            Mockito.verifyNoMoreInteractions(dao)
        }
    }

    @Test
    fun chainInteractorEmitsEmptyForOnlyUnsupportedCatalog() = runBlocking {
        val dao = Mockito.mock(ChainDao::class.java)
        Mockito.`when`(dao.joinChainInfoFlow()).thenReturn(flowOf(listOf(fixture("unknown", "future"))))
        val interactor = ChainInteractor(
            dao,
            Mockito.mock(XcmEntitiesFetcher::class.java),
            Mockito.mock(CrossChainRouteProviderRegistry::class.java)
        )
        assertTrue(interactor.getChainsFlow().first().isEmpty())
    }

    @Test
    fun remoteAssetInitializationDispatchesOnlyExactHealthyPeerInBothOrders() = runBlocking {
        val healthy = fixture("healthy")
        val expected = mapChainLocalToChain(healthy)
        for (reverse in listOf(false, true)) {
            val rows = listOf(fixture("unknown", "future"), healthy).let { if (reverse) it.reversed() else it }
            val before = Gson().toJson(rows)
            val dao = Mockito.mock(ChainDao::class.java)
            Mockito.`when`(dao.getJoinChainInfo()).thenReturn(rows)
            val provider = Mockito.mock(RemoteAssetsSyncServiceProvider::class.java)
            val service = Mockito.mock(RemoteAssetsSyncService::class.java)
            Mockito.`when`(provider.provide(expected)).thenReturn(service)
            RemoteAssetsInitializer(dao, provider).invoke()
            Mockito.verify(provider).provide(expected)
            Mockito.verifyNoMoreInteractions(provider)
            Mockito.verify(service).sync()
            Mockito.verifyNoMoreInteractions(service)
            Mockito.verify(dao).getJoinChainInfo()
            Mockito.verifyNoMoreInteractions(dao)
            assertEquals(before, Gson().toJson(rows))
        }
    }

    @Test
    fun remoteAssetInitializationDoesNotDispatchOnlyUnsupportedCatalog() = runBlocking {
        val dao = Mockito.mock(ChainDao::class.java)
        Mockito.`when`(dao.getJoinChainInfo()).thenReturn(listOf(fixture("unknown", "future")))
        val provider = Mockito.mock(RemoteAssetsSyncServiceProvider::class.java)
        RemoteAssetsInitializer(dao, provider).invoke()
        Mockito.verifyNoInteractions(provider)
    }

    private fun wallet(rows: Map<JoinedChainInfo, CachedAssetWithPriceRead>): WalletRepositoryImpl {
        val reader = Mockito.mock(CurrentAccountAssetReader::class.java)
        val meta = Mockito.mock(MetaAccount::class.java)
        Mockito.`when`(meta.id).thenReturn(42L)
        val snapshot = CurrentAccountAssetSnapshot(
            CurrentAccountAssetContext(meta, FiatValuationContext("usd")),
            rows.keys.mapNotNull(::mapChainLocalToChainOrNull).associateBy { it.id },
            rows.values.toList(), quotesReady = true
        )
        Mockito.`when`(reader.observeAccountSnapshots(42L))
            .thenReturn(flowOf(SelectedAccountAssetSnapshot.Ready(snapshot)))
        val constructor = WalletRepositoryImpl::class.java.constructors.single()
        val arguments = constructor.parameterTypes.map { type ->
            if (type == CurrentAccountAssetReader::class.java) reader else Mockito.mock(type)
        }.toTypedArray()
        return constructor.newInstance(*arguments) as WalletRepositoryImpl
    }

    private fun balance(id: String, value: Long) = CachedAssetWithPriceRead(
        CachedAssetRead(
            "0", id, byteArrayOf(value.toByte()), 42L, null, 0, true, true, "Public $id", null,
            CachedNumericState.Known(java.math.BigInteger.valueOf(value)),
            CachedNumericState.Missing, CachedNumericState.Missing, CachedNumericState.Missing,
            CachedNumericState.Missing, CachedNumericState.Missing, CachedNumericState.Missing
        ), null
    )

    @Test
    fun fundedMappingKeepsEachHealthyChainPairedWithItsOwnBalanceInBothOrders() = runBlocking {
        val healthy = fixture("healthy")
        val unknown = fixture("unknown", "future")
        val healthyAsset = balance("healthy", 111L)
        val unknownAsset = balance("unknown", 222L)
        val expected = wallet(linkedMapOf(healthy to healthyAsset)).observeChainsPerAsset(42L, "0").first()
        assertEquals(1, expected.size)
        for (reverse in listOf(false, true)) {
            val entries = listOf(unknown to unknownAsset, healthy to healthyAsset).let { if (reverse) it.reversed() else it }
            val rows = entries.toMap(LinkedHashMap())
            val before = Gson().toJson(rows.values)
            val actual = wallet(rows).observeChainsPerAsset(42L, "0").first()
            assertEquals(listOf("healthy"), actual.keys.map { it.id })
            assertEquals(Gson().toJson(expected.values.single()), Gson().toJson(actual.values.single()))
            assertEquals(before, Gson().toJson(rows.values))
        }
    }

    @Test
    fun fundedMappingEmitsEmptyWhenNoSupportedPairRemains() = runBlocking {
        val rows = mapOf(fixture("unknown", "future") to balance("unknown", 222L))
        assertTrue(wallet(rows).observeChainsPerAsset(42L, "0").first().isEmpty())
    }

    @Test
    fun accountProjectionRetainsUnknownChainKeysPublicIdentityAndFavoriteChoice() {
        val publicKey = ByteArray(32) { 7 }
        val accountId = ByteArray(32) { 8 }
        val wallet = MetaAccountLocal(
            publicKey, CryptoType.SR25519, accountId, null, null, null,
            "Public wallet", true, 0, false, null, true
        ).apply { id = 42 }
        val account = ChainAccountLocal(42, "unknown", publicKey, accountId, CryptoType.SR25519, "Public account", true)
        val favorite = FavoriteChainLocal(42, "unknown", false)
        val joined = RelationJoinedMetaAccountInfo(wallet, listOf(account), listOf(favorite))
        val before = Gson().toJson(joined)
        val result = jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountLocalToMetaAccount(emptyMap(), joined)
        assertEquals(setOf("unknown"), result.chainAccounts.keys)
        val retained = result.chainAccounts.getValue("unknown")
        assertNull(retained.chain)
        assertArrayEquals(publicKey, retained.publicKey)
        assertArrayEquals(accountId, retained.accountId)
        assertEquals(CryptoType.SR25519, retained.cryptoType)
        assertEquals("Public account", retained.accountName)
        assertEquals(setOf("unknown"), result.favoriteChains.keys)
        assertFalse(result.favoriteChains.getValue("unknown").isFavorite)
        assertNull(result.favoriteChains.getValue("unknown").chain)
        assertEquals(before, Gson().toJson(joined))
    }
}
