package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubsquidHistoryElementsConnectionResponse
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SquidHistorySourcesRobolectricTest {

    private val api = mockk<OperationsHistoryApi>()
    private val dot = WalletTestModels.asset(staking = Asset.StakingType.RELAYCHAIN)
    private val chain = WalletTestModels.chain(assets = listOf(dot))

    @Test
    fun subsquidMapsTransfersAndRewardsAndPagesByEndCursor(): Unit = runBlocking {
        val request = slot<SubsquidHistoryRequest>()
        coEvery { api.getSubsquidOperationsHistory("https://squid", capture(request)) } returns subsquid(hasNext = true, endCursor = "40")

        val page = SubsquidHistorySource(api, "https://squid").getOperations(
            20, "20", TransactionFilter.entries.toSet(), ByteArray(32), chain, dot, "5Me"
        )

        assertEquals("40", page.nextCursor)
        assertTrue(request.captured.query.contains("\"5Me\""))
        assertTrue(request.captured.query.contains("first: 20"))
        val transfer = page.first { it.type is Operation.Type.Transfer }.type as Operation.Type.Transfer
        assertEquals(BigInteger.TEN, transfer.amount)
        assertEquals(Operation.Status.COMPLETED, transfer.status)
        val reward = page.first { it.type is Operation.Type.Reward }.type as Operation.Type.Reward
        assertEquals(4, reward.era)
        assertEquals(BigInteger.ZERO, (page.last().type as Operation.Type.Reward).amount)
        assertEquals(2_000L, page.first().time)
    }

    @Test
    fun subsquidHonoursFiltersAndShortPages(): Unit = runBlocking {
        coEvery { api.getSubsquidOperationsHistory(any(), any()) } returns subsquid(hasNext = true, endCursor = "5")

        val transfersOnly = SubsquidHistorySource(api, "https://squid").getOperations(
            20, "0", setOf(TransactionFilter.TRANSFER), ByteArray(32), chain, dot, "5Me"
        )

        assertNull(transfersOnly.nextCursor)
        assertTrue(transfersOnly.all { it.type is Operation.Type.Transfer })
    }

    @Test
    fun subqueryKeepsNativeOperationsForAUtilityAsset(): Unit = runBlocking {
        val request = slot<SubqueryHistoryRequest>()
        coEvery { api.getOperationsHistory("https://subquery", capture(request)) } returns subquery()

        val page = SubqueryHistorySource(api, mockk(), "https://subquery").getOperations(
            10, "c1", setOf(TransactionFilter.TRANSFER, TransactionFilter.EXTRINSIC), ByteArray(32), chain, dot, "5Me"
        )

        assertEquals("end", page.nextCursor)
        assertEquals(listOf("native-transfer", "native-reward"), page.map { it.id })
        assertTrue(request.captured.query.contains("reward"))
        assertTrue(request.captured.query.contains("\"c1\""))
    }

    @Test
    fun subqueryRequestOmitsRewardsForAssetsWithoutStakingAndFiltersAllTypes(): Unit = runBlocking {
        val request = slot<SubqueryHistoryRequest>()
        val plain = WalletTestModels.asset()
        coEvery { api.getOperationsHistory(any(), capture(request)) } returns subquery()

        SubqueryHistorySource(api, mockk(), "https://subquery").getOperations(
            10, null, TransactionFilter.entries.toSet(), ByteArray(32), chain, plain, "5Me"
        )

        assertFalse(request.captured.query.contains("reward\n"))
        assertTrue(request.captured.query.contains("after: null"))
    }

    private fun subsquid(hasNext: Boolean, endCursor: String) = SubsquidResponse(
        Gson().fromJson(
            """{"historyElementsConnection": {"pageInfo": {"hasNextPage": $hasNext, "endCursor": "$endCursor"}, "edges": [
              {"node": {"id": "t", "extrinsicHash": "0xt", "timestamp": 2, "address": "5Me", "success": true,
                "transfer": {"amount": "10", "to": "5Bob", "from": "5Me", "fee": 1}}},
              {"node": {"id": "r", "timestamp": 1, "address": "5Me", "success": true,
                "reward": {"amount": "3", "era": 4, "stash": "5Me", "validator": "5Val"}}},
              {"node": {"id": "r2", "timestamp": 1, "address": "5Me", "success": true,
                "reward": {"amount": "not a number", "era": null}}}
            ]}}""",
            SubsquidHistoryElementsConnectionResponse::class.java
        )
    )

    private fun subquery() = SubQueryResponse(
        Gson().fromJson(
            """{"query": {"historyElements": {"pageInfo": {"startCursor": "start", "endCursor": "end"}, "nodes": [
              {"id": "native-transfer", "timestamp": "5", "address": "5Me",
                "transfer": {"amount": 1, "to": "5Bob", "from": "5Me", "fee": 1, "block": "1", "success": true}},
              {"id": "token-transfer", "timestamp": "5", "address": "5Me",
                "transfer": {"amount": 1, "to": "5Bob", "from": "5Me", "fee": 1, "block": "1", "success": true, "assetId": "0x01"}},
              {"id": "native-reward", "timestamp": "4", "address": "5Me",
                "reward": {"era": 1, "amount": 2, "isReward": true, "validator": "5Val"}}
            ]}}}""",
            SubqueryHistoryElementResponse::class.java
        )
    )
}
