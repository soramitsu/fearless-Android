package jp.co.soramitsu.staking.impl.presentation.common

import java.math.BigDecimal
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.resources.ResourceManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class StakingBalanceAvailabilityTest {
    private val resources = mock<ResourceManager> {
        on { getString(jp.co.soramitsu.common.R.string.common_error_general_title) } doReturn "Error"
        on { getString(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable) } doReturn "Unavailable"
    }

    @Test fun missingBalanceFailsWithLocalizedUnavailableMessage() {
        val error = runCatching { requireStakingBalance<BigDecimal>(null, resources) }.exceptionOrNull()
        assertTrue(error is ValidationException)
        assertEquals("Error", error?.message)
        assertEquals("Unavailable", (error as ValidationException).explanation)
    }

    @Test fun explicitZeroAndPositiveAmountsRetainTheirValue() {
        for (amount in listOf(BigDecimal.ZERO, BigDecimal("12.75"))) {
            assertSame(amount, requireStakingBalance(amount, resources))
        }
        verifyNoInteractions(resources)
    }

    @Test fun unavailableFailureFactoryPreservesBothLocalizedFields() {
        val error = stakingBalanceUnavailable(resources)
        assertEquals("Error", error.message)
        assertEquals("Unavailable", error.explanation)
    }
}
