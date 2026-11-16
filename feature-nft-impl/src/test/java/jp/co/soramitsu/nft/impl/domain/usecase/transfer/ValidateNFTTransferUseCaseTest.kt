package jp.co.soramitsu.nft.impl.domain.usecase.transfer

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class ValidateNFTTransferUseCaseTest {
    private val ownAddress = "0x" + "11".repeat(20)
    private val otherAddress = "0x" + "22".repeat(20)
    private val chain = mockk<Chain> { every { ecosystem } returns Ecosystem.Ethereum }
    private val utility = mockk<Asset> { every { transferableInPlanks } returns BigInteger.TEN }
    private val validate = ValidateNFTTransferUseCase()

    @Test
    fun validRecipientWithOneTokenAndExactFeeBalancePasses() {
        assertEquals(TransferValidationResult.Valid, result())
    }

    @Test
    fun invalidAddressTakesPriorityOverBalanceFailure() {
        assertEquals(TransferValidationResult.InvalidAddress, result(recipient = "invalid", balance = BigInteger.ZERO))
    }

    @Test
    fun sameAddressIsRejectedUnlessExplicitlyConfirmed() {
        assertEquals(TransferValidationResult.TransferToTheSameAddress, result(recipient = ownAddress))
        assertEquals(
            TransferValidationResult.Valid,
            result(recipient = ownAddress, confirmed = listOf(TransferValidationResult.TransferToTheSameAddress))
        )
    }

    @Test
    fun tokenBalanceMustCoverOneToken() {
        assertEquals(TransferValidationResult.InsufficientBalance, result(balance = BigInteger.ZERO))
    }

    @Test
    fun utilityBalanceMustCoverFeeIndependentlyOfTokenBalance() {
        assertEquals(TransferValidationResult.InsufficientUtilityAssetBalance, result(fee = BigInteger.valueOf(11)))
    }

    private fun result(
        recipient: String = otherAddress,
        balance: BigInteger = BigInteger.ONE,
        fee: BigInteger = BigInteger.TEN,
        confirmed: List<TransferValidationResult> = emptyList()
    ): TransferValidationResult = validate(chain, recipient, ownAddress, utility, fee, false, balance, confirmed).getOrThrow()
}
