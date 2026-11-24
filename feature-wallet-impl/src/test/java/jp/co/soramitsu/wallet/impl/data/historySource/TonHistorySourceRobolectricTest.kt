package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.ton.AccountEvents
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.WalletTestModels.TON_RAW
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
class TonHistorySourceRobolectricTest {

    private val remote = mockk<TonRemoteSource>()
    private val ton = WalletTestModels.asset(id = "ton", chainId = "-239", precision = 9, type = ChainAssetType.Normal)
    private val usdt = WalletTestModels.asset(id = JETTON, chainId = "-239", precision = 6, type = ChainAssetType.Jetton, isUtility = false)
    private val chain = WalletTestModels.chain(id = "-239", assets = listOf(ton, usdt), ecosystem = Ecosystem.Ton)
    private val publicKey = ByteArray(32) { 7 }
    private val source = TonHistorySource(remote, "https://tonapi/")

    @Test
    fun nativeHistoryMapsTransfersAndOtherActionsWithFeesAndCursor(): Unit = runBlocking {
        coEvery { remote.getAccountEvents(chain, "https://tonapi/", any(), null, null, 20) } returns events(EVENTS)

        val page = source.getOperations(20, null, TransactionFilter.entries.toSet(), publicKey, chain, ton, "UQme")

        assertEquals("100:abc", page.nextCursor)
        val transfer = page.first { it.type is Operation.Type.Transfer }
        assertEquals("200:def:0", transfer.id)
        assertEquals(BigInteger.valueOf(1_000_000_000), (transfer.type as Operation.Type.Transfer).amount)
        assertEquals(BigInteger.valueOf(5), (transfer.type as Operation.Type.Transfer).fee)
        assertEquals(Operation.Status.COMPLETED, (transfer.type as Operation.Type.Transfer).status)
        val extrinsic = page.first { it.type is Operation.Type.Extrinsic }.type as Operation.Type.Extrinsic
        assertEquals("ContractDeploy", extrinsic.call)
        assertEquals(Operation.Status.PENDING, extrinsic.status)
        assertTrue(page.none { it.id.startsWith("100:abc") })
    }

    @Test
    fun jettonHistoryKeepsOnlyThatJettonsTransfers(): Unit = runBlocking {
        coEvery { remote.getAccountEvents(chain, any(), any(), 300L, "hash", 10) } returns events(EVENTS)

        val page = source.getOperations(10, " 300:hash ", setOf(TransactionFilter.TRANSFER), publicKey, chain, usdt, "UQme")

        val transfer = page.single().type as Operation.Type.Transfer
        assertEquals(BigInteger.valueOf(2_500_000), transfer.amount)
        assertEquals(Operation.Status.FAILED, transfer.status)
        assertTrue(transfer.receiver.isNotEmpty())
    }

    @Test
    fun cursorsWithoutAHashUseTheLogicalTime(): Unit = runBlocking {
        coEvery { remote.getAccountEvents(any(), any(), any(), any(), any(), any()) } returns events("""{"events": []}""")

        val empty = source.getOperations(10, "400", setOf(TransactionFilter.EXTRINSIC), publicKey, chain, ton, "UQme")
        source.getOperations(10, "500:", setOf(TransactionFilter.EXTRINSIC), publicKey, chain, ton, "UQme")

        assertNull(empty.nextCursor)
        coVerify { remote.getAccountEvents(chain, any(), any(), 400L, null, 10) }
        coVerify { remote.getAccountEvents(chain, any(), any(), null, null, 10) }
    }

    @Test
    fun oldestEventWithoutAHashedIdPagesByItsLt(): Unit = runBlocking {
        coEvery { remote.getAccountEvents(any(), any(), any(), any(), any(), any()) } returns events(
            """{"events": [{"event_id": "plain", "timestamp": 10, "lt": 77, "in_progress": false, "extra": 3, "is_scam": false,
              "account": {"address": "$TON_RAW", "is_scam": false, "is_wallet": true},
              "actions": [{"type": "ContractDeploy", "status": "ok", "base_transactions": []}]}]}"""
        )

        val withoutExtrinsics = source.getOperations(10, null, setOf(TransactionFilter.TRANSFER), publicKey, chain, ton, "UQme")

        assertEquals("77", withoutExtrinsics.nextCursor)
        assertTrue(withoutExtrinsics.isEmpty())
    }

    private fun events(json: String): AccountEvents = Gson().fromJson(json, AccountEvents::class.java)

    private companion object {
        const val JETTON = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        const val ADDRESS = """{"address": "$TON_RAW", "is_scam": false, "is_wallet": true}"""
        const val EVENTS = """{"events": [
          {"event_id": "200:def", "timestamp": 200, "lt": 200, "in_progress": false, "extra": -5, "is_scam": false, "account": $ADDRESS,
           "actions": [
             {"type": "TonTransfer", "status": "ok", "base_transactions": [], "TonTransfer": {"sender": $ADDRESS, "recipient": $ADDRESS, "amount": 1000000000}},
             {"type": "ContractDeploy", "status": "unknown_status_value", "base_transactions": []}
           ]},
          {"event_id": "100:abc", "timestamp": 100, "lt": 100, "in_progress": true, "extra": 4, "is_scam": false, "account": $ADDRESS,
           "actions": [
             {"type": "JettonTransfer", "status": "failed", "base_transactions": [],
              "JettonTransfer": {"senders_wallet": "w1", "recipients_wallet": "w2", "amount": "2500000",
                "jetton": {"address": "$JETTON", "name": "Tether", "symbol": "USDT", "decimals": 6},
                "sender": $ADDRESS, "recipient": $ADDRESS}}
           ]}
        ]}"""
    }
}
