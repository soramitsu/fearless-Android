package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.NotEnoughResultedAmountToPayFeeException
import jp.co.soramitsu.common.validation.SpendInsufficientBalanceException
import jp.co.soramitsu.common.validation.UnableToPayFeeException
import org.junit.Assert.*
import org.junit.Test

class SwapBalanceValidationTest {
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(any()) } answers { "text-${firstArg<Int>()}" }
    }
    private val ten = BigDecimal.TEN
    private val one = BigDecimal.ONE

    @Test fun unknownInputAndFeeBalancesCannotAuthorizeSwap() {
        for ((input, fee) in listOf(null to ten, ten to null, null to null)) {
            val error = validateSwapBalances(input, fee, one, one, one, false, true, resources)
            assertTrue(error is ValidationException)
            assertEquals("text-${R.string.common_error_general_title}", error?.message)
            assertEquals("text-${R.string.portfolio_balance_unavailable}", (error as ValidationException).explanation)
        }
    }

    @Test fun knownZeroRemainsAnInsufficientAmountRatherThanUnavailable() {
        assertTrue(validateSwapBalances(BigDecimal.ZERO, ten, one, one, one, false, false, resources)
            is SpendInsufficientBalanceException)
    }

    @Test fun knownIndependentBalancesRetainSuccessfulAndExactFeeBoundary() {
        assertNull(validateSwapBalances(ten, ten, one, one, one, false, false, resources))
        assertTrue(validateSwapBalances(ten, one, one, one, one, false, false, resources)
            is UnableToPayFeeException)
    }

    @Test fun inputFeeAssetStillReservesItsFee() {
        assertTrue(validateSwapBalances(BigDecimal("2"), ten, one, one, one, true, false, resources)
            is SpendInsufficientBalanceException)
        assertNull(validateSwapBalances(BigDecimal("3"), ten, one, one, one, true, false, resources))
    }

    @Test fun outputFeeAssetRetainsResultedAmountBoundary() {
        assertTrue(validateSwapBalances(ten, BigDecimal.ZERO, one, one, one, false, true, resources)
            is NotEnoughResultedAmountToPayFeeException)
        assertNull(validateSwapBalances(ten, BigDecimal.ZERO, one, ten, one, false, true, resources))
    }
}
