package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTxStatus
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSpendableUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSelectionException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSelector
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinUtxoSelectorTest {

    @Test
    fun `selects deterministic highest value utxos and computes change fee`() {
        val result = selector.select(
            amountSats = 50_000,
            changeAddress = MAINNET_ADDRESS,
            feeRateSatPerVbyte = 2.0,
            utxos = listOf(
                spendable(txid = "55".repeat(32), valueSats = 80_000, vout = 1),
                spendable(txid = "11".repeat(32), valueSats = 100_000, vout = 0)
            )
        )

        assertEquals(listOf("11".repeat(32)), result.selectedUtxos.map { it.txid })
        assertEquals(100_000L, result.inputTotalSats)
        assertEquals(282L, result.feeSats)
        assertEquals(MAINNET_ADDRESS, result.changeAddress)
        assertEquals(49_718L, result.changeSats)
        assertEquals(0L, result.absorbedDustSats)
    }

    @Test
    fun `absorbs uneconomical dust remainder into fee`() {
        val result = selector.select(
            amountSats = 50_000,
            changeAddress = MAINNET_ADDRESS,
            feeRateSatPerVbyte = 1.0,
            utxos = listOf(spendable(valueSats = 50_210))
        )

        assertEquals(210L, result.feeSats)
        assertEquals(100L, result.absorbedDustSats)
        assertEquals(0L, result.changeSats)
        assertNull(result.changeAddress)
    }

    @Test
    fun `filters unconfirmed esplora utxos unless explicitly included`() {
        val utxos = listOf(
            esploraUtxo(valueSats = 1_000, confirmed = true),
            esploraUtxo(valueSats = 2_000, confirmed = false, txid = "22".repeat(32))
        )

        assertEquals(
            1,
            selector.spendableUtxos(BitcoinUtxoSource(MAINNET_ADDRESS, "m/84'/0'/0'/0/0"), utxos).size
        )
        assertEquals(
            2,
            selector.spendableUtxos(
                BitcoinUtxoSource(MAINNET_ADDRESS, "m/84'/0'/0'/0/0"),
                utxos,
                includeUnconfirmed = true
            ).size
        )
    }

    @Test
    fun `rejects insufficient funds and too many required inputs`() {
        assertSelectionError(BitcoinUtxoSelectionException.Code.INSUFFICIENT_FUNDS) {
            selector.select(
                amountSats = 50_000,
                changeAddress = MAINNET_ADDRESS,
                feeRateSatPerVbyte = 1.0,
                utxos = listOf(spendable(valueSats = 30_000))
            )
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.TOO_MANY_INPUTS_REQUIRED) {
            selector.select(
                amountSats = 90_000,
                changeAddress = MAINNET_ADDRESS,
                feeRateSatPerVbyte = 1.0,
                maxInputs = 1,
                utxos = listOf(
                    spendable(valueSats = 40_000, txid = "11".repeat(32), vout = 0),
                    spendable(valueSats = 40_000, txid = "22".repeat(32), vout = 1),
                    spendable(valueSats = 40_000, txid = "33".repeat(32), vout = 2)
                )
            )
        }
    }

    @Test
    fun `rejects malformed send selection inputs before returning a plan`() {
        assertSelectionError(BitcoinUtxoSelectionException.Code.AMOUNT_BELOW_DUST) {
            selector.select(1, MAINNET_ADDRESS, 1.0, utxos = listOf(spendable(valueSats = 1_000)))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_CHANGE_ADDRESS) {
            selector.select(1_000, TESTNET_ADDRESS, 1.0, utxos = listOf(spendable(valueSats = 2_000)))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_FEE_RATE) {
            selector.select(1_000, MAINNET_ADDRESS, 0.0, utxos = listOf(spendable(valueSats = 2_000)))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_MAX_INPUTS) {
            selector.select(1_000, MAINNET_ADDRESS, 1.0, maxInputs = 0, utxos = listOf(spendable(valueSats = 2_000)))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.UTXOS_REQUIRED) {
            selector.select(1_000, MAINNET_ADDRESS, 1.0, utxos = emptyList())
        }
    }

    @Test
    fun `rejects adversarial utxo payloads`() {
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_TXID) {
            selectSingle(spendable(txid = "../bad", valueSats = 2_000))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_VOUT) {
            selectSingle(spendable(valueSats = 2_000, vout = -1))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_UTXO_VALUE) {
            selectSingle(spendable(valueSats = 0))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_SCRIPT_PUBKEY) {
            selectSingle(spendable(valueSats = 2_000, scriptPubKey = "00gg"))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_DERIVATION_PATH) {
            selectSingle(spendable(valueSats = 2_000, derivationPath = "../bad"))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.DUPLICATE_UTXO) {
            selector.select(
                amountSats = 1_000,
                changeAddress = MAINNET_ADDRESS,
                feeRateSatPerVbyte = 1.0,
                utxos = listOf(spendable(valueSats = 2_000), spendable(valueSats = 2_000))
            )
        }
    }

    @Test
    fun `rejects malformed source addresses and paths`() {
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_SOURCE_ADDRESS) {
            selector.spendableUtxos(BitcoinUtxoSource(TESTNET_ADDRESS), listOf(esploraUtxo()))
        }
        assertSelectionError(BitcoinUtxoSelectionException.Code.INVALID_DERIVATION_PATH) {
            selector.spendableUtxos(BitcoinUtxoSource(MAINNET_ADDRESS, "../bad"), listOf(esploraUtxo()))
        }
    }

    private fun selectSingle(utxo: BitcoinSpendableUtxo) {
        selector.select(
            amountSats = 1_000,
            changeAddress = MAINNET_ADDRESS,
            feeRateSatPerVbyte = 1.0,
            utxos = listOf(utxo)
        )
    }

    private fun assertSelectionError(
        expected: BitcoinUtxoSelectionException.Code,
        block: () -> Unit
    ) {
        val error = assertThrows(BitcoinUtxoSelectionException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private companion object {
        val selector = BitcoinUtxoSelector()
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val TESTNET_ADDRESS = "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"

        fun spendable(
            txid: String = "11".repeat(32),
            valueSats: Long,
            vout: Long = 0,
            derivationPath: String? = null,
            scriptPubKey: String? = null
        ): BitcoinSpendableUtxo {
            return BitcoinSpendableUtxo(
                address = MAINNET_ADDRESS,
                derivationPath = derivationPath,
                scriptPubKey = scriptPubKey,
                txid = txid,
                valueSats = valueSats,
                vout = vout
            )
        }

        fun esploraUtxo(
            valueSats: Long = 1_000,
            confirmed: Boolean = true,
            txid: String = "11".repeat(32)
        ): BitcoinEsploraUtxo {
            return BitcoinEsploraUtxo(
                txid = txid,
                vout = 0,
                value = valueSats,
                status = BitcoinEsploraTxStatus(confirmed = confirmed)
            )
        }
    }
}
