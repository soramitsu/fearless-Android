package jp.co.soramitsu.xnetworking.lib.datasources.txhistory.impl.domain.adapters

import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.ConfigDAO
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.ChainInfo
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxFilter
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryInfo
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestClientConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HistoryInfoRemoteLoaderFacadeTest {
    private val config = object : ConfigDAO {}
    private val client = object : RestClient {
        override val config: AbstractRestClientConfig get() = error("The empty fallback must not start a request")
    }

    @Test
    fun firstPageWithoutFiltersReturnsAnEmptyTerminalPage() = runBlocking<Unit> {
        val facade = HistoryInfoRemoteLoaderFacade(config, client)
        assertSame(config, facade.configDAO)
        assertSame(client, facade.restClient)
        assertEquals(
            TxHistoryInfo(null, true, emptyList()),
            facade.loadHistoryInfo(20, null, "public-account", ChainInfo.Simple("chain"), emptySet())
        )
    }

    @Test
    fun continuationWithAllFiltersDoesNotPretendAnotherPageExists() = runBlocking<Unit> {
        val facade = HistoryInfoRemoteLoaderFacade(config, client)
        assertEquals(
            TxHistoryInfo(null, true, emptyList()),
            facade.loadHistoryInfo(1, "old-cursor", "public-account", ChainInfo.Simple("chain"), TxFilter.entries.toSet())
        )
    }
}
