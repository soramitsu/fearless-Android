package jp.co.soramitsu.nft.impl.domain.usecase.transfer

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.nft.domain.models.NFT
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.EthGetTransactionCount
import java.math.BigInteger
import java.util.concurrent.CompletableFuture

class NFTCallAdaptersTest {
    private val sender = "0x" + "11".repeat(20)
    private val receiver = "0x" + "22".repeat(20)
    private val contract = "0x" + "33".repeat(20)
    private val web3j = mockk<Web3j>(relaxed = true) {
        every { ethGetTransactionCount(sender, DefaultBlockParameterName.PENDING).sendAsync() } returns
            CompletableFuture.completedFuture(EthGetTransactionCount().apply { result = "0x7" })
    }

    @Test
    fun knownErc721ReceiverUsesTransferFromWithExactArguments(): Unit = runBlocking {
        val call = NFTTransferAdapter(web3j, sender, receiver, token("ERC721"), true)
        assertEquals("0x23b872dd" + word(sender) + word(receiver) + word("2a"), call.encodedFunction)
        assertEquals(BigInteger.valueOf(7), call.nonce)
        assertEquals(sender, call.sender)
        assertEquals(receiver, call.receiver)
        assertEquals(contract, call.contractAddress)
        assertTrue(call.outputTypeRefs.isEmpty())
    }

    @Test
    fun unknownErc721ReceiverUsesSafeTransferWithEmptyData(): Unit = runBlocking {
        val call = NFTTransferAdapter(web3j, sender, receiver, token("ERC721"), false)
        val expected = "0xb88d4fde" + word(sender) + word(receiver) + word("2a") + word("80") + word("0")
        assertEquals(expected, call.encodedFunction)
    }

    @Test
    fun erc1155TransferAlwaysSendsExactlyOneTokenWithEmptyData(): Unit = runBlocking {
        val first = NFTTransferAdapter(web3j, sender, receiver, token("ERC1155"), false)
        val known = NFTTransferAdapter(web3j, sender, receiver, token("ERC1155"), true)
        val expected = "0xf242432a" + word(sender) + word(receiver) + word("2a") + word("1") + word("a0") + word("0")
        assertEquals(expected, first.encodedFunction)
        assertEquals(first.encodedFunction, known.encodedFunction)
    }

    @Test
    fun negativeTokenIdCannotBeTransferred() {
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { NFTTransferAdapter(web3j, sender, receiver, token("ERC721", BigInteger.valueOf(-1)), true) }
        }
        assertEquals("TokenId supplied is null.", failure.message)
    }

    @Test
    fun unsupportedTokenCannotBeTransferred() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { NFTTransferAdapter(web3j, sender, receiver, token("ERC20"), true) }
        }
        assertEquals("Token provided is not supported.", failure.message)
    }

    @Test
    fun erc721BalanceCallQueriesOwnersCollectionBalance() {
        val call = NFTAccountBalanceAdapter(sender, token("ERC721"))
        assertEquals("0x70a08231" + word(sender), call.encodedFunction)
        assertEquals(BigInteger.ZERO, call.nonce)
        assertEquals(sender, call.sender)
        assertEquals("", call.receiver)
        assertEquals(contract, call.contractAddress)
        assertEquals(org.web3j.abi.datatypes.generated.Uint256::class.java, call.outputTypeRefs.single().type)
        assertEquals(1, call.outputTypeRefs.size)
    }

    @Test
    fun erc1155BalanceCallIncludesTokenIdentifier() {
        val call = NFTAccountBalanceAdapter(sender, token("ERC1155"))
        assertEquals("0x00fdd58e" + word(sender) + word("2a"), call.encodedFunction)
        assertEquals(1, call.outputTypeRefs.size)
    }

    @Test
    fun balanceCallRejectsMissingContract() {
        val failure = assertThrows(IllegalStateException::class.java) {
            NFTAccountBalanceAdapter(sender, token("ERC721", address = " "))
        }
        assertEquals("ContractAddress supplied is incorrect.", failure.message)
    }

    @Test
    fun balanceCallRejectsNegativeId() {
        val failure = assertThrows(IllegalStateException::class.java) {
            NFTAccountBalanceAdapter(sender, token("ERC721", BigInteger.valueOf(-1)))
        }
        assertEquals("TokenId supplied is incorrect.", failure.message)
    }

    @Test
    fun balanceCallRejectsUnsupportedTokenType() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            NFTAccountBalanceAdapter(sender, token("ERC20"))
        }
        assertEquals("Token provided is not supported.", failure.message)
    }

    private fun token(
        type: String,
        id: BigInteger = BigInteger.valueOf(42),
        address: String = contract
    ): NFT = mockk {
        every { tokenType } returns type
        every { tokenId } returns id
        every { contractAddress } returns address
    }

    private fun word(hex: String) = hex.removePrefix("0x").padStart(64, '0')
}
