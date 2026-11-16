package jp.co.soramitsu.wallet.impl.data.historySource

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.model.response.AtletaAddress
import jp.co.soramitsu.wallet.impl.data.network.model.response.AtletaFee
import jp.co.soramitsu.wallet.impl.data.network.model.response.AtletaHistoryItem
import jp.co.soramitsu.wallet.impl.data.network.model.response.AtletaHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.FireHistoryData
import jp.co.soramitsu.wallet.impl.data.network.model.response.FireHistoryItem
import jp.co.soramitsu.wallet.impl.data.network.model.response.FireHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.VicscanHistoryItem
import jp.co.soramitsu.wallet.impl.data.network.model.response.VicscanHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZchainHistoryAddress
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZchainHistoryItem
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZchainHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The explorer-backed EVM history sources page their transfers and fail closed to an empty page. */
@RunWith(RobolectricTestRunner::class)
class EvmScanHistorySourcesRobolectricTest {

    private val api = mockk<OperationsHistoryApi>()
    private val asset = WalletTestModels.asset(id = "eth", chainId = "evm", precision = 18)
    private val chain = WalletTestModels.chain(id = "evm", assets = listOf(asset))
    private val me = "0xMe"

    @Test
    fun fireMapsReceiptsAndSkipsContractCreations(): Unit = runBlocking {
        coEvery { api.getFireOperationsHistory("https://fire/transactions/address/0xMe", 2, 20) } returns FireHistoryResponse(
            "ok",
            FireHistoryData(
                2,
                listOf(
                    FireHistoryItem("0x1", 1, "2024-01-02T03:04:05Z", BigInteger.ONE, "0xFROM", "0xTO", BigInteger.TEN, BigInteger.ONE, BigInteger.TWO, BigInteger.ONE),
                    FireHistoryItem("0x2", 0, "2024-01-02T03:04:05Z", BigInteger.ONE, "0xFROM", null, BigInteger.TEN, BigInteger.ONE, BigInteger.TWO, BigInteger.ONE)
                )
            ),
            2,
            2
        )

        val page = FireHistorySource(api, "https://fire/").operations("2")

        assertEquals("3", page.nextCursor)
        val transfer = page.single().type as Operation.Type.Transfer
        assertEquals("0xto", transfer.receiver)
        assertEquals("0xfrom", transfer.sender)
        assertEquals(Operation.Status.COMPLETED, transfer.status)
        assertEquals(1_704_164_645_000L, page.single().time)
    }

    @Test
    fun atletaMapsResultsToStatuses(): Unit = runBlocking {
        coEvery { api.getAtletaOperationsHistory("https://atleta/addresses/0xMe/transactions") } returns AtletaHistoryResponse(
            listOf(
                AtletaHistoryItem("2024-01-02T03:04:05Z", AtletaFee("actual", BigInteger.ONE), "success", AtletaAddress("0xto"), AtletaAddress("0xfrom"), BigInteger.TEN, "0xa"),
                AtletaHistoryItem("2024-01-02T03:04:05Z", null, "error", AtletaAddress("0xto"), AtletaAddress("0xfrom"), BigInteger.TEN, "0xb"),
                AtletaHistoryItem("2024-01-02T03:04:05Z", null, "pending", AtletaAddress("0xto"), AtletaAddress("0xfrom"), BigInteger.TEN, "0xc")
            )
        )

        val page = AtletaHistorySource(api, "https://atleta/").operations(null)

        assertEquals("2", page.nextCursor)
        assertEquals(
            listOf(Operation.Status.COMPLETED, Operation.Status.FAILED, Operation.Status.COMPLETED),
            page.map { (it.type as Operation.Type.Transfer).status }
        )
        assertEquals(BigInteger.ONE, (page.first().type as Operation.Type.Transfer).fee)
        assertNull((page.last().type as Operation.Type.Transfer).fee)
    }

    @Test
    fun vicscanPagesByOffset(): Unit = runBlocking {
        coEvery { api.getVicscanOperationsHistory("https://vic/transaction/list", me, 20, 40) } returns VicscanHistoryResponse(
            listOf(
                vicscan("success"), vicscan("error"), vicscan("unknown")
            ),
            3
        )

        val page = VicscanHistorySource(api, "https://vic/").operations("3")

        assertEquals("4", page.nextCursor)
        assertEquals("7", page.first().id)
        assertEquals(
            listOf(Operation.Status.COMPLETED, Operation.Status.FAILED, Operation.Status.COMPLETED),
            page.map { (it.type as Operation.Type.Transfer).status }
        )
    }

    @Test
    fun zchainsMapsSuccessFlags(): Unit = runBlocking {
        coEvery { api.getZchainOperationsHistory("https://z/transaction", me, 1, 20) } returns ZchainHistoryResponse(
            listOf(zchain(true), zchain(false)),
            2
        )

        val page = ZchainsHistorySource(api, "https://z/").operations(null)

        assertEquals(listOf(Operation.Status.COMPLETED, Operation.Status.FAILED), page.map { (it.type as Operation.Type.Transfer).status })
        assertEquals("0xto", (page.first().type as Operation.Type.Transfer).receiver)
    }

    @Test
    fun everyExplorerFailureIsAnEmptyFinalPage(): Unit = runBlocking {
        coEvery { api.getFireOperationsHistory(any(), any(), any()) } throws IllegalStateException("down")
        coEvery { api.getAtletaOperationsHistory(any()) } throws IllegalStateException("down")
        coEvery { api.getVicscanOperationsHistory(any(), any(), any(), any()) } throws IllegalStateException("down")
        coEvery { api.getZchainOperationsHistory(any(), any(), any(), any()) } throws IllegalStateException("down")

        listOf(
            FireHistorySource(api, "u/"), AtletaHistorySource(api, "u/"), VicscanHistorySource(api, "u/"), ZchainsHistorySource(api, "u/")
        ).forEach { source ->
            val page = source.getOperations(20, null, emptySet(), ByteArray(20), chain, asset, me)
            assertNull(page.nextCursor)
            assertTrue(page.isEmpty())
        }
        coVerify { api.getZchainOperationsHistory("u/transaction", me, 1, 20) }
    }

    @Test
    fun providerBuildsTheSourceForEachHistoryType() {
        val provider = HistorySourceProvider(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk())
        val expected = mapOf(
            Chain.ExternalApi.Section.Type.SUBQUERY to SubqueryHistorySource::class,
            Chain.ExternalApi.Section.Type.SORA to SoraHistorySource::class,
            Chain.ExternalApi.Section.Type.SUBSQUID to SubsquidHistorySource::class,
            Chain.ExternalApi.Section.Type.GIANTSQUID to GiantsquidHistorySource::class,
            Chain.ExternalApi.Section.Type.ETHERSCAN to EtherscanHistorySource::class,
            Chain.ExternalApi.Section.Type.OKLINK to OkLinkHistorySource::class,
            Chain.ExternalApi.Section.Type.BLOCKSCOUT to BlockscoutHistorySource::class,
            Chain.ExternalApi.Section.Type.REEF to ReefHistorySource::class,
            Chain.ExternalApi.Section.Type.KLAYTN to KaiaScanHistorySource::class,
            Chain.ExternalApi.Section.Type.FIRE to FireHistorySource::class,
            Chain.ExternalApi.Section.Type.VICSCAN to VicscanHistorySource::class,
            Chain.ExternalApi.Section.Type.ZCHAINS to ZchainsHistorySource::class,
            Chain.ExternalApi.Section.Type.TON to TonHistorySource::class,
            Chain.ExternalApi.Section.Type.BITCOIN to BitcoinHistorySource::class,
            Chain.ExternalApi.Section.Type.SOLANA to SolanaHistorySource::class,
            Chain.ExternalApi.Section.Type.IROHA to IrohaHistorySource::class
        )

        expected.forEach { (type, kind) ->
            val endpoint = if (type == Chain.ExternalApi.Section.Type.OKLINK) {
                "https://www.oklink.com/api/v5/explorer/address/transaction-list"
            } else "https://h/"
            assertTrue(type.name, kind.isInstance(provider(endpoint, type)))
        }
        assertNull(provider("https://h/", Chain.ExternalApi.Section.Type.GITHUB))
        assertNull(provider("https://h/", Chain.ExternalApi.Section.Type.UNKNOWN))
    }

    private suspend fun jp.co.soramitsu.wallet.impl.data.historySource.HistorySource.operations(cursor: String?) =
        getOperations(20, cursor, emptySet(), ByteArray(20), chain, asset, me)

    private fun vicscan(status: String) = VicscanHistoryItem(
        BigInteger.valueOf(7), status, "0xh", 1_700_000_000_000, BigInteger.ONE, "0xFROM", "0xTO",
        BigInteger.TEN, BigInteger.ONE, BigInteger.ONE, BigInteger.TWO, BigInteger.ONE
    )

    private fun zchain(success: Boolean) = ZchainHistoryItem(
        success, "0xz", 1_700_000_000_000, BigInteger.ONE, ZchainHistoryAddress("0xfrom"), ZchainHistoryAddress("0xto"),
        BigInteger.TEN, BigInteger.ONE, BigInteger.ONE, BigInteger.TWO, BigInteger.ONE
    )
}
