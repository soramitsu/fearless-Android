package jp.co.soramitsu.wallet.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.SignedRawTransaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.crypto.transaction.type.Transaction1559
import org.web3j.utils.Numeric

class EthereumTransferSigningTest {

    // Public web3 documentation key; it holds nothing on any network.
    private val credentials = Credentials.create("0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318")
    private val recipient = "0x3535353535353535353535353535353535353535"

    @Test
    fun `legacy transfer is replay protected for its chain`() {
        val raw = RawTransaction.createEtherTransaction(
            BigInteger.valueOf(9),
            BigInteger.valueOf(20_000_000_000),
            BigInteger.valueOf(21_000),
            recipient,
            BigInteger.TEN.pow(18)
        )

        val signed = TransactionDecoder.decode(signEvmTransfer(raw, BSC_CHAIN_ID, credentials).toHexString(true))
            as SignedRawTransaction

        assertTrue(signed.type.isLegacy)
        assertEquals(BSC_CHAIN_ID, signed.chainId)
        assertTrue(Numeric.toBigInt(signed.signatureData.v) >= BigInteger.valueOf(35))
        assertEquals(credentials.address, signed.from)
    }

    @Test
    fun `eip1559 transfer keeps the chain id of its typed payload`() {
        val raw = RawTransaction.createEtherTransaction(
            ETHEREUM_CHAIN_ID,
            BigInteger.valueOf(9),
            BigInteger.valueOf(21_000),
            recipient,
            BigInteger.TEN.pow(18),
            BigInteger.valueOf(1_000_000_000),
            BigInteger.valueOf(30_000_000_000)
        )

        val encoded = signEvmTransfer(raw, ETHEREUM_CHAIN_ID, credentials)
        val signed = TransactionDecoder.decode(encoded.toHexString(true)) as SignedRawTransaction

        assertEquals(EIP1559_ENVELOPE, encoded.first())
        assertTrue(signed.type.isEip1559)
        assertEquals(ETHEREUM_CHAIN_ID, (signed.transaction as Transaction1559).chainId)
    }

    private companion object {
        const val ETHEREUM_CHAIN_ID = 1L
        const val BSC_CHAIN_ID = 56L
        const val EIP1559_ENVELOPE: Byte = 0x02
    }
}
