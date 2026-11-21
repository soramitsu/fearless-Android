package jp.co.soramitsu.polkaswap.api.presentation.models

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.feature_polkaswap_api.R
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class SwapDetailsViewStateTest {
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "s:${firstArg<Int>()}" } }
    private val xor = asset("xor", "0x0200000", "#FF0000", fiat = BigDecimal("2"))
    private val valToken = asset("val", "0x0200040", "#00FF00", fiat = BigDecimal("0.5"))

    private fun asset(symbol: String, currencyId: String?, color: String, fiat: BigDecimal?): Asset {
        val config = mockk<CoreAsset> {
            every { this@mockk.symbol } returns symbol
            every { this@mockk.currencyId } returns currencyId
            every { iconUrl } returns "https://icons.test/$symbol.svg"
            every { this@mockk.color } returns color
            every { precision } returns 18
        }
        return mockk {
            every { token.configuration } returns config
            every { token.fiatAmount(any()) } answers { fiat?.multiply(firstArg()) }
            every { token.fiatSymbol } returns "$"
        }
    }

    private val details = SwapDetails(
        amount = BigDecimal("30"), minMax = BigDecimal("29.85"), fromTokenOnToToken = BigDecimal("0.3333"),
        toTokenOnFromToken = BigDecimal("3"), feeAsset = xor, bestDexId = 0, route = "XOR ➝ VAL"
    )

    @Test fun exactInputShowsMinimumReceivedInTheTargetToken() {
        val state = detailsToViewState(resources, BigDecimal("10"), xor, valToken, details, WithDesired.INPUT)

        assertEquals("0x0200000", state.fromTokenId)
        assertEquals("0x0200040", state.toTokenId)
        assertEquals("XOR", state.fromTokenName)
        assertEquals("VAL", state.toTokenName)
        assertEquals(BigDecimal("10").formatCryptoDetail("xor"), state.fromTokenAmount)
        assertEquals(BigDecimal("30").formatCryptoDetail("val"), state.toTokenAmount)
        assertEquals("s:${R.string.common_min_received}", state.minmaxTitle)
        assertEquals(BigDecimal("29.85").formatCryptoDetail("val"), state.toTokenMinReceived)
        assertEquals(BigDecimal("14.925").formatFiat("$"), state.toFiatMinReceived)
        assertEquals(BigDecimal("0.3333").formatCryptoDetail(), state.fromTokenOnToToken)
        assertEquals(BigDecimal("3").formatCryptoDetail(), state.toTokenOnFromToken)
        assertEquals("XOR ➝ VAL", state.route)
        val fromIcon = state.fromTokenImage as GradientIconState.Remote
        assertEquals("https://icons.test/xor.svg", fromIcon.url)
        assertEquals("#FF0000", fromIcon.color)
        assertEquals("https://icons.test/val.svg", (state.toTokenImage as GradientIconState.Remote).url)
    }

    @Test fun exactOutputShowsMaximumSoldInTheSourceToken() {
        val state = detailsToViewState(resources, BigDecimal("10"), xor, valToken, details, WithDesired.OUTPUT)

        assertEquals(BigDecimal("30").formatCryptoDetail("xor"), state.fromTokenAmount)
        assertEquals(BigDecimal("10").formatCryptoDetail("val"), state.toTokenAmount)
        assertEquals("s:${R.string.polkaswap_maximum_sold}", state.minmaxTitle)
        assertEquals(BigDecimal("29.85").formatCryptoDetail("xor"), state.toTokenMinReceived)
        assertEquals(BigDecimal("59.70").formatFiat("$"), state.toFiatMinReceived)
    }

    @Test fun unpricedTokenLeavesTheFiatValueEmpty() {
        val unpriced = asset("pswap", "0x0200050", "#0000FF", fiat = null)

        val state = detailsToViewState(resources, BigDecimal.ONE, xor, unpriced, details, WithDesired.INPUT)

        assertEquals("", state.toFiatMinReceived)
    }

    @Test fun assetsWithoutCurrencyIdCannotBeSwapped() {
        val unregistered = asset("dot", null, "#000000", fiat = null)

        assertThrows(IllegalArgumentException::class.java) {
            detailsToViewState(resources, BigDecimal.ONE, xor, unregistered, details, WithDesired.INPUT)
        }
    }

    @Test fun quoteIsConvertedFromPlanksWithTheRoute() {
        val config = mockk<CoreAsset> { every { precision } returns 18 }

        val quote = QuoteResponse(BigInteger("1500000000000000000"), listOf("0x01", "0x02")).toModel(config)

        assertEquals(0, BigDecimal("1.5").compareTo(quote.amount))
        assertEquals(listOf("0x01", "0x02"), quote.route)
        assertEquals(null, QuoteResponse(BigInteger.ONE).toModel(config).route)
    }
}
