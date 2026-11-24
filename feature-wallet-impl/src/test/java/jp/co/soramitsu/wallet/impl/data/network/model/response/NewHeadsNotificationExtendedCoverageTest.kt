package jp.co.soramitsu.wallet.impl.data.network.model.response

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.web3j.protocol.ObjectMapperFactory

/** eth_subscribe newHeads notifications are read with web3j's mapper, as the websocket service does. */
class NewHeadsNotificationExtendedCoverageTest {

    @Test
    fun `new head notification exposes the block header including the base fee`() {
        val json = """
            {
              "jsonrpc": "2.0",
              "method": "eth_subscription",
              "params": {
                "result": {
                  "difficulty": "0x0",
                  "extraData": "0x",
                  "gasLimit": "0x1c9c380",
                  "gasUsed": "0x5208",
                  "hash": "0xblock",
                  "logsBloom": "0x00",
                  "miner": "0xminer",
                  "nonce": "0x0000000000000000",
                  "number": "0x10",
                  "parentHash": "0xparent",
                  "receiptRoot": "0xreceipts",
                  "sha3Uncles": "0xuncles",
                  "stateRoot": "0xstate",
                  "timestamp": "0x6553f100",
                  "transactionRoot": "0xtransactions",
                  "baseFeePerGas": "0x3b9aca00"
                }
              }
            }
        """.trimIndent()

        val notification = ObjectMapperFactory.getObjectMapper().readValue(json, NewHeadsNotificationExtended::class.java)

        val head = requireNotNull(notification.params.result)
        assertEquals("2.0", notification.jsonrpc)
        assertEquals("eth_subscription", notification.method)
        assertEquals("0x10", head.number)
        assertEquals("0x3b9aca00", head.baseFeePerGas)
        assertEquals("0xblock", head.hash)
        assertEquals("0xparent", head.parentHash)
        assertEquals("0x5208", head.gasUsed)
        assertEquals("0x1c9c380", head.gasLimit)
        assertEquals("0x6553f100", head.timestamp)
        assertEquals("0xminer", head.miner)
        assertEquals("0xstate", head.stateRoot)
        assertEquals("0xreceipts", head.receiptRoot)
        assertEquals("0xtransactions", head.transactionRoot)
        assertEquals("0xuncles", head.sha3Uncles)
        assertEquals("0x0", head.difficulty)
        assertEquals("0x", head.extraData)
        assertEquals("0x00", head.logsBloom)
        assertEquals("0x0000000000000000", head.nonce)
    }

    @Test
    fun `a fresh head has no fields set`() {
        val head = NewHeadExtended()

        assertNull(head.baseFeePerGas)
        head.baseFeePerGas = "0x1"
        assertEquals("0x1", head.baseFeePerGas)
    }
}
