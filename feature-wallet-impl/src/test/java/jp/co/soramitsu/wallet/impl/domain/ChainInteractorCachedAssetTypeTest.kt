package jp.co.soramitsu.wallet.impl.domain

import com.google.gson.Gson
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class ChainInteractorCachedAssetTypeTest {

    private fun asset(type: String?, id: String = "public"): ChainAssetLocal = ChainAssetLocal(
        id = id,
        name = "Public token",
        symbol = "PUB",
        chainId = "public-chain",
        icon = "public-icon",
        priceId = null,
        staking = "UNSUPPORTED",
        precision = 12,
        purchaseProviders = "[\"public-provider\"]",
        isUtility = false,
        type = type,
        currencyId = "123",
        existentialDeposit = "1",
        color = "public-color",
        isNative = false,
        priceProvider = null,
        coinbaseUrl = null
    )

    private suspend fun read(rows: List<ChainAssetLocal>): List<Asset> {
        val before = Gson().toJson(rows)
        val dao = Mockito.mock(ChainDao::class.java)
        Mockito.`when`(dao.getAssetsConfigs()).thenReturn(rows)
        val interactor = ChainInteractor(
            dao,
            Mockito.mock(XcmEntitiesFetcher::class.java),
            Mockito.mock(CrossChainRouteProviderRegistry::class.java)
        )

        val result = interactor.getChainAssets()

        Mockito.verify(dao).getAssetsConfigs()
        Mockito.verifyNoMoreInteractions(dao)
        assertEquals(before, Gson().toJson(rows))
        return result
    }

    @Test
    fun knownValuesRemainExact() = runBlocking {
        val values = ChainAssetType.entries.toList()
        assertEquals(values, read(values.map { asset(it.name, it.name) }).map { it.type })
    }

    @Test
    fun absentTypeRetainsNativeCurrencyCompatibility() = runBlocking {
        val result = read(listOf(asset(null))).single()
        assertNull(result.type)
        assertNull(result.currency)
    }

    @Test
    fun explicitUnknownTypeCannotBecomeNativeCurrency() = runBlocking {
        for (value in listOf("FUTURE_UNRECOGNIZED", "", "normal")) {
            val result = read(listOf(asset(value))).single()
            assertEquals(ChainAssetType.Unknown, result.type)
            assertEquals("123", result.currencyId)
            val failure = try {
                result.currency
                null
            } catch (error: IllegalStateException) {
                error
            }
            assertNotNull(failure)
        }
    }

    @Test
    fun unsupportedAssetPreservesHealthyPeerAndAllOtherFieldsInBothOrders() = runBlocking {
        val healthy = asset("Normal", "healthy")
        for (value in listOf("FUTURE_UNRECOGNIZED", "", "normal")) {
            for (reverse in listOf(false, true)) {
                val unknown = asset(value, "unknown")
                val rows = if (reverse) listOf(healthy, unknown) else listOf(unknown, healthy)
                val expected = read(rows.map { if (it.id == "unknown") it.copy(type = "Unknown") else it })
                val actual = read(rows)

                assertEquals(expected, actual)
                assertEquals(Gson().toJson(expected), Gson().toJson(actual))
                assertEquals(ChainAssetType.Normal, actual.single { it.id == "healthy" }.type)
                println("RECOVERED_INTERACTOR_TYPE value=" + Gson().toJson(value) + " reverse=" + reverse)
            }
        }
    }
}
