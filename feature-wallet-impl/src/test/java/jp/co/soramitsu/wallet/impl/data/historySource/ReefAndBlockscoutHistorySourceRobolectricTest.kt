package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.model.response.ReefHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZetaAddress
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZetaFee
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZetaHistoryItem
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZetaHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZetaTotal
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReefAndBlockscoutHistorySourceRobolectricTest {

    private val api = mockk<OperationsHistoryApi>()
    private val reef = WalletTestModels.asset(id = "reef", chainId = "reef", precision = 18)
    private val reefChain = WalletTestModels.chain(id = "reef", assets = listOf(reef))

    @Test
    fun reefMergesTransfersRewardsAndExtrinsicsNewestFirst(): Unit = runBlocking {
        coEvery { api.getReefOperationsHistory("https://reef", any()) } returnsMany listOf(
            reefResponse(TRANSFERS), reefResponse(REWARDS), reefResponse(EXTRINSICS)
        )

        val page = ReefHistorySource(api, "https://reef").getOperations(
            20, "50", TransactionFilter.entries.toSet(), ByteArray(32), reefChain, reef, "5Me"
        )

        assertEquals(listOf("e1", "r1", "t1", "0xtx2"), page.map { it.id })
        assertEquals("100", page.nextCursor)
        val transfer = page.first { it.type is Operation.Type.Transfer }.type as Operation.Type.Transfer
        assertEquals(BigInteger.valueOf(5), transfer.fee)
        assertEquals("5Bob", transfer.receiver)
        val extrinsic = page.first().type as Operation.Type.Extrinsic
        assertEquals("staking", extrinsic.module)
        assertEquals(Operation.Status.FAILED, extrinsic.status)
        assertEquals(BigInteger.ZERO, extrinsic.fee)
        coVerify(exactly = 3) { api.getReefOperationsHistory("https://reef", any()) }
    }

    @Test
    fun reefWithoutMorePagesHasNoCursor(): Unit = runBlocking {
        coEvery { api.getReefOperationsHistory(any(), any()) } returns reefResponse("""{"transfersConnection": {"pageInfo": {"hasNextPage": true, "endCursor": "10"}, "edges": []}}""")

        val small = ReefHistorySource(api, "https://reef").getOperations(20, null, setOf(TransactionFilter.TRANSFER), ByteArray(32), reefChain, reef, "5Me")
        coEvery { api.getReefOperationsHistory(any(), any()) } returns reefResponse("{}")
        val empty = ReefHistorySource(api, "https://reef").getOperations(20, "0", setOf(TransactionFilter.REWARD, TransactionFilter.EXTRINSIC), ByteArray(32), reefChain, reef, "5Me")

        assertNull(small.nextCursor)
        assertNull(empty.nextCursor)
        assertTrue(empty.isEmpty())
    }

    @Test
    fun blockscoutReadsNativeTransactionsAndTokenTransfers(): Unit = runBlocking {
        val account = ByteArray(20) { 0x11 }
        val native = WalletTestModels.asset(id = "zeta", chainId = "zeta", precision = 18, type = ChainAssetType.Normal)
        val token = WalletTestModels.asset(id = "0xtoken", chainId = "zeta", precision = 6, type = ChainAssetType.ERC20)
        val chain = WalletTestModels.chain(id = "zeta", assets = listOf(native, token))
        coEvery { api.getZetaOperationsHistory("https://zeta/addresses/0x${"11".repeat(20)}/transactions") } returns ZetaHistoryResponse(
            listOf(
                ZetaHistoryItem("2024-01-02T03:04:05Z", ZetaFee("actual", BigInteger.ONE), "success", ZetaAddress("0xTO"), ZetaAddress("0xFROM"), BigInteger.TEN, hash = "0x1"),
                ZetaHistoryItem("2024-01-02T03:04:05Z", null, "error", ZetaAddress("0xTO"), ZetaAddress("0xFROM"), BigInteger.TEN, hash = null, txHash = "0x2"),
                ZetaHistoryItem("2024-01-02T03:04:05Z", null, "other", ZetaAddress("0xTO"), ZetaAddress("0xFROM"), BigInteger.TEN)
            )
        )
        coEvery { api.getZetaOperationsHistory("https://zeta/addresses/0x${"11".repeat(20)}/token-transfers?token=0xtoken") } returns ZetaHistoryResponse(
            listOf(ZetaHistoryItem("2024-01-02T03:04:05Z", null, "success", ZetaAddress("0xTO"), ZetaAddress("0xFROM"), null, ZetaTotal(BigDecimal("1.5"), 6), txHash = "0x3"))
        )

        val source = BlockscoutHistorySource(api, "https://zeta/")
        val nativePage = source.getOperations(20, null, emptySet(), account, chain, native, "0xme")
        val tokenPage = source.getOperations(20, null, emptySet(), account, chain, token, "0xme")

        assertEquals(listOf("0x1", "0x2", ""), nativePage.map { it.id })
        assertEquals(
            listOf(Operation.Status.COMPLETED, Operation.Status.FAILED, Operation.Status.COMPLETED),
            nativePage.map { (it.type as Operation.Type.Transfer).status }
        )
        assertEquals("0xto", (nativePage.first().type as Operation.Type.Transfer).receiver)
        assertEquals(BigInteger.valueOf(1_500_000), (tokenPage.single().type as Operation.Type.Transfer).amount)
        assertNull(tokenPage.nextCursor)
    }

    @Test
    fun blockscoutRefusesUnsupportedAssetTypesWithAnEmptyPage(): Unit = runBlocking {
        val orml = WalletTestModels.asset(id = "x", chainId = "zeta", type = ChainAssetType.OrmlChain)

        val page = BlockscoutHistorySource(api, "https://zeta/").getOperations(20, null, emptySet(), ByteArray(20), reefChain, orml, "0xme")

        assertTrue(page.isEmpty())
        coVerify(exactly = 0) { api.getZetaOperationsHistory(any()) }
    }

    private fun reefResponse(json: String) = SubsquidResponse(Gson().fromJson(json, ReefHistoryResponse::class.java))

    private companion object {
        const val TRANSFERS = """{"transfersConnection": {"pageInfo": {"hasNextPage": true, "endCursor": "100"}, "edges": [
            {"node": {"id": "t1", "amount": 10, "type": "Native", "timestamp": "2024-01-02T03:04:05Z", "success": true,
              "to": {"id": "5Bob"}, "from": {"id": "5Me"}, "extrinsicHash": "0xtx", "signedData": {"fee": {"partialFee": 5}}}},
            {"node": {"amount": 11, "type": "Native", "timestamp": "2023-01-02T03:04:05Z", "success": false,
              "to": {"id": "5Me"}, "from": {"id": "5Bob"}, "extrinsicHash": "0xtx2", "signedData": null}}
        ]}}"""
        const val REWARDS = """{"stakingsConnection": {"pageInfo": {"hasNextPage": false, "endCursor": "1"}, "edges": [
            {"node": {"id": "r1", "type": "Reward", "amount": 3, "timestamp": "2024-02-02T03:04:05Z", "signer": {"id": "5Me"}}}
        ]}}"""
        const val EXTRINSICS = """{"extrinsicsConnection": {"pageInfo": {"hasNextPage": false, "endCursor": "x"}, "edges": [
            {"node": {"id": "e1", "hash": "0xe", "method": "bond", "section": "staking", "signedData": null, "status": "error",
              "signer": "5Me", "timestamp": "2024-03-02T03:04:05Z", "type": "signed"}}
        ]}}"""
    }
}
