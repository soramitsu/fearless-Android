package jp.co.soramitsu.tonconnect.impl.domain

import jp.co.soramitsu.tonconnect.api.model.TonConnectRawMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.tlb.storeTlb
import java.math.BigInteger
import java.util.Base64

class TonConnectMessageSummaryTest {
    @Test
    fun `native send keeps requested address bounce flag and nanotons`() {
        val message = TonConnectRawMessage(DESTINATION.toString(userFriendly = true, bounceable = false), 1_000_000_001L, null, null)
        val summary = TonConnectMessageSummaries.summary(message)
        assertEquals(DESTINATION.toString(userFriendly = true, urlSafe = true, testOnly = false, bounceable = false), summary.destination)
        assertEquals(BigInteger("1000000001"), summary.amount)
        assertEquals(TonConnectPayloadSummary.Empty, summary.payload)
        assertFalse(summary.deploysContract)
    }

    @Test
    fun `jetton inline comment decodes requested amount recipient and excess return`() {
        val body = jetton(comment("payment"), referenced = false)
        val summary = summary(body)
        val transfer = summary.payload as TonConnectPayloadSummary.JettonTransfer
        assertEquals(BigInteger("1234567890123456789"), transfer.amount)
        assertEquals(RECIPIENT, transfer.recipient)
        assertEquals(RESPONSE, transfer.responseDestination)
        assertEquals(BigInteger.ONE, transfer.forwardTonAmount)
        assertEquals(TonConnectPayloadSummary.ForwardPayload.Comment("payment"), transfer.forwardPayload)
        assertTrue(transfer.isComplete)
        val request = message(body)
        assertEquals(request.payload.hash(), TonConnectTransfers.transfer(request, true).messageData.body.hash())
    }

    @Test
    fun `referenced jetton forward comment and no response address are understood`() {
        val transfer = summary(jetton(comment("hello"), true, response = false)).payload as TonConnectPayloadSummary.JettonTransfer
        assertNull(transfer.responseDestination)
        assertEquals(TonConnectPayloadSummary.ForwardPayload.Comment("hello"), transfer.forwardPayload)
        assertTrue(transfer.isComplete)
    }

    @Test
    fun `custom jetton instructions and contract forwarding remain incomplete`() {
        val custom = summary(jetton(Cell(), false, custom = true)).payload as TonConnectPayloadSummary.JettonTransfer
        assertTrue(custom.hasCustomPayload)
        assertFalse(custom.isComplete)
        val unknown = summary(
            jetton(
                buildCell {
            storeUInt(0x12345678, 32)
        },
            true
            )
        ).payload as TonConnectPayloadSummary.JettonTransfer
        assertEquals(TonConnectPayloadSummary.ForwardPayload.Undecoded, unknown.forwardPayload)
        assertFalse(unknown.isComplete)
    }

    @Test
    fun `NFT transfer shows new owner and cannot hide custom data`() {
        for (custom in listOf(false, true)) {
            val body = buildCell {
                storeUInt(0x5fcc3d14, 32)
                storeUInt(0, 64)
                storeTlb(MsgAddress, RECIPIENT)
                storeTlb(MsgAddress, AddrNone)
                storeBit(custom)
                if (custom) storeRef(comment("custom"))
                storeTlb(Coins, Coins.ofNano(0))
                storeBit(false)
            }
            val transfer = summary(body).payload as TonConnectPayloadSummary.NftTransfer
            assertEquals(RECIPIENT, transfer.newOwner)
            assertNull(transfer.responseDestination)
            assertEquals(!custom, transfer.isComplete)
            assertEquals(TonConnectPayloadSummary.ForwardPayload.Empty, transfer.forwardPayload)
        }
    }

    @Test
    fun `snake comments join referenced bytes without truncating`() {
        val body = buildCell {
            storeUInt(0, 32)
            storeBytes("hello ".toByteArray())
            storeRef(buildCell { storeBytes("世界".toByteArray()) })
        }
        assertEquals(TonConnectPayloadSummary.Comment("hello 世界"), summary(body).payload)
    }

    @Test
    fun `malformed snake or token bodies remain raw`() {
        val malformed = listOf(
            buildCell { storeUInt(1, 8) },
            buildCell { storeUInt(0xf8a7ea5, 32) },
            buildCell {
                storeUInt(0, 32)
            storeBit(true)
            },
            buildCell {
                storeUInt(0, 32)
            storeRef(Cell())
            storeRef(Cell())
            },
            comment("pay\u202e100"),
            buildCell {
                storeUInt(0, 32)
            storeBytes(byteArrayOf(0xc3.toByte(), 0x28))
            }
        )
        malformed.forEach { assertFalse(summary(it).payload.isComplete) }
    }

    @Test
    fun `extra data after referenced forward payload remains raw`() {
        val body = jetton(comment("hello"), referenced = true)
        val withExtra = buildCell {
            storeBits(body.bits)
            body.refs.forEach { storeRef(it) }
            storeBit(false)
        }
        assertFalse(summary(withExtra).payload.isComplete)
    }

    private fun summary(body: Cell) = TonConnectMessageSummaries.summary(message(body))

    private fun message(body: Cell) = TonConnectRawMessage(
        DESTINATION.toString(userFriendly = true), 50_000_000L, null,
        Base64.getEncoder().encodeToString(BagOfCells(body).toByteArray())
    )

    private fun jetton(
        forward: Cell,
        referenced: Boolean,
        custom: Boolean = false,
        response: Boolean = true
    ) = buildCell {
        storeUInt(0xf8a7ea5, 32)
        storeUInt(7, 64)
        storeTlb(Coins, Coins.ofNano(BigInteger("1234567890123456789")))
        storeTlb(MsgAddress, RECIPIENT)
        storeTlb(MsgAddress, if (response) RESPONSE else AddrNone)
        storeBit(custom)
        if (custom) storeRef(comment("custom"))
        storeTlb(Coins, Coins.ofNano(1))
        storeBit(referenced)
        if (referenced) {
            storeRef(forward)
        } else {
            storeBits(forward.bits)
            forward.refs.forEach { storeRef(it) }
        }
    }

    private fun comment(text: String) = buildCell {
        storeUInt(0, 32)
    storeBytes(text.toByteArray())
    }

    private companion object {
        val DESTINATION = AddrStd(0, ByteArray(32) { 5 })
        val RECIPIENT = AddrStd(0, ByteArray(32) { 6 })
        val RESPONSE = AddrStd(0, ByteArray(32) { 7 })
    }
}
