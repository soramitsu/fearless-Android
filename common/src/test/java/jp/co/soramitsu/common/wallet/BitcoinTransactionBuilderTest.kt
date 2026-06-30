package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinPaymentOutput
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSpendableUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionBuilder
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinTransactionBuilderTest {

    @Test
    fun `builds and signs mainnet p2wpkh transaction matching web vector`() {
        val result = BitcoinTransactionBuilder.buildP2wpkhTransaction(
            mnemonic = MNEMONIC,
            inputs = listOf(
                BitcoinSpendableUtxo(
                    address = MAINNET_ADDRESS,
                    txid = TXID,
                    valueSats = 100_000,
                    vout = 1
                )
            ),
            outputs = listOf(BitcoinPaymentOutput(MAINNET_RECIPIENT, 50_000)),
            changeAddress = MAINNET_ADDRESS,
            feeRateSatPerVbyte = 2.0
        )

        assertEquals(282L, result.feeSats)
        assertEquals(49_718L, result.changeSats)
        assertEquals(100_000L, result.inputTotalSats)
        assertEquals(50_000L, result.outputTotalSats)
        assertEquals(141, result.vsize)
        assertEquals(EXPECTED_TXID, result.txid)
        assertEquals(EXPECTED_TX_HEX, result.txHex)
    }

    @Test
    fun `supports explicit fee spends with no change output`() {
        val result = BitcoinTransactionBuilder.buildP2wpkhTransaction(
            mnemonic = MNEMONIC,
            inputs = listOf(
                BitcoinSpendableUtxo(
                    txid = "22".repeat(32),
                    valueSats = 51_000,
                    vout = 0
                )
            ),
            outputs = listOf(BitcoinPaymentOutput(MAINNET_RECIPIENT, 50_000)),
            feeSats = 1_000
        )

        assertEquals(0L, result.changeSats)
        assertEquals(1_000L, result.feeSats)
        assertEquals(50_000L, result.outputTotalSats)
    }

    @Test
    fun `rejects unsafe utxos outputs fees and change handling before signing`() {
        val baseInput = BitcoinSpendableUtxo(txid = TXID, valueSats = 100_000, vout = 0)
        val baseOutput = BitcoinPaymentOutput(MAINNET_RECIPIENT, 50_000)

        assertTransactionError(BitcoinTransactionException.Code.INPUTS_REQUIRED) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(MNEMONIC, inputs = emptyList(), outputs = listOf(baseOutput), feeSats = 1_000)
        }
        assertTransactionError(BitcoinTransactionException.Code.INVALID_TXID) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(baseInput.copy(txid = "zz")),
                outputs = listOf(baseOutput),
                feeSats = 1_000
            )
        }
        assertTransactionError(BitcoinTransactionException.Code.DUPLICATE_UTXO) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(baseInput, baseInput),
                outputs = listOf(baseOutput),
                feeSats = 1_000
            )
        }
        assertTransactionError(BitcoinTransactionException.Code.SATOSHI_OVERFLOW) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(
                    baseInput.copy(valueSats = BitcoinUtxoSelector.MAX_SATOSHI),
                    baseInput.copy(txid = "22".repeat(32), valueSats = 1)
                ),
                outputs = listOf(baseOutput),
                feeSats = 1_000
            )
        }
        assertTransactionError(BitcoinTransactionException.Code.INVALID_OUTPUT_ADDRESS) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(baseInput),
                outputs = listOf(BitcoinPaymentOutput(TESTNET_ADDRESS, 50_000)),
                feeSats = 1_000
            )
        }
        assertTransactionError(BitcoinTransactionException.Code.SATOSHI_OVERFLOW) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(baseInput.copy(valueSats = BitcoinUtxoSelector.MAX_SATOSHI)),
                outputs = listOf(
                    BitcoinPaymentOutput(MAINNET_RECIPIENT, BitcoinUtxoSelector.MAX_SATOSHI),
                    BitcoinPaymentOutput(MAINNET_ADDRESS, BitcoinUtxoSelector.BITCOIN_P2WPKH_DUST_SATS)
                ),
                feeSats = 1_000
            )
        }
        assertTransactionError(BitcoinTransactionException.Code.INVALID_OUTPUT_VALUE) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(baseInput),
                outputs = listOf(BitcoinPaymentOutput(MAINNET_RECIPIENT, BitcoinUtxoSelector.BITCOIN_P2WPKH_DUST_SATS - 1)),
                feeSats = 1_000
            )
        }
        assertTransactionError(BitcoinTransactionException.Code.INVALID_FEE) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(MNEMONIC, inputs = listOf(baseInput), outputs = listOf(baseOutput), feeSats = 0)
        }
        assertTransactionError(BitcoinTransactionException.Code.INVALID_FEE_RATE) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(baseInput),
                outputs = listOf(baseOutput),
                feeRateSatPerVbyte = 0.0
            )
        }
        assertTransactionError(BitcoinTransactionException.Code.INSUFFICIENT_FUNDS) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(baseInput.copy(valueSats = 50_500)),
                outputs = listOf(baseOutput),
                feeSats = 1_000
            )
        }
        assertTransactionError(BitcoinTransactionException.Code.CHANGE_ADDRESS_REQUIRED) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(baseInput.copy(valueSats = 52_000)),
                outputs = listOf(baseOutput),
                feeSats = 1_000
            )
        }
        assertTransactionError(BitcoinTransactionException.Code.CHANGE_BELOW_DUST) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                MNEMONIC,
                inputs = listOf(baseInput.copy(valueSats = 50_900)),
                outputs = listOf(baseOutput),
                changeAddress = MAINNET_ADDRESS,
                feeSats = 800
            )
        }
    }

    @Test
    fun `rejects utxos that do not match derived bip84 key or witness script`() {
        assertTransactionError(BitcoinTransactionException.Code.UTXO_ADDRESS_MISMATCH) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                mnemonic = MNEMONIC,
                inputs = listOf(
                    BitcoinSpendableUtxo(
                        address = MAINNET_RECIPIENT,
                        txid = TXID,
                        valueSats = 51_000,
                        vout = 0
                    )
                ),
                outputs = listOf(BitcoinPaymentOutput(MAINNET_RECIPIENT, 50_000)),
                feeSats = 1_000
            )
        }

        assertTransactionError(BitcoinTransactionException.Code.UTXO_SCRIPT_MISMATCH) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                mnemonic = MNEMONIC,
                inputs = listOf(
                    BitcoinSpendableUtxo(
                        scriptPubKey = "0014${"00".repeat(20)}",
                        txid = TXID,
                        valueSats = 51_000,
                        vout = 0
                    )
                ),
                outputs = listOf(BitcoinPaymentOutput(MAINNET_RECIPIENT, 50_000)),
                feeSats = 1_000
            )
        }

        assertTransactionError(BitcoinTransactionException.Code.INVALID_DERIVATION_PATH) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                mnemonic = MNEMONIC,
                inputs = listOf(
                    BitcoinSpendableUtxo(
                        derivationPath = "m/84'/2147483648'/0'/0/0",
                        txid = TXID,
                        valueSats = 51_000,
                        vout = 0
                    )
                ),
                outputs = listOf(BitcoinPaymentOutput(MAINNET_RECIPIENT, 50_000)),
                feeSats = 1_000
            )
        }
    }

    @Test
    fun `rejects empty mnemonic before signing`() {
        assertTransactionError(BitcoinTransactionException.Code.INVALID_MNEMONIC) {
            BitcoinTransactionBuilder.buildP2wpkhTransaction(
                mnemonic = "",
                inputs = listOf(BitcoinSpendableUtxo(txid = TXID, valueSats = 51_000, vout = 0)),
                outputs = listOf(BitcoinPaymentOutput(MAINNET_RECIPIENT, 50_000)),
                feeSats = 1_000
            )
        }
    }

    private fun assertTransactionError(
        expected: BitcoinTransactionException.Code,
        block: () -> Unit
    ) {
        val error = assertThrows(BitcoinTransactionException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val MAINNET_RECIPIENT = "bc1qslk39wvggqa0vl8nd6jckaz54dw3vk45c5w60m"
        const val TESTNET_ADDRESS = "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"
        val TXID = "11".repeat(32)
        const val EXPECTED_TXID = "94c9b9d5070f24e06725b1000d9b1a0d46473d07b59088aca35e3d3da345023d"
        const val EXPECTED_TX_HEX = "0200000000010111111111111111111111111111111111111111111111111111111111111111110100000000ffffffff0250c300000000000016001487ed12b988403af67cf36ea58b7454ab5d165ab436c2000000000000160014c0cebcd6c3d3ca8c75dc5ec62ebe55330ef910e202473044022009ec0c24a20c4346c6516065723e2e83e6a7e4dd278fb66e27f36d9108d7ef4c022077f115bfbd68a2bc7a4c100766d9cceaf5300bcfcdc775be0248e7fb5a58216801210330d54fd0dd420a6e5f8d3624f5f3482cae350f79d5f0753bf5beef9c2d91af3c00000000"
    }
}
