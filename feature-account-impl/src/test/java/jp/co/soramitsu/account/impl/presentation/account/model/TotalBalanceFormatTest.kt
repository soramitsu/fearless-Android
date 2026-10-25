package jp.co.soramitsu.account.impl.presentation.account.model

import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.utils.formatFiat
import org.junit.Assert.*
import org.junit.Test

class TotalBalanceFormatTest {
    private fun ready(amount: BigDecimal?, subtotal: BigDecimal?) = PortfolioBalance(
        amount, subtotal, null, null, 1, if (subtotal != null) 1 else 0, if (amount == null) 1 else 0, 0
    ).let { TotalBalance.Ready(mockk(), emptyMap(), "eur", "EUR ", true, it, it, it) }

    @Test fun bareFormatOnlyReturnsCompleteCapturedValue() {
        assertEquals(BigDecimal.TEN.formatFiat("EUR "), ready(BigDecimal.TEN, BigDecimal.TEN).format())
        assertNull(ready(null, BigDecimal.TEN).format())
        assertNull(ready(null, null).format())
        assertNull(TotalBalance.Loading(7, "eur").format())
        assertNull(TotalBalance.Unavailable.format())
    }
}
