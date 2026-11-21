package jp.co.soramitsu.wallet.impl.data.historySource

import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.subquery.GiantsquidResponse
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.model.response.GiantsquidHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Records that share an id and a timestamp are resolved by operation type and then by their canonical content, so the
 * kept record never depends on the order the indexer returned them in.
 */
class GiantsquidHistorySourceTieBreakCoverageTest {

    private val asset = WalletTestModels.asset()
    private val chain = WalletTestModels.chain(assets = listOf(asset))

    @Test
    fun `a transfer wins a tie over rewards and extrinsics and the smaller transfer is kept`() = runBlocking {
        val transfers = listOf(transfer(amount = "7"), transfer(amount = "5"))
        val rewards = listOf(reward(amount = "3"), reward(amount = "1"))
        val bonds = listOf(bond())
        val slashes = listOf(slash())

        val forward = load(response(transfers, rewards, bonds, slashes), TransactionFilter.entries.toSet())
        val reversed = load(
            response(transfers.reversed(), rewards.reversed(), bonds, slashes),
            TransactionFilter.entries.toSet()
        )

        assertEquals(forward, reversed)
        val kept = forward.single().type as Operation.Type.Transfer
        assertEquals(BigInteger.valueOf(5), kept.amount)
    }

    @Test
    fun `a reward wins a tie over an extrinsic and the smaller reward is kept`() = runBlocking {
        val rewards = listOf(reward(amount = "3"), reward(amount = "1"))
        val filters = setOf(TransactionFilter.REWARD, TransactionFilter.EXTRINSIC)

        val forward = load(response(rewards = rewards, bonds = listOf(bond()), slashes = listOf(slash())), filters)
        val reversed = load(response(rewards = rewards.reversed(), bonds = listOf(bond()), slashes = listOf(slash())), filters)

        assertEquals(forward, reversed)
        val kept = forward.single().type as Operation.Type.Reward
        assertEquals(BigInteger.ONE, kept.amount)
    }

    @Test
    fun `between tied extrinsics the slash with an empty hash is kept`() = runBlocking {
        val filters = setOf(TransactionFilter.EXTRINSIC)

        val withBond = load(response(bonds = listOf(bond()), slashes = listOf(slash())), filters)
        val withNomination = load(
            response(bonds = listOf(bond(type = "bond_and_nominate")), slashes = listOf(slash())),
            filters
        )

        assertEquals("slash", (withBond.single().type as Operation.Type.Extrinsic).module)
        assertEquals("slash", (withNomination.single().type as Operation.Type.Extrinsic).module)
    }

    private suspend fun load(
        response: GiantsquidResponse<GiantsquidHistoryResponse>,
        filters: Set<TransactionFilter>
    ): List<Operation> {
        val api = mockk<OperationsHistoryApi>()
        coEvery { api.getGiantsquidOperationsHistory(any(), any()) } returns response
        return GiantsquidHistorySource(api, HISTORY_URL)
            .getOperations(25, null, filters, byteArrayOf(), chain, asset, ACCOUNT)
            .items
    }

    private fun response(
        transfers: List<GiantsquidHistoryResponse.GiantsquidTransferResponse> = emptyList(),
        rewards: List<GiantsquidHistoryResponse.GiantsquidReward> = emptyList(),
        bonds: List<GiantsquidHistoryResponse.GiantsquidBond> = emptyList(),
        slashes: List<GiantsquidHistoryResponse.GiantsquidSlash> = emptyList()
    ) = GiantsquidResponse(GiantsquidHistoryResponse(transfers, rewards, bonds, slashes))

    private fun transfer(amount: String) = GiantsquidHistoryResponse.GiantsquidTransferResponse(
        ID,
        GiantsquidHistoryResponse.GiantsquidTransfer(
            id = "$ID-$amount",
            blockNumber = BigInteger.ONE,
            timestamp = TIMESTAMP,
            extrinsicHash = "0x$ID",
            from = GiantsquidHistoryResponse.GiantsquidAccount(ACCOUNT),
            to = GiantsquidHistoryResponse.GiantsquidAccount(OTHER_ACCOUNT),
            amount = amount,
            success = true
        )
    )

    private fun reward(amount: String) = GiantsquidHistoryResponse.GiantsquidReward(
        id = ID,
        timestamp = TIMESTAMP,
        blockNumber = 2,
        extrinsicHash = "0x$ID",
        amount = amount,
        era = BigInteger.TWO,
        validatorId = "validator",
        account = GiantsquidHistoryResponse.GiantsquidAccount(ACCOUNT)
    )

    private fun bond(type: String = "bond") = GiantsquidHistoryResponse.GiantsquidBond(
        id = ID,
        accountId = ACCOUNT,
        amount = "10",
        blockNumber = BigInteger.valueOf(3),
        extrinsicHash = "0x$ID",
        success = true,
        timestamp = TIMESTAMP,
        type = type
    )

    private fun slash() = GiantsquidHistoryResponse.GiantsquidSlash(
        id = ID,
        accountId = ACCOUNT,
        amount = "4",
        blockNumber = BigInteger.valueOf(4),
        era = BigInteger.valueOf(4),
        timestamp = TIMESTAMP
    )

    private companion object {
        const val HISTORY_URL = "https://history.example/graphql"
        const val ACCOUNT = "account"
        const val OTHER_ACCOUNT = "other"
        const val ID = "same"
        const val TIMESTAMP = "2024-01-01T00:00:01Z"
    }
}
