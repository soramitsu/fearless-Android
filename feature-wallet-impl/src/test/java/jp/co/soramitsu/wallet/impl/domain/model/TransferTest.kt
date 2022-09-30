package jp.co.soramitsu.wallet.impl.domain.model

import java.math.BigDecimal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

private val EXISTENTIAL_DEPOSIT = BigDecimal("0.01").setScale(10)

@Suppress("UnnecessaryVariable")
@RunWith(MockitoJUnitRunner::class)
class TransferTest {

    @Mock
    lateinit var chainAsset: Chain.Asset

    @Before
    fun setup() {
        `when`(chainAsset.precision).thenReturn(18)
    }

    @Test
    fun `should find ok status`() {
        val total = BigDecimal("10")
        val transferable = BigDecimal("10.0")

        val transfer = createTransfer(BigDecimal("1.0"))
        val fee = BigDecimal("0.1")
        val recipientBalance = BigDecimal("1.2")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance, EXISTENTIAL_DEPOSIT)

        assertEquals(status, TransferValidityLevel.Ok)
    }

    @Test
    fun `should find not enough funds status`() {
        val total = BigDecimal("0.5")
        val transferable = total

        val transfer = createTransfer(BigDecimal("1.0"))
        val fee = BigDecimal("0.1")
        val recipientBalance = BigDecimal("1.2")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance, EXISTENTIAL_DEPOSIT)

        assertEquals(status, TransferValidityLevel.Error.Status.NotEnoughFunds)
    }

    @Test
    fun `should find dead recipient status`() {
        val total = BigDecimal("5")
        val transferable = total

        val transfer = createTransfer(EXISTENTIAL_DEPOSIT / BigDecimal.TEN)
        val fee = BigDecimal("0.1")
        val recipientBalance = BigDecimal("0.0")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance, EXISTENTIAL_DEPOSIT)

        assertEquals(status, TransferValidityLevel.Error.Status.DeadRecipient)
    }

    @Test
    fun `should find destroy account status`() {
        val total = EXISTENTIAL_DEPOSIT
        val transferable = total

        val transfer = createTransfer(EXISTENTIAL_DEPOSIT / BigDecimal.TEN)
        val fee = BigDecimal(0)
        val recipientBalance = BigDecimal("1.0")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance, EXISTENTIAL_DEPOSIT)

        assertEquals(status, TransferValidityLevel.Warning.Status.WillRemoveAccount)
    }

    @Test
    fun `errors should go ahead of warnings`() {
        val total = EXISTENTIAL_DEPOSIT
        val transferable = total

        val transfer = createTransfer(EXISTENTIAL_DEPOSIT / BigDecimal.TEN)
        val fee = BigDecimal(0)
        val recipientBalance = BigDecimal("0")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance, EXISTENTIAL_DEPOSIT)

        assertEquals(status, TransferValidityLevel.Error.Status.DeadRecipient)
    }

    private fun createTransfer(amount: BigDecimal) = Transfer("test", amount, chainAsset)
}
