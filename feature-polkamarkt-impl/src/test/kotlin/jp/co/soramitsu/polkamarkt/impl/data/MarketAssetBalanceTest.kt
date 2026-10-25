package jp.co.soramitsu.polkamarkt.impl.data

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MarketAssetBalanceTest {
    private fun holding(id: String, balance: BigDecimal?): Asset = mockk {
        every { token.configuration.currencyId } returns id
        every { transferable } returns balance
    }

    @Test
    fun missingAndUnavailableAmountsCannotProveFundingOrZero() {
        assertNull(emptyList<Asset>().marketAssetBalance("xor"))
        assertNull(listOf(holding("xor", null)).marketAssetBalance("xor"))
        assertNull(listOf(holding("kusd", BigDecimal.TEN)).marketAssetBalance("xor"))
    }

    @Test
    fun exactKnownZeroAndPositiveAmountsRetainTheirValues() {
        assertEquals(BigDecimal.ZERO, listOf(holding("XOR", BigDecimal.ZERO)).marketAssetBalance("xor"))
        assertEquals(BigDecimal.TEN, listOf(holding("xor", BigDecimal.TEN)).marketAssetBalance("XOR"))
    }

    @Test
    fun duplicateIdentityCannotChooseAnArbitraryFundedRow() {
        val rows = listOf(holding("xor", null), holding("XOR", BigDecimal.TEN))
        assertNull(rows.marketAssetBalance("xor"))
        assertNull(rows.reversed().marketAssetBalance("xor"))
    }
}
