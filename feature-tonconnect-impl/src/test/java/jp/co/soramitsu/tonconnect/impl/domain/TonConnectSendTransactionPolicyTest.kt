package jp.co.soramitsu.tonconnect.impl.domain

import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.TonConnectRawMessage
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.block.AddrStd
import org.ton.boc.BagOfCells
import org.ton.cell.buildCell
import java.util.Base64

class TonConnectSendTransactionPolicyTest {

    @Test
    fun shouldCapExpiryAtFiveMinutes_whenTheDappSetsNone() {
        assertEquals(NOW + 300, validUntil(request()))
    }

    @Test
    fun shouldKeepTheDappExpiry_whenItEndsWithinFiveMinutes() {
        assertEquals(NOW + 60, validUntil(request(validUntil = NOW + 60)))
    }

    @Test
    fun shouldCapTheDappExpiry_whenItEndsLater() {
        assertEquals(NOW + 300, validUntil(request(validUntil = NOW + 3_600)))
        assertEquals(NOW + 300, validUntil(request(validUntil = Long.MAX_VALUE)))
    }

    @Test
    fun shouldReject_whenTheExpiryHasPassed() {
        assertRejected(request(validUntil = NOW - 1))
        assertRejected(request(validUntil = NOW))
        assertRejected(request(validUntil = INVALID_EXPIRY))
    }

    @Test
    fun shouldAcceptMainnet_whenTheNetworkIsAbsentOrMainnet() {
        listOf(null, "", "-239", " -239 ").forEach { network ->
            assertEquals(NOW + 300, validUntil(request(network = network)))
        }
    }

    @Test
    fun shouldReject_whenTheNetworkIsNotMainnet() {
        listOf("-3", "239", "mainnet", "-239.0", "0").forEach { network ->
            assertRejected(request(network = network))
        }
    }

    @Test
    fun shouldSignForTheConnectedWallet_whenFromIsAbsent() {
        listOf(null, "", " ").forEach { from ->
            assertTrue(TonConnectSendTransactionPolicy.isFromWallet(from, WALLET))
        }
    }

    @Test
    fun shouldAcceptEveryFormOfTheConnectedWallet_whenFromIsSet() {
        val forms = listOf(
            WALLET.toString(userFriendly = false),
            WALLET.toString(userFriendly = false).uppercase(),
            WALLET.toString(userFriendly = true, bounceable = true),
            WALLET.toString(userFriendly = true, bounceable = false),
            WALLET.toString(userFriendly = true, testOnly = true),
            WALLET.toString(userFriendly = true, urlSafe = false)
        )

        forms.forEach { from ->
            assertTrue(from, TonConnectSendTransactionPolicy.isFromWallet(from, WALLET))
            assertEquals(NOW + 300, validUntil(request(from = from)))
        }
    }

    @Test
    fun shouldReject_whenFromIsAnotherAccount() {
        val otherAccount = AddrStd(0, ByteArray(32) { 7 })
        val sameHashOnMasterchain = AddrStd(-1, WALLET_HASH)

        listOf(
            otherAccount.toString(userFriendly = false),
            otherAccount.toString(userFriendly = true),
            sameHashOnMasterchain.toString(userFriendly = false),
            sameHashOnMasterchain.toString(userFriendly = true),
            "not an address"
        ).forEach { from ->
            assertFalse(from, TonConnectSendTransactionPolicy.isFromWallet(from, WALLET))
            assertRejected(request(from = from))
        }
    }

    @Test
    fun shouldReject_whenTheMessageCountIsOutOfRange() {
        assertRejected(request(messages = emptyList()))
        assertRejected(request(messages = List(5) { message() }))
        assertEquals(NOW + 300, validUntil(request(messages = List(4) { message() })))
    }

    @Test
    fun shouldReject_whenAMessageCannotBeSignedAsRequested() {
        assertRejected(request(messages = listOf(message(amount = -1))))
        assertRejected(request(messages = listOf(message(address = "EQ-not-an-address"))))
        assertRejected(request(messages = listOf(message(payload = "not a bag of cells"))))
        assertRejected(request(messages = listOf(message(payload = TWO_ROOT_BOC))))
        assertRejected(request(messages = listOf(message(stateInit = "not a bag of cells"))))
        assertRejected(request(messages = listOf(message(stateInit = TRUNCATED_STATE_INIT))))
    }

    @Test
    fun shouldAccept_whenEveryMessagePartParses() {
        val messages = listOf(
            message(amount = 0),
            message(payload = COMMENT_PAYLOAD),
            message(payload = "", stateInit = "")
        )

        assertEquals(NOW + 300, validUntil(request(messages = messages)))
    }

    @Test
    fun shouldRejectWithBadRequest_soTheDappIsTold() {
        val error = assertThrows(BridgeError.Exception::class.java) {
            validUntil(request(network = "-3"))
        }

        assertEquals(BridgeError.BAD_REQUEST, error.error)
    }

    private fun validUntil(request: TonConnectSignRequest): Long {
        return TonConnectSendTransactionPolicy.validUntilToSign(
            request = request,
            wallet = WALLET,
            nowSeconds = NOW,
            maxMessages = MAX_MESSAGES
        )
    }

    private fun assertRejected(request: TonConnectSignRequest) {
        assertThrows(BridgeError.Exception::class.java) { validUntil(request) }
    }

    private fun request(
        from: String? = null,
        validUntil: Long? = null,
        network: String? = null,
        messages: List<TonConnectRawMessage> = listOf(message())
    ) = TonConnectSignRequest(
        fromValue = from,
        validUntil = validUntil,
        messages = messages,
        networkValue = network
    )

    private fun message(
        address: String = RECIPIENT.toString(userFriendly = true),
        amount: Long = 1_000_000,
        stateInit: String? = null,
        payload: String? = null
    ) = TonConnectRawMessage(address, amount, stateInit, payload)

    private companion object {
        const val NOW = 1_800_000_000L
        const val MAX_MESSAGES = 4

        // What TonConnectSignRequest stores for a valid_until it cannot read.
        const val INVALID_EXPIRY = 0L
        val WALLET_HASH = ByteArray(32) { (it + 1).toByte() }
        val WALLET = AddrStd(0, WALLET_HASH)
        val RECIPIENT = AddrStd(0, ByteArray(32) { 3 })
        val COMMENT_PAYLOAD: String = Base64.getEncoder().encodeToString(
            BagOfCells(
                buildCell {
                    storeUInt(0, 32)
                    storeBytes("hello".toByteArray())
                }
            ).toByteArray()
        )
        val TRUNCATED_STATE_INIT: String = Base64.getEncoder().encodeToString(
            BagOfCells(buildCell { storeBit(true) }).toByteArray()
        )
        val TWO_ROOT_BOC: String = Base64.getEncoder().encodeToString(
            BagOfCells(
                buildCell { storeUInt(1, 32) },
                buildCell { storeUInt(2, 32) }
            ).toByteArray()
        )
    }
}
