package jp.co.soramitsu.tonconnect.impl.domain

import jp.co.soramitsu.runtime.multiNetwork.chain.ton.TonWalletContract.Companion.createIntMsg
import jp.co.soramitsu.tonconnect.api.model.TonConnectRawMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb
import java.math.BigInteger
import java.util.Base64

class TonConnectTransfersTest {

    @Test
    fun shouldSignTheAmountTheDappRequested_whenItIsBelowAnyWalletDefault() {
        val message = message(amount = 50_000_000, payload = JETTON_TRANSFER_WITH_INLINE_FORWARD)

        val transfer = TonConnectTransfers.transfer(message, bounceable = true)

        assertEquals(Coins.ofNano(50_000_000), transfer.coins.coins)
        assertEquals(Coins.ofNano(50_000_000), (createIntMsg(transfer).info as CommonMsgInfoRelaxed.IntMsgInfoRelaxed).value.coins)
    }

    @Test
    fun shouldSignThePayloadUnchanged_whenItIsAJettonTransfer() {
        val message = message(payload = JETTON_TRANSFER_WITH_INLINE_FORWARD)

        val transfer = TonConnectTransfers.transfer(message, bounceable = true)

        assertEquals(JETTON_TRANSFER_CELL.hash(), transfer.messageData.body.hash())
    }

    @Test
    fun shouldKeepDestinationStateInitAndBounce_asRequested() {
        val message = message(stateInit = STATE_INIT)

        val bounceable = TonConnectTransfers.transfer(message, bounceable = true)
        val nonBounceable = TonConnectTransfers.transfer(message, bounceable = false)

        assertEquals(RECIPIENT.toString(userFriendly = false), (bounceable.destination as AddrStd).toString(userFriendly = false))
        val signedStateInit = requireNotNull(bounceable.messageData.stateInit).value
        assertEquals(CellRef(STATE_INIT_VALUE, StateInit).hash(), CellRef(signedStateInit, StateInit).hash())
        assertTrue(bounceable.bounceable)
        assertFalse(nonBounceable.bounceable)
    }

    @Test
    fun shouldSendAnEmptyBody_onlyWhenTheDappSentNoPayload() {
        val transfer = TonConnectTransfers.transfer(message(payload = null), bounceable = true)

        assertTrue(transfer.messageData.body.isEmpty())
        assertNull(transfer.messageData.stateInit)
    }

    @Test
    fun shouldStoreTheBoundedExpiry_whenTheWalletIsNotDeployed() {
        val body = TonConnectTransfers.unsignedBody(WALLET_ID, seqno = 0, validUntil = VALID_UNTIL, transfers = emptyList())
        val slice = body.beginParse()

        assertEquals(WALLET_ID.toLong(), slice.loadUInt(32).toLong())
        // Not the all-ones "never expires" value that undeployed wallets used before.
        assertEquals(VALID_UNTIL, slice.loadUInt(32).toLong())
        assertEquals(0L, slice.loadUInt(32).toLong())
    }

    @Test
    fun shouldStoreTheExpirySeqnoAndEveryMessage_whenTheWalletIsDeployed() {
        val transfers = listOf(
            TonConnectTransfers.transfer(message(amount = 1), bounceable = true),
            TonConnectTransfers.transfer(message(amount = 2, payload = JETTON_TRANSFER_WITH_INLINE_FORWARD), bounceable = false)
        )

        val body = TonConnectTransfers.unsignedBody(WALLET_ID, seqno = 41, validUntil = VALID_UNTIL, transfers = transfers)
        val slice = body.beginParse()

        assertEquals(WALLET_ID.toLong(), slice.loadUInt(32).toLong())
        assertEquals(VALID_UNTIL, slice.loadUInt(32).toLong())
        assertEquals(41L, slice.loadUInt(32).toLong())
        assertEquals(0L, slice.loadUInt(8).toLong())
        transfers.forEachIndexed { index, transfer ->
            assertEquals(3L, slice.loadUInt(8).toLong())
            val expected = buildCell { storeTlb(MessageRelaxed.tlbCodec(AnyTlbConstructor), createIntMsg(transfer)) }
            assertEquals(expected.hash(), body.refs[index].hash())
        }
        assertEquals(transfers.size, body.refs.size)
    }

    private fun message(
        amount: Long = 1_000_000,
        payload: String? = null,
        stateInit: String? = null
    ) = TonConnectRawMessage(RECIPIENT.toString(userFriendly = true), amount, stateInit, payload)

    private companion object {
        const val WALLET_ID = 698_983_191
        const val VALID_UNTIL = 1_800_000_300L
        val RECIPIENT = AddrStd(0, ByteArray(32) { 5 })
        val OWNER = AddrStd(0, ByteArray(32) { 6 })

        // A jetton transfer whose forward payload (a text comment) is stored inline, after a 0 bit.
        val JETTON_TRANSFER_CELL: Cell = buildCell {
            storeUInt(0xf8a7ea5, 32)
            storeUInt(BigInteger.valueOf(7), 64)
            storeTlb(Coins, Coins.ofNano(1_000))
            storeTlb(MsgAddressInt, RECIPIENT)
            storeTlb(MsgAddressInt, OWNER)
            storeBit(false)
            storeTlb(Coins, Coins.ofNano(1))
            storeBit(false)
            storeUInt(0, 32)
            storeBytes("swap".toByteArray())
        }
        val JETTON_TRANSFER_WITH_INLINE_FORWARD: String = Base64.getEncoder().encodeToString(
            BagOfCells(JETTON_TRANSFER_CELL).toByteArray()
        )
        val STATE_INIT_VALUE = StateInit(buildCell { storeUInt(1, 8) }, buildCell { storeUInt(2, 8) })
        val STATE_INIT: String = Base64.getEncoder().encodeToString(
            BagOfCells(buildCell { storeTlb(StateInit.tlbCodec(), STATE_INIT_VALUE) }).toByteArray()
        )
    }
}
