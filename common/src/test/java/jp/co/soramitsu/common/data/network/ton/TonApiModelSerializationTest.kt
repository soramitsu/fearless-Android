package jp.co.soramitsu.common.data.network.ton

import com.google.gson.Gson
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TonApiModelSerializationTest {
    private val gson = Gson()
    private val account = AccountAddress("0:account", false, true, "Alice", "file:///avatar.png")
    private val recipient = AccountAddress("0:recipient", false, true)
    private val jetton = JettonPreview("master", "Example token", "JET", 9, "file:///token.png", "whitelist")

    @Test
    fun shouldRoundTripAllTransactionPhasesAndMessageBodies() {
        val message = Message("int_msg", 123, true, false, false, 1000, 2, 3, 4, 1700, "message-hash", recipient, account,
            "0x1234", StateInit("boc", listOf("wallet_v4")), "raw", "transfer", mapOf("text" to "hello"))
        val transaction = Transaction("hash", 321, account, true, 1700, AccountStatus.active, AccountStatus.frozen,
            BigInteger("999999999999999999999999"), 4000, "TransOrd", "old", "new", listOf(message), "block", false, false,
            "raw-boc", message, "previous", 320, ComputePhase(false, "none", true, 5, 6, 7, 0, "ok"),
            StoragePhase(8, "unchanged", 9), CreditPhase(10, 11), ActionPhase(true, 0, 2, 0, 12, 13, "success"), "ok")
        val wire = gson.toJsonTree(transaction).asJsonObject
        assertEquals("999999999999999999999999", wire["total_fees"].asString)
        assertEquals("old", wire["state_update_old"].asString)
        assertEquals("transfer", wire["in_msg"].asJsonObject["decoded_op_name"].asString)
        assertEquals(transaction, gson.fromJson(wire, Transaction::class.java))
    }

    @Test
    fun shouldKeepOptionalPhaseAndMessageFieldsAbsent() {
        val message = Message("ext_in_msg", 1, false, true, false, 0, 0, 0, 0, 1700)
        val phase = ComputePhase(true, "no_state")
        val wire = gson.toJsonTree(message).asJsonObject
        assertFalse(wire.has("decoded_body"))
        assertFalse(wire.has("init"))
        assertEquals(message, gson.fromJson(wire, Message::class.java))
        assertEquals(phase, gson.fromJson(gson.toJson(phase), ComputePhase::class.java))
        assertEquals(StateInit("boc"), gson.fromJson("{\"boc\":\"boc\"}", StateInit::class.java))
    }

    @Test
    fun shouldPreserveTonAndJettonEventActionsAndClassifyEveryType() {
        val encrypted = EncryptedComment("simple", "ciphertext")
        val refund = Refund("bounce", "original")
        val transfer = TonTransferAction(account, recipient, 12345, "memo", encrypted, refund)
        val jettonTransfer = JettonTransferAction("sender-wallet", "recipient-wallet", "99999999999999999999", jetton,
            account, recipient, "token memo", encrypted, refund)
        val preview = ActionSimplePreview("Transfer", "Sent TON", listOf(account, recipient), "action.png", "1 TON", "ton.png")
        val action = AccountEventAction(AccountEventAction.Type.tonTransfer, AccountEventAction.Status.ok, preview,
            listOf("hash"), transfer, jettonTransfer)
        val event = AccountEvent("event", account, 1700, listOf(action), false, 123, true, -10)
        val events = AccountEvents(listOf(event))
        val wire = gson.toJsonTree(events).asJsonObject
        assertTrue(wire["events"].asJsonArray[0].asJsonObject["in_progress"].asBoolean)
        assertEquals(events, gson.fromJson(wire, AccountEvents::class.java))
        AccountEventAction.Type.entries.forEach { type ->
            val variant = action.copy(type = type, status = AccountEventAction.Status.failed, tonTransfer = null, jettonTransfer = null)
            assertEquals(type.name.startsWith("jetton"), variant.isJetton())
            assertEquals(type.value, gson.toJsonTree(variant).asJsonObject["type"].asString)
            assertEquals(variant, gson.fromJson(gson.toJson(variant), AccountEventAction::class.java))
        }
    }

    @Test
    fun shouldKeepJettonPrecisionPriceHistoryAndLocalPartialFlag() {
        val rates = TokenRates(mapOf("USD" to BigDecimal("0.123456789")), mapOf("USD" to "+1%"),
            mapOf("USD" to "-2%"), mapOf("USD" to "+3%"))
        val balance = JettonBalance("123456789012345678901", account, jetton, rates, listOf("custom_payload"), JettonBalanceLock("100", 1900))
        val balances = JettonsBalances(listOf(balance), partial = true)
        val wire = gson.toJsonTree(balances).asJsonObject
        assertFalse(wire.has("partial"))
        val decoded = gson.fromJson(wire, JettonsBalances::class.java)
        assertFalse(decoded.partial)
        assertEquals(balances.balances, decoded.balances)
        assertEquals("123456789012345678901", decoded.balances.single().balance)
        assertEquals(BigDecimal("0.123456789"), decoded.balances.single().price?.prices?.get("USD"))
    }

    @Test
    fun shouldSerializeAccountStatusesAndOptionalSafetyFlags() {
        AccountStatus.entries.forEach { status ->
            val data = TonAccountData("account", 123, 1700, status, listOf("seqno"), true,
                mapOf("USD" to "5.00"), listOf("wallet_v4"), "Alice", false, "avatar.png", true, false)
            val wire = gson.toJsonTree(data).asJsonObject
            assertEquals(status.value, wire["status"].asString)
            assertTrue(wire["memo_required"].asBoolean)
            assertEquals(data, gson.fromJson(wire, TonAccountData::class.java))
        }
    }
}
