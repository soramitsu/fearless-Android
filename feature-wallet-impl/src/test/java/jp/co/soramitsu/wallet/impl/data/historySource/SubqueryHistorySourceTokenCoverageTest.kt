package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Alias
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A token's SubQuery history keeps only the records whose asset id is the token's SCALE-encoded currency id. */
@RunWith(RobolectricTestRunner::class)
class SubqueryHistorySourceTokenCoverageTest {

    private val api = mockk<OperationsHistoryApi>()
    private val ausd = WalletTestModels.asset(
        id = "ausd",
        symbol = "AUSD",
        chainId = CHAIN_ID,
        precision = 12,
        currencyId = "AUSD",
        isUtility = false,
        type = ChainAssetType.OrmlAsset
    )
    private val chain = WalletTestModels.chain(id = CHAIN_ID, assets = listOf(WalletTestModels.asset(chainId = CHAIN_ID), ausd))
    private val chainRegistry = mockk<ChainRegistry> {
        coEvery { getRuntime(CHAIN_ID) } returns currencyIdRuntime()
    }

    @Test
    fun `a token keeps the records of its encoded currency id`(): Unit = runBlocking {
        coEvery { api.getOperationsHistory("https://subquery", any()) } returns response()

        val page = SubqueryHistorySource(api, chainRegistry, "https://subquery").getOperations(
            10, null, TransactionFilter.entries.toSet(), ByteArray(32), chain, ausd, "5Me"
        )

        // Token(AUSD) is variant 0 of CurrencyId and AUSD is variant 1 of TokenSymbol: 0x0001.
        assertEquals(listOf("token-transfer", "token-reward"), page.map { it.id })
        assertEquals("end", page.nextCursor)
        assertTrue(page.all { it.chainAsset == ausd })
        assertTrue(page.first().type is Operation.Type.Transfer)
        coVerify(exactly = 1) { chainRegistry.getRuntime(CHAIN_ID) }
    }

    /** A runtime whose only CurrencyId is `Token(TokenSymbol)` with ACA = 0 and AUSD = 1. */
    private fun currencyIdRuntime(): RuntimeSnapshot {
        val tokenSymbol = DictEnum(
            "TokenSymbol",
            listOf(DictEnum.Entry("ACA", TypeReference(Null)), DictEnum.Entry("AUSD", TypeReference(Null)))
        )
        val currencyId = DictEnum("CurrencyId", listOf(DictEnum.Entry("Token", TypeReference(tokenSymbol))))
        val registry = TypeRegistry(
            types = mapOf("acala_primitives::currency::CurrencyId" to TypeReference(Alias("CurrencyIdAlias", TypeReference(currencyId)))),
            dynamicTypeResolver = DynamicTypeResolver(emptyList())
        )
        return RuntimeSnapshot(registry, mockk())
    }

    private fun response() = SubQueryResponse(
        Gson().fromJson(
            """{"query": {"historyElements": {"pageInfo": {"startCursor": "start", "endCursor": "end"}, "nodes": [
              {"id": "native-transfer", "timestamp": "6", "address": "5Me",
                "transfer": {"amount": 1, "to": "5Bob", "from": "5Me", "fee": 1, "block": "1", "success": true}},
              {"id": "token-transfer", "timestamp": "5", "address": "5Me",
                "transfer": {"amount": 1, "to": "5Bob", "from": "5Me", "fee": 1, "block": "1", "success": true, "assetId": "0x0001"}},
              {"id": "other-token-transfer", "timestamp": "5", "address": "5Me",
                "transfer": {"amount": 1, "to": "5Bob", "from": "5Me", "fee": 1, "block": "1", "success": true, "assetId": "0x0000"}},
              {"id": "token-reward", "timestamp": "4", "address": "5Me",
                "reward": {"era": 1, "amount": 2, "isReward": true, "validator": "5Val", "assetId": "0x0001"}}
            ]}}}""",
            SubqueryHistoryElementResponse::class.java
        )
    )

    private companion object {
        const val CHAIN_ID = "acala"
    }
}
