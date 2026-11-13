package jp.co.soramitsu.common.domain

import io.mockk.mockk
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectedFiatMetadataTest {
    private val dollar = FiatCurrency("usd", "$", "US Dollar", "")

    @Test fun answersFromTheCurrencyListItWasGiven() {
        val selected = SelectedFiat(mockk(relaxed = true)) { id -> dollar.takeIf { it.id == id } }
        assertEquals(dollar, selected.metadata("usd"))
        assertNull(selected.metadata("cad"))
    }

    @Test fun knowsNoCurrencyWithoutAList() {
        assertNull(SelectedFiat(mockk(relaxed = true)).metadata("usd"))
    }
}
