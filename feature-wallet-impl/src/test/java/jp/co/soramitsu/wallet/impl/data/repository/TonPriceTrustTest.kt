package jp.co.soramitsu.wallet.impl.data.repository

import java.math.BigDecimal
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.data.network.ton.JettonPreview
import jp.co.soramitsu.common.data.network.ton.TokenRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonPriceTrustTest {
    private val usd = FiatCurrency("usd", "$", "US Dollar", "")

    @Test
    fun `same symbol verified jettons keep independent master keyed prices`() {
        val first = jetton("master-a", "DUP", "whitelist", "2")
            .toVerifiedTonTokenPrice(usd)!!
        val second = jetton("master-b", "DUP", "verified", "7")
            .toVerifiedTonTokenPrice(usd)!!

        assertEquals("master-a", first.priceId)
        assertEquals(BigDecimal("2"), first.fiatRate)
        assertEquals("master-b", second.priceId)
        assertEquals(BigDecimal("7"), second.fiatRate)
    }

    @Test
    fun `unverified jetton price is rejected even when ticker matches`() {
        assertNull(jetton("master-c", "DUP", "none", "99").toVerifiedTonTokenPrice(usd))
    }

    private fun jetton(
        master: String,
        symbol: String,
        verification: String,
        price: String
    ): JettonBalance {
        return JettonBalance(
            balance = "1",
            walletAddress = AccountAddress(master, false, true),
            jetton = JettonPreview(master, symbol, symbol, 9, "", verification),
            price = TokenRates(
                prices = mapOf("USD" to BigDecimal(price)),
                diff24h = mapOf("USD" to "+1%")
            )
        )
    }
}
