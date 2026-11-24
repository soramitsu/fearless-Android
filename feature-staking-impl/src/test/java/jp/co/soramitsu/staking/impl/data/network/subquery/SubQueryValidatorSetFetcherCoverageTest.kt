package jp.co.soramitsu.staking.impl.data.network.subquery

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common.base.errors.RewardsNotSupportedWarning
import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse
import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse.EraValidatorInfo
import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse.EraValidatorInfo.Nodes
import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse.EraValidatorInfo.Nodes.Node
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validators a stash nominated over the history depth, from SubQuery, the SORA explorer or SubSquid. */
class SubQueryValidatorSetFetcherCoverageTest {

    private val api = mockk<StakingApi>()
    private val repository = mockk<StakingRelayChainScenarioRepository>()
    private val registry = mockk<ChainRegistry>()
    private val explorer = mockk<BlockExplorerRepository>()
    private val fetcher = SubQueryValidatorSetFetcher(api, repository, registry, explorer)

    init {
        // Eras 7, 8 and 9: the history depth of 3 before the active era 10.
        coEvery { repository.getActiveEraIndex(any()) } returns BigInteger.TEN
        coEvery { repository.getCurrentEraIndex(any()) } returns BigInteger.TEN
        coEvery { repository.getHistoryDepth(any()) } returns BigInteger.valueOf(3)
    }

    private fun chain(type: Chain.ExternalApi.Section.Type?, id: String = "validators-chain"): String {
        val external = type?.let { Chain.ExternalApi(Chain.ExternalApi.Section(it, URL), null, null) }
        val chain = StakingLaneFixtures.chain(StakingLaneFixtures.chainAsset(chainId = id), externalApi = external)
        coEvery { registry.getChain(id) } returns chain
        return id
    }

    private fun node(address: String, era: Long) = Node("$address-$era", address, BigInteger.valueOf(era), "1", "1")

    @Test
    fun `subquery validators are distinct addresses`() = runTest {
        val nodes = Nodes(listOf(node("A", 7), node("A", 8), node("B", 9)))
        coEvery { api.getValidatorsInfo(URL, any()) } returns SubQueryResponse(EraValidatorInfoQueryResponse(EraValidatorInfo(nodes)))

        assertEquals(listOf("A", "B"), fetcher.fetchAllValidators(chain(Chain.ExternalApi.Section.Type.SUBQUERY), STASH))

        coEvery { api.getValidatorsInfo(URL, any()) } returns SubQueryResponse(EraValidatorInfoQueryResponse(null))
        assertTrue(fetcher.fetchAllValidators(chain(Chain.ExternalApi.Section.Type.SUBQUERY), STASH).isEmpty())
    }

    @Test
    fun `sora validators come from the explorer over the historical eras`() = runTest {
        val id = chain(Chain.ExternalApi.Section.Type.SORA, "sora-chain")
        coEvery { explorer.getValidatorsList(id, STASH, listOf("7", "8", "9")) } returns listOf("V")

        assertEquals(listOf("V"), fetcher.fetchAllValidators(id, STASH))

        coEvery { explorer.getValidatorsList(id, STASH, any()) } throws IllegalStateException("explorer down")
        assertTrue(fetcher.fetchAllValidators(id, STASH).isEmpty())
    }

    @Test
    fun `subsquid collators are not listed`() = runTest {
        assertTrue(fetcher.fetchAllValidators(chain(Chain.ExternalApi.Section.Type.SUBSQUID), STASH).isEmpty())
        coVerify(exactly = 0) { api.getValidatorsInfo(any(), any()) }
    }

    @Test
    fun `chains without a supported indexer are refused`() = runTest {
        val missing = runCatching { fetcher.fetchAllValidators(chain(null), STASH) }.exceptionOrNull()
        val unknown = runCatching { fetcher.fetchAllValidators(chain(Chain.ExternalApi.Section.Type.ETHERSCAN, "other"), STASH) }.exceptionOrNull()

        assertTrue(missing is RewardsNotSupportedWarning)
        assertTrue(unknown is RewardsNotSupportedWarning)
    }

    private companion object {
        const val URL = "https://validators.example/graphql"
        const val STASH = "stash-address"
    }
}
