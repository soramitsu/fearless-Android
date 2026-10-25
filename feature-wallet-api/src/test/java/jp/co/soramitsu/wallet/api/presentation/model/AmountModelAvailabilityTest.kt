package jp.co.soramitsu.wallet.api.presentation.model

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class AmountModelAvailabilityTest {
    private fun asset(quoteCurrency: String = "usd"): Asset {
        val config = mockk<jp.co.soramitsu.core.models.Asset> {
            every { symbol } returns "DOT"
            every { precision } returns 2
            every { priceId } returns "dot"
            every { priceProvider } returns null
        }
        val token = Token(config, AssetAmountState.Known(BigDecimal("2.50")), AssetAmountState.Missing,
            FiatValuationContext("usd", FiatCurrency("usd", "$", "Dollar", "")), quoteCurrency, "dot")
        return mockk { every { this@mockk.token } returns token }
    }

    @Test fun unknownAmountKeepsPlaceholderAndNeverAcquiresFiatInEitherFormat() {
        for (detail in listOf(false, true)) {
            val result = mapAmountToAmountModel(null, asset(), 123, detail, "Unavailable")
            assertNull(result.amount)
            assertEquals("Unavailable", result.token)
            assertNull(result.fiat)
            assertEquals(123, result.titleResId)
        }
        assertEquals("—", mapAmountToAmountModel(null, asset()).token)
    }

    @Test fun knownZeroRemainsNumericWithVerifiedFiat() {
        val result = mapAmountToAmountModel(BigDecimal.ZERO, asset())
        assertEquals(BigDecimal.ZERO, result.amount)
        assertEquals(BigDecimal.ZERO.formatCrypto("DOT"), result.token)
        assertEquals(BigDecimal("0.00").formatFiat("$"), result.fiat)
    }

    @Test fun positiveAmountRetainsBothCryptoFormatsButCannotBorrowForeignCurrency() {
        val amount = BigDecimal("12.34")
        for (detail in listOf(false, true)) {
            val trusted = mapAmountToAmountModel(amount, asset(), useDetailCryptoFormat = detail)
            assertEquals(amount, trusted.amount)
            assertEquals(if (detail) amount.formatCryptoDetail("DOT") else amount.formatCrypto("DOT"), trusted.token)
            assertEquals(BigDecimal("30.8500").formatFiat("$"), trusted.fiat)
            val foreign = mapAmountToAmountModel(amount, asset("cad"), useDetailCryptoFormat = detail)
            assertEquals(trusted.token, foreign.token)
            assertNull(foreign.fiat)
        }
    }

    @Test fun plankConversionPreservesAmountAndTitle() {
        val result = mapAmountToAmountModel(BigInteger("1234"), asset(), 456)
        assertEquals(BigDecimal("12.34"), result.amount)
        assertEquals(456, result.titleResId)
        assertEquals(BigDecimal("30.8500").formatFiat("$"), result.fiat)
    }
}
