package jp.co.soramitsu.feature_wallet_api.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

private val TOKEN_TYPE = Token.Type.WND
private val EXISTENTIAL_DEPOSIT = TOKEN_TYPE.networkType.runtimeConfiguration.existentialDeposit.setScale(10)

@Suppress("UnnecessaryVariable")
@RunWith(MockitoJUnitRunner::class)
class TransferTest {

    @Test
    fun `should find ok status`() {
        val total = BigDecimal("10")
        val transferable = BigDecimal("10.0")

        val transfer = createTransfer(BigDecimal("1.0"))
        val fee = BigDecimal("0.1")
        val recipientBalance = BigDecimal("1.2")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance)

        assertEquals(status, TransferValidityLevel.Ok)
    }

    @Test
    fun `should find not enough funds status`() {
        val total = BigDecimal("0.5")
        val transferable = total

        val transfer = createTransfer(BigDecimal("1.0"))
        val fee = BigDecimal("0.1")
        val recipientBalance = BigDecimal("1.2")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance)

        assertEquals(status, TransferValidityLevel.Error.Status.NotEnoughFunds)
    }

    @Test
    fun `should find dead recipient status`() {
        val total = BigDecimal("5")
        val transferable = total

        val transfer = createTransfer(EXISTENTIAL_DEPOSIT / BigDecimal.TEN)
        val fee = BigDecimal("0.1")
        val recipientBalance = BigDecimal("0.0")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance)

        assertEquals(status, TransferValidityLevel.Error.Status.DeadRecipient)
    }

    @Test
    fun `should find destroy account status`() {
        val total = EXISTENTIAL_DEPOSIT
        val transferable = total

        val transfer = createTransfer(EXISTENTIAL_DEPOSIT / BigDecimal.TEN)
        val fee = BigDecimal(0)
        val recipientBalance = BigDecimal("1.0")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance)

        assertEquals(status, TransferValidityLevel.Warning.Status.WillRemoveAccount)
    }

    @Test
    fun `errors should go ahead of warnings`() {
        val total = EXISTENTIAL_DEPOSIT
        val transferable = total

        val transfer = createTransfer(EXISTENTIAL_DEPOSIT / BigDecimal.TEN)
        val fee = BigDecimal(0)
        val recipientBalance = BigDecimal("0")

        val status = transfer.validityStatus(transferable, total, fee, recipientBalance)

        assertEquals(status, TransferValidityLevel.Error.Status.DeadRecipient)
    }

    private fun createTransfer(amount: BigDecimal) = Transfer("test", amount, TOKEN_TYPE)
}