package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.network.subquery.GiantsquidResponse
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.testshared.any
import jp.co.soramitsu.testshared.whenever
import jp.co.soramitsu.wallet.impl.data.network.model.response.GiantsquidHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.math.BigInteger
import java.time.Instant

class GiantsquidHistorySourceTest {

    @Test
    fun `includes slash bond and bond-and-nominate exactly once in deterministic order`() = runBlocking {
        val chain = mock(Chain::class.java)
        val asset = mock(Asset::class.java)
        val api = apiWith(
            response(
                transfers = listOf(transfer(id = "transfer", timestamp = "2024-01-01T00:00:02Z")),
                rewards = listOf(reward(id = "reward", timestamp = "2024-01-01T00:00:04Z")),
                slashes = listOf(
                    slash(id = "slash", timestamp = "2024-01-01T00:00:06Z"),
                    slash(id = "slash", timestamp = "2024-01-01T00:00:01Z")
                ),
                bonds = listOf(
                    bond(id = "bond", timestamp = "2024-01-01T00:00:03Z", type = "bond"),
                    bond(id = "bond", timestamp = "2024-01-01T00:00:01Z", type = "bond", amount = "999"),
                    bond(
                        id = "bond-and-nominate",
                        timestamp = "2024-01-01T00:00:05Z",
                        type = "BOND_and-NOMINATE",
                        amount = "42"
                    ),
                    bond(
                        id = "bond-and-nominate",
                        timestamp = "2024-01-01T00:00:01Z",
                        type = "bond",
                        amount = "1"
                    )
                )
            )
        )
        val source = GiantsquidHistorySource(api, HISTORY_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = TransactionFilter.values().toSet(),
            accountId = byteArrayOf(),
            chain = chain,
            chainAsset = asset,
            accountAddress = ACCOUNT
        )

        assertEquals(listOf("slash", "bond-and-nominate", "reward", "bond", "transfer"), page.items.map(Operation::id))
        assertEquals(page.items.map(Operation::id).distinct(), page.items.map(Operation::id))
        assertTrue(page.items.zipWithNext().all { (left, right) -> left.time >= right.time })
        assertTrue(page.items.all { it.address == ACCOUNT })
        assertTrue(page.items.all { it.chainAsset === asset })
        val slashOperation = page.items.single { it.id == "slash" }.type as Operation.Type.Extrinsic
        assertEquals("slash", slashOperation.module)
        assertEquals("", slashOperation.call)
        assertEquals(Operation.Status.COMPLETED, slashOperation.status)

        val bondOperation = page.items.single { it.id == "bond" }.type as Operation.Type.Extrinsic
        assertEquals("bond", bondOperation.module)
        assertEquals("10", bondOperation.call)
        assertEquals(Operation.Status.COMPLETED, bondOperation.status)

        val nominateOperation = page.items.single { it.id == "bond-and-nominate" }.type as Operation.Type.Extrinsic
        assertEquals("bondAndNominate", nominateOperation.module)
        assertEquals("42", nominateOperation.call)
        assertEquals(Operation.Status.COMPLETED, nominateOperation.status)
    }

    @Test
    fun `deduplicates globally by id using newest canonical record independent of response order`() = runBlocking {
        val asset = mock(Asset::class.java)
        val chain = mock(Chain::class.java)
        val newest = transfer(id = "duplicate", timestamp = "2024-01-01T00:00:09Z", amount = "9")
        val oldest = transfer(id = "duplicate", timestamp = "2024-01-01T00:00:01Z", amount = "1")
        val duplicateReward = reward(id = "duplicate", timestamp = "2024-01-01T00:00:09Z", amount = "999")
        val duplicateBondOld = bond(id = "bond-duplicate", timestamp = "malformed", amount = "1")
        val duplicateBondNew = bond(id = "bond-duplicate", timestamp = "2024-01-01T00:00:08Z", amount = "8")

        suspend fun load(
            transfers: List<GiantsquidHistoryResponse.GiantsquidTransferResponse>,
            bonds: List<GiantsquidHistoryResponse.GiantsquidBond>
        ): List<Operation> {
            return GiantsquidHistorySource(
                apiWith(
                    response(
                        transfers = transfers,
                        rewards = listOf(duplicateReward),
                        bonds = bonds,
                        slashes = listOf(slash(id = "", timestamp = "2024-01-01T00:00:10Z"))
                    )
                ),
                HISTORY_URL
            ).getOperations(
                25,
                null,
                TransactionFilter.values().toSet(),
                byteArrayOf(),
                chain,
                asset,
                ACCOUNT
            ).items
        }

        val first = load(listOf(oldest, newest), listOf(duplicateBondOld, duplicateBondNew))
        val second = load(listOf(newest, oldest), listOf(duplicateBondNew, duplicateBondOld))

        assertEquals(first, second)
        assertEquals(listOf("duplicate", "bond-duplicate"), first.map(Operation::id))
        assertEquals(BigInteger.valueOf(9), (first[0].type as Operation.Type.Transfer).amount)
        assertEquals("8", (first[1].type as Operation.Type.Extrinsic).call)
        assertFalse(first.any { it.id.isBlank() })
    }

    @Test
    fun `honors filters and fails malformed amounts timestamps and nullable success closed`() = runBlocking {
        val source = GiantsquidHistorySource(
            apiWith(
                response(
                    transfers = listOf(transfer()),
                    rewards = listOf(reward()),
                    bonds = listOf(
                        bond(
                            id = "malformed-bond",
                            timestamp = "not-a-timestamp",
                            amount = "9".repeat(10_000),
                            success = null,
                            type = "BOND_AND_NOMINATE".repeat(1_000)
                        )
                    ),
                    slashes = listOf(slash(id = "malformed-slash", timestamp = "not-a-timestamp"))
                )
            ),
            HISTORY_URL
        )

        val page = source.getOperations(
            25,
            null,
            setOf(TransactionFilter.EXTRINSIC),
            byteArrayOf(),
            mock(Chain::class.java),
            mock(Asset::class.java),
            ACCOUNT
        )

        assertEquals(listOf("malformed-bond", "malformed-slash"), page.items.map(Operation::id))
        assertTrue(page.items.all { it.time == 0L })
        val malformedBond = page.items.first().type as Operation.Type.Extrinsic
        assertEquals("bond", malformedBond.module)
        assertEquals("", malformedBond.call)
        assertEquals(Operation.Status.FAILED, malformedBond.status)
        assertTrue(page.items.none { it.type is Operation.Type.Transfer || it.type is Operation.Type.Reward })
    }

    @Test
    fun `orders strict timestamps across offsets and fractions and fails impossible dates closed`() = runBlocking {
        val source = GiantsquidHistorySource(
            apiWith(
                response(
                    bonds = listOf(
                        bond(id = "nanoseconds-offset", timestamp = "2024-01-01T01:00:00.987654321+01:00"),
                        bond(id = "milliseconds-z", timestamp = "2024-01-01T00:00:00.988Z"),
                        bond(id = "tenths-z", timestamp = "2024-01-01T00:00:00.9Z"),
                        bond(id = "impossible-date", timestamp = "2024-02-30T00:00:01Z")
                    )
                )
            ),
            HISTORY_URL
        )

        val page = source.getOperations(
            25,
            null,
            setOf(TransactionFilter.EXTRINSIC),
            byteArrayOf(),
            mock(Chain::class.java),
            mock(Asset::class.java),
            ACCOUNT
        )

        assertEquals(
            listOf("milliseconds-z", "nanoseconds-offset", "tenths-z", "impossible-date"),
            page.items.map(Operation::id)
        )
        assertEquals(Instant.parse("2024-01-01T00:00:00.988Z").toEpochMilli(), page.items[0].time)
        assertEquals(Instant.parse("2024-01-01T00:00:00.987Z").toEpochMilli(), page.items[1].time)
        assertEquals(Instant.parse("2024-01-01T00:00:00.900Z").toEpochMilli(), page.items[2].time)
        assertEquals(0L, page.items[3].time)
    }

    @Test
    fun `advances a bounded offset only when a selected root fills the page`() = runBlocking {
        val chain = mock(Chain::class.java)
        val asset = mock(Asset::class.java)
        val fullTransfers = List(100) { index ->
            transfer(id = "transfer-$index", timestamp = "2024-01-01T00:00:01Z")
        }
        val selectedFullPage = GiantsquidHistorySource(
            apiWith(response(transfers = fullTransfers)),
            HISTORY_URL
        ).getOperations(
            100,
            "100",
            setOf(TransactionFilter.TRANSFER),
            byteArrayOf(),
            chain,
            asset,
            ACCOUNT
        )
        assertEquals("200", selectedFullPage.nextCursor)
        assertEquals(100, selectedFullPage.items.size)

        val unselectedFullPage = GiantsquidHistorySource(
            apiWith(
                response(
                    transfers = fullTransfers.dropLast(1),
                    rewards = List(100) { index -> reward(id = "reward-$index") }
                )
            ),
            HISTORY_URL
        ).getOperations(
            100,
            null,
            setOf(TransactionFilter.TRANSFER),
            byteArrayOf(),
            chain,
            asset,
            ACCOUNT
        )
        assertEquals(null, unselectedFullPage.nextCursor)
        assertEquals(99, unselectedFullPage.items.size)
    }

    @Test
    fun `rejects empty requests and malformed cursors without network calls`() = runBlocking {
        val api = mock(OperationsHistoryApi::class.java)
        val source = GiantsquidHistorySource(api, HISTORY_URL)
        val chain = mock(Chain::class.java)
        val asset = mock(Asset::class.java)

        val pages = listOf(
            source.getOperations(0, null, setOf(TransactionFilter.TRANSFER), byteArrayOf(), chain, asset, ACCOUNT),
            source.getOperations(-1, null, setOf(TransactionFilter.TRANSFER), byteArrayOf(), chain, asset, ACCOUNT),
            source.getOperations(101, null, setOf(TransactionFilter.TRANSFER), byteArrayOf(), chain, asset, ACCOUNT),
            source.getOperations(25, null, emptySet(), byteArrayOf(), chain, asset, ACCOUNT),
            source.getOperations(25, "-1", setOf(TransactionFilter.TRANSFER), byteArrayOf(), chain, asset, ACCOUNT),
            source.getOperations(25, "not-an-offset", setOf(TransactionFilter.TRANSFER), byteArrayOf(), chain, asset, ACCOUNT),
            source.getOperations(25, "2147483648", setOf(TransactionFilter.TRANSFER), byteArrayOf(), chain, asset, ACCOUNT)
        )

        assertTrue(pages.all { it.items.isEmpty() && it.nextCursor == null })
        verifyNoInteractions(api)
    }

    private suspend fun apiWith(response: GiantsquidResponse<GiantsquidHistoryResponse>): OperationsHistoryApi {
        val api = mock(OperationsHistoryApi::class.java)
        whenever(api.getGiantsquidOperationsHistory(any(), any())).thenReturn(response)
        return api
    }

    private fun response(
        transfers: List<GiantsquidHistoryResponse.GiantsquidTransferResponse> = emptyList(),
        rewards: List<GiantsquidHistoryResponse.GiantsquidReward> = emptyList(),
        bonds: List<GiantsquidHistoryResponse.GiantsquidBond> = emptyList(),
        slashes: List<GiantsquidHistoryResponse.GiantsquidSlash> = emptyList()
    ) = GiantsquidResponse(GiantsquidHistoryResponse(transfers, rewards, bonds, slashes))

    private fun transfer(
        id: String = "transfer",
        timestamp: String = "2024-01-01T00:00:01Z",
        amount: String = "1"
    ) = GiantsquidHistoryResponse.GiantsquidTransferResponse(
        id,
        GiantsquidHistoryResponse.GiantsquidTransfer(
            id = "$id-inner",
            blockNumber = BigInteger.ONE,
            timestamp = timestamp,
            extrinsicHash = "0x$id",
            from = GiantsquidHistoryResponse.GiantsquidAccount(ACCOUNT),
            to = GiantsquidHistoryResponse.GiantsquidAccount(OTHER_ACCOUNT),
            amount = amount,
            success = true
        )
    )

    private fun reward(
        id: String = "reward",
        timestamp: String = "2024-01-01T00:00:02Z",
        amount: String = "2"
    ) = GiantsquidHistoryResponse.GiantsquidReward(
        id = id,
        timestamp = timestamp,
        blockNumber = 2,
        extrinsicHash = "0x$id",
        amount = amount,
        era = BigInteger.TWO,
        validatorId = "validator",
        account = GiantsquidHistoryResponse.GiantsquidAccount(ACCOUNT)
    )

    private fun bond(
        id: String = "bond",
        timestamp: String = "2024-01-01T00:00:03Z",
        amount: String = "10",
        success: Boolean? = true,
        type: String? = "bond"
    ) = GiantsquidHistoryResponse.GiantsquidBond(
        id = id,
        accountId = ACCOUNT,
        amount = amount,
        blockNumber = BigInteger.valueOf(3),
        extrinsicHash = "0x$id",
        success = success,
        timestamp = timestamp,
        type = type
    )

    private fun slash(id: String = "slash", timestamp: String = "2024-01-01T00:00:04Z") =
        GiantsquidHistoryResponse.GiantsquidSlash(
            id = id,
            accountId = ACCOUNT,
            amount = "4",
            blockNumber = BigInteger.valueOf(4),
            era = BigInteger.valueOf(4),
            timestamp = timestamp
        )

    private companion object {
        const val HISTORY_URL = "https://history.example/graphql"
        const val ACCOUNT = "account"
        const val OTHER_ACCOUNT = "other"
    }
}
