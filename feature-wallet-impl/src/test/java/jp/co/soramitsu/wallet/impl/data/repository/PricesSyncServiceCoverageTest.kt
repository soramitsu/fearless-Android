package jp.co.soramitsu.wallet.impl.data.repository

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.data.network.ton.JettonPreview
import jp.co.soramitsu.common.data.network.ton.JettonsBalances
import jp.co.soramitsu.common.data.network.ton.TokenRate
import jp.co.soramitsu.common.data.network.ton.TokenRates
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.TonSyncDataRepository
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PricesSyncServiceCoverageTest {

    private val usd = FiatCurrency("usd", "$", "US Dollar", "")
    private val tokenPriceDao = mockk<TokenPriceDao>(relaxed = true)
    private val coingecko = mockk<CoingeckoPricesService>()
    private val chainlink = mockk<ChainlinkPricesService>()
    private val tonPrices = mockk<TonPricesService>()
    private val selectedFiat = mockk<SelectedFiat>()
    private val fiatCurrencies = mockk<GetAvailableFiatCurrencies>()
    private val chainsRepository = mockk<ChainsRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val tonSync = mockk<TonSyncDataRepository>()
    private val tonChain = WalletTestModels.chain(id = "ton-coverage", ecosystem = Ecosystem.Ton)
    private val tonKey = ByteArray(32) { 1 }

    @Before
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun restoreLog() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `sync stores coingecko, chainlink and ton prices together on every completed run`(): Unit = runBlocking {
        val dot = TokenPriceLocal("dot", "$", BigDecimal("6"), BigDecimal("1.5"), fiatCurrencyId = "usd")
        val ksm = TokenPriceLocal("ksm", "$", BigDecimal("20"), null, fiatCurrencyId = "usd")
        val feed = TokenPriceLocal("0xfeed", "$", BigDecimal("1"), BigDecimal("1.5"), fiatCurrencyId = "usd")
        val ton = TokenPriceLocal("the-open-network", "$", BigDecimal("5"), BigDecimal("-1"), fiatCurrencyId = "usd")
        every { selectedFiat.get() } returns "usd"
        every { fiatCurrencies["usd"] } returns usd
        coEvery { coingecko.load(usd) } returns listOf(dot, ksm)
        coEvery { chainlink.load(usd, any()) } returns listOf(feed)
        coEvery { tonPrices.load(usd) } returns listOf(ton)
        val service = syncService()

        service.sync()
        service.sync()

        coVerify(exactly = 2) { tokenPriceDao.insertTokensPrice(listOf(dot, ksm, feed, ton)) }
        coVerify(exactly = 2) { chainlink.load(usd, mapOf("dot" to BigDecimal("1.5"))) }
        verify { Log.d("PricesSyncService", match { it.startsWith("prices sync completed") }) }
    }

    @Test
    fun `sync does nothing for a currency the app does not know`(): Unit = runBlocking {
        every { selectedFiat.get() } returns "xyz"
        every { fiatCurrencies["xyz"] } returns null

        syncService().sync()

        coVerify(exactly = 0) { coingecko.load(any()) }
        coVerify(exactly = 0) { tokenPriceDao.insertTokensPrice(any()) }
    }

    @Test
    fun `ton prices keep verified jettons and the ton coin quote`(): Unit = runBlocking {
        val accountId = tonKey.tonAccountId(false)
        coEvery { chainsRepository.getChains() } returns listOf(tonChain, WalletTestModels.chain())
        coEvery { accountRepository.allMetaAccounts() } returns listOf(
            WalletTestModels.metaAccount(id = 1L, ton = tonKey),
            WalletTestModels.metaAccount(id = 2L, ton = null)
        )
        coEvery { tonSync.getJettonBalances(tonChain, accountId) } returns JettonsBalances(
            listOf(jetton("master-a", "whitelist", "2", "+1%"), jetton("master-b", "none", "9", "+3%"))
        )
        coEvery { tonSync.getTonCoinPrices() } returns TokenRate(
            prices = mapOf("USD" to 5.25),
            diff24h = mapOf("USD" to "−2.5%"),
            diff7d = emptyMap(),
            diff30d = emptyMap()
        )

        val prices = tonPricesService().load(usd)

        assertEquals(
            listOf(
                TokenPriceLocal("master-a", "$", BigDecimal("2"), BigDecimal("1"), fiatCurrencyId = "usd"),
                TokenPriceLocal("the-open-network", "$", BigDecimal("5.25"), BigDecimal("-2.5"), fiatCurrencyId = "usd")
            ),
            prices
        )
    }

    @Test
    fun `ton prices are empty without ton accounts and skip failed or incomplete quotes`(): Unit = runBlocking {
        val withoutTon = WalletTestModels.metaAccount(ton = null)
        coEvery { chainsRepository.getChains() } returns listOf(tonChain)
        coEvery { accountRepository.allMetaAccounts() } returnsMany listOf(
            listOf(withoutTon),
            listOf(WalletTestModels.metaAccount(ton = tonKey))
        )
        coEvery { tonSync.getJettonBalances(any(), any()) } throws IllegalStateException("indexer down")
        coEvery { tonSync.getTonCoinPrices() } returns TokenRate(
            prices = mapOf("USD" to 5.0),
            diff24h = emptyMap(),
            diff7d = emptyMap(),
            diff30d = emptyMap()
        )
        val service = tonPricesService()

        assertTrue(service.load(usd).isEmpty())
        coVerify(exactly = 0) { tonSync.getTonCoinPrices() }
        assertTrue(service.load(usd).isEmpty())
        coVerify(exactly = 1) { tonSync.getTonCoinPrices() }
    }

    @Test
    fun `chainlink skips feeds without a price`(): Unit = runBlocking {
        val ethereum = mockk<EthereumRemoteSource>()
        val provider = WalletTestModels.chain(id = "eth-feeds", ecosystem = Ecosystem.Ethereum).copy(chainlinkProvider = true)
        val dot = WalletTestModels.asset(id = "dot", priceId = "polkadot")
            .copy(priceProvider = Asset.PriceProvider("0xdotfeed", Asset.PriceProviderType.Chainlink, 8))
        val ksm = WalletTestModels.asset(id = "ksm", chainId = "kusama", priceId = "kusama")
            .copy(priceProvider = Asset.PriceProvider("0xksmfeed", Asset.PriceProviderType.Chainlink, 8))
        coEvery { chainsRepository.getChains() } returns listOf(provider, WalletTestModels.chain(assets = listOf(dot, ksm)))
        coEvery { ethereum.fetchPriceFeed("eth-feeds", "0xdotfeed") } returns BigInteger.valueOf(612_000_000)
        coEvery { ethereum.fetchPriceFeed("eth-feeds", "0xksmfeed") } returns null

        val prices = ChainlinkPricesService(ethereum, chainsRepository).load(usd, mapOf("polkadot" to BigDecimal("1.2")))

        assertEquals(listOf(TokenPriceLocal("0xdotfeed", "$", BigDecimal("6.12000000"), BigDecimal("1.2"), fiatCurrencyId = "usd")), prices)
    }

    private fun syncService() = PricesSyncService(tokenPriceDao, coingecko, chainlink, tonPrices, selectedFiat, fiatCurrencies)

    private fun tonPricesService() = TonPricesService(tonSync, chainsRepository, accountRepository)

    private fun jetton(master: String, verification: String, price: String, diff: String) = JettonBalance(
        balance = "1",
        walletAddress = AccountAddress(master, false, true),
        jetton = JettonPreview(master, master.uppercase(), master.uppercase(), 9, "", verification),
        price = TokenRates(prices = mapOf("USD" to BigDecimal(price)), diff24h = mapOf("USD" to diff))
    )
}
