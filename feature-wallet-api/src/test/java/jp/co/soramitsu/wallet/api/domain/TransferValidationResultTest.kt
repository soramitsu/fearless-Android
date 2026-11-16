package jp.co.soramitsu.wallet.api.domain

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.CrossChainDestinationUnavailableException
import jp.co.soramitsu.common.validation.DeadRecipientException
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedException
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedWarning
import jp.co.soramitsu.common.validation.ExistentialDepositUnknownException
import jp.co.soramitsu.common.validation.ExistentialDepositUnknownWarning
import jp.co.soramitsu.common.validation.SpendInsufficientBalanceException
import jp.co.soramitsu.common.validation.SubstrateBridgeAmountLessThenFeeException
import jp.co.soramitsu.common.validation.SubstrateBridgeMinimumAmountRequired
import jp.co.soramitsu.common.validation.TransferAddressNotValidException
import jp.co.soramitsu.common.validation.TransferToTheSameAddressException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class TransferValidationResultTest {
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "s${firstArg<Int>()}" }
        every { getString(any(), *anyVararg()) } answers {
            "s${firstArg<Int>()}(" + secondArg<Array<Any>>().joinToString(",") + ")"
        }
    }

    @Test
    fun everyResultMapsToItsDialog() {
        val expected = listOf(
            TransferValidationResult.InsufficientBalance to SpendInsufficientBalanceException::class,
            TransferValidationResult.InsufficientUtilityAssetBalance to SpendInsufficientBalanceException::class,
            TransferValidationResult.SubstrateBridgeMinimumAmountRequired("5 XOR") to SubstrateBridgeMinimumAmountRequired::class,
            TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning("SORA") to SubstrateBridgeAmountLessThenFeeException::class,
            TransferValidationResult.ExistentialDepositWarning("1 DOT") to ExistentialDepositCrossedWarning::class,
            TransferValidationResult.UtilityExistentialDepositWarning("0.1 DOT") to ExistentialDepositCrossedWarning::class,
            TransferValidationResult.ExistentialDepositError("1 DOT") to ExistentialDepositCrossedException::class,
            TransferValidationResult.UtilityExistentialDepositError("0.1 DOT") to ExistentialDepositCrossedException::class,
            TransferValidationResult.ExistentialDepositUnknown("DOT, KSM") to ExistentialDepositUnknownWarning::class,
            TransferValidationResult.ExistentialDepositUnavailable("DOT") to ExistentialDepositUnknownException::class,
            TransferValidationResult.CrossChainDestinationUnavailable to CrossChainDestinationUnavailableException::class,
            TransferValidationResult.DeadRecipient("0.5", "1", "0.5") to DeadRecipientException::class,
            TransferValidationResult.InvalidAddress to TransferAddressNotValidException::class,
            TransferValidationResult.WaitForFee to WaitForFeeCalculationException::class,
            TransferValidationResult.TransferToTheSameAddress to TransferToTheSameAddressException::class
        )

        for ((result, type) in expected) {
            val exception = ValidationException.fromValidationResult(result, resources)
            assertEquals(result.toString(), type, exception!!::class)
        }
    }

    @Test
    fun dialogsCarryTheAmountsAndNames() {
        val crossed = ValidationException.fromValidationResult(TransferValidationResult.ExistentialDepositWarning("1 DOT"), resources)
        assertTrue(crossed is ValidationWarning)
        assertEquals("s${R.string.common_existential_warning_message}(1 DOT)", crossed!!.explanation)

        val unknown = ValidationException.fromValidationResult(TransferValidationResult.ExistentialDepositUnknown("DOT, KSM"), resources)
        assertTrue(unknown!!.explanation.contains("DOT, KSM"))

        val dead = ValidationException.fromValidationResult(TransferValidationResult.DeadRecipient("0.5", "1", "0.25"), resources)
        assertEquals("s${R.string.wallet_send_dead_recipient_message_v2}(0.5,1,0.25)", dead!!.explanation)

        val bridge = ValidationException.fromValidationResult(TransferValidationResult.SubstrateBridgeMinimumAmountRequired("5 XOR"), resources)
        assertTrue(bridge!!.explanation.contains("5 XOR"))
    }

    @Test
    fun unavailableBalanceExplainsItselfAndValidNeedsNoDialog() {
        val unavailable = ValidationException.fromValidationResult(TransferValidationResult.BalanceUnavailable, resources)!!

        assertEquals(ValidationException::class, unavailable::class)
        assertEquals("s${R.string.common_error_general_title}", unavailable.message)
        assertEquals("s${R.string.portfolio_balance_unavailable}", unavailable.explanation)
        assertNull(ValidationException.fromValidationResult(TransferValidationResult.Valid, resources))
    }

    @Test
    fun onlyExistentialDepositWarningsAreWarnings() {
        assertTrue(TransferValidationResult.ExistentialDepositWarning("1").isExistentialDepositWarning)
        assertTrue(TransferValidationResult.UtilityExistentialDepositWarning("1").isExistentialDepositWarning)
        assertFalse(TransferValidationResult.ExistentialDepositError("1").isExistentialDepositWarning)
        assertFalse(TransferValidationResult.Valid.isExistentialDepositWarning)
        assertEquals(TransferValidationResult.DeadRecipient("1", "2", "3"), TransferValidationResult.DeadRecipient("1", "2", "3"))
    }

    @Test
    fun existentialDepositValidationDefaultsToNoConfirmationsAndNoDestinationFee(): Unit = runBlocking {
        val useCase = RecordingValidateTransferUseCase()

        val result = useCase.validateExistentialDeposit(BigInteger.TEN, mockk(), "chain", "to", "from", BigInteger.ONE)

        assertEquals(TransferValidationResult.Valid, result.getOrThrow())
        assertEquals(emptyList<TransferValidationResult>(), useCase.confirmedValidations)
        assertEquals(BigInteger.ZERO, useCase.destinationFee)
    }

    private class RecordingValidateTransferUseCase : ValidateTransferUseCase {
        var confirmedValidations: List<TransferValidationResult>? = null
        var destinationFee: BigInteger? = null

        override suspend fun validateExistentialDeposit(
            amountInPlanks: BigInteger,
            originAsset: Asset,
            destinationChainId: ChainId,
            destinationAddress: String,
            originAddress: String,
            originFee: BigInteger,
            confirmedValidations: List<TransferValidationResult>,
            destinationFeeInPlanks: BigInteger
        ): Result<TransferValidationResult> {
            this.confirmedValidations = confirmedValidations
            destinationFee = destinationFeeInPlanks
            return Result.success(TransferValidationResult.Valid)
        }

        override suspend fun validateTransfer(
            amountInPlanks: BigInteger,
            asset: Asset,
            destinationAddress: String,
            senderAddress: String,
            fee: BigInteger?,
            confirmedValidations: List<TransferValidationResult>,
            transferMyselfAvailable: Boolean,
            skipEdValidation: Boolean
        ): Result<TransferValidationResult> = error("not used")

        override suspend fun validateXcmTransfer(
            amountInPlanks: BigInteger,
            originAsset: Asset,
            destinationChainId: ChainId,
            destinationAddress: String,
            originAddress: String,
            originFee: BigInteger?,
            confirmedValidations: List<TransferValidationResult>,
            transferMyselfAvailable: Boolean,
            destinationFee: BigDecimal?
        ): Result<TransferValidationResult> = error("not used")
    }
}
