package jp.co.soramitsu.runtime.multiNetwork.chain.ton

import io.ktor.util.hex
import jp.co.soramitsu.fearless_utils.encrypt.ton.TonEd25519
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.tlb.CellRef
import java.math.BigInteger
import java.util.Random

/** The TON contract and comment code after TON keys moved from ton-kotlin types to plain bytes (4.3.2). */
class TonInRepoKeyMaterialTest {

    private val random = Random(432)

    @Test
    fun `V4R2 addresses equal the ones built from ton-kotlin's PublicKeyEd25519`() {
        repeat(25) { index ->
            val publicKey = TonEd25519.publicKey(bytes(32))
            val contract = V4R2WalletContract(publicKey)
            val legacy = legacyAddress(publicKey)
            assertEquals("$index", legacy, contract.getSmartContractAddress())
            assertEquals("$index", legacy, contract.address)
            assertEquals(contract.getAddress(isTestnet = false), V4R2WalletContract(hex(publicKey)).getAddress(isTestnet = false))
            assertEquals(contract.getAccountId(isTestnet = true), V4R2WalletContract(publicKey + byteArrayOf(1, 2)).getAccountId(isTestnet = true))
            assertArrayEquals(publicKey, contract.publicKey)
        }
        assertThrows(IllegalArgumentException::class.java) { V4R2WalletContract(ByteArray(31)) }
    }

    @Test
    fun `encrypted comments open on the other side`() {
        val sender = bytes(32)
        val recipient = bytes(32)
        val senderPublic = TonEd25519.publicKey(sender)
        val recipientPublic = TonEd25519.publicKey(recipient)
        val senderAddress = V4R2WalletContract(senderPublic).getAddress(isTestnet = false)

        for (comment in listOf("hi", "a longer comment that needs more than one cell of payload to carry it whole", "こんにちは")) {
            val cell = CommentEncryption.encryptComment(comment, senderPublic, recipientPublic, sender, senderAddress)
            val payload = snakeBytes(cell)
            assertEquals("2167da4b", hex(payload.copyOfRange(0, 4)))
            val cipherText = hex(payload.copyOfRange(4, payload.size))
            assertEquals(comment, CommentEncryption.decryptComment(recipientPublic, recipient, cipherText, senderAddress))
            assertEquals(comment, CommentEncryption.decryptComment(senderPublic, sender, cipherText, senderAddress))
        }
        val longKey = sender + senderPublic
        val cell = CommentEncryption.encryptComment("64-byte key", senderPublic, recipientPublic, longKey, senderAddress)
        val payload = snakeBytes(cell)
        assertEquals("64-byte key", CommentEncryption.decryptComment(recipientPublic, recipient, hex(payload.copyOfRange(4, payload.size)), senderAddress))
    }

    @Test
    fun `wallet query ids are random 64-bit values`() {
        val ids = List(8) { newWalletQueryId() }
        ids.forEach { id -> assertTrue(id.signum() >= 0 && id.bitLength() <= 64) }
        assertNotEquals(1, ids.toSet().size)
        assertTrue(ids.all { it < BigInteger.ONE.shiftLeft(64) })
    }

    // The 4.3.1 V4R2WalletContract address: PublicKeyEd25519.decode, key bytes into the data cell.
    private fun legacyAddress(publicKey: ByteArray): AddrStd {
        val key = PublicKeyEd25519.decode(publicKey)
        val data = CellBuilder.createCell {
            storeUInt(0, 32)
            storeUInt(TonWalletContract.DEFAULT_WALLET_ID, 32)
            storeBytes(key.key.toByteArray())
            storeBit(false)
        }
        val stateInit = StateInit(V4R2WalletContract.code, data)
        return AddrStd(TonWalletContract.DEFAULT_WORKCHAIN, CellRef(stateInit, StateInit).hash())
    }

    private fun snakeBytes(cell: Cell): ByteArray {
        var bytes = cell.bits.toByteArray()
        var next = cell.refs.firstOrNull()
        while (next != null) {
            bytes += next.bits.toByteArray()
            next = next.refs.firstOrNull()
        }
        return bytes
    }

    private fun bytes(length: Int): ByteArray = ByteArray(length).also(random::nextBytes)
}
