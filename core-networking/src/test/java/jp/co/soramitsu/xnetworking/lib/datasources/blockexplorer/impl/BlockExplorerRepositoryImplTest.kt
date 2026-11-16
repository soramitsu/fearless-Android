package jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.impl

import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.ConfigDAO
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.TxHistoryRepository
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestClientConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BlockExplorerRepositoryImplTest {
    @Test
    fun unavailableExplorerDataReturnsEmptyCollectionsWithoutStartingRequests() = runBlocking<Unit> {
        val config = object : ConfigDAO {}
        val history = object : TxHistoryRepository {}
        val client = object : RestClient {
            override val config: AbstractRestClientConfig get() = error("The fallback must not start a request")
        }
        val repository = BlockExplorerRepositoryImpl(config, client, history)
        assertSame(config, repository.configDAO)
        assertSame(client, repository.restClient)
        assertSame(history, repository.txHistoryRepository)
        assertEquals(emptyList<Any>(), repository.getApy("chain"))
        assertEquals(emptyList<String>(), repository.getStakingRewarded("chain", "public-account"))
        assertEquals(emptyList<String>(), repository.getValidatorsList("chain", "public-stash", listOf("1", "2")))
    }
}
