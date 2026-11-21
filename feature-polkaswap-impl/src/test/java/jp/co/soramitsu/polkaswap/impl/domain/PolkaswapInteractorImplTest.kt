package jp.co.soramitsu.polkaswap.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.InsufficientLiquidityException
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.coroutines.EmptyCoroutineContext
import jp.co.soramitsu.core.models.Asset as CoreAsset

class PolkaswapInteractorImplTest {
    private val chainRegistry = mockk<ChainRegistry>()
    private val walletRepository = mockk<WalletRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val repository = mockk<PolkaswapRepository>()
    private val preferences = mockk<Preferences>(relaxed = true)
    private val chainsRepository = mockk<ChainsRepository>()

    private val xor = coreAsset("xor", XOR_ID, "XOR", utility = true)
    private val valToken = coreAsset("val", VAL_ID, "VAL")
    private val pswap = coreAsset("pswap", PSWAP_ID, "PSWAP")
    private val chain = chain(listOf(xor, valToken, pswap))
    private val accountId = ByteArray(32) { 5 }
    private val metaAccount = MetaAccount(
        id = META_ID, chainAccounts = emptyMap(), favoriteChains = emptyMap(), substratePublicKey = null,
        substrateCryptoType = null, substrateAccountId = accountId, ethereumAddress = null, ethereumPublicKey = null,
        tonPublicKey = null, isSelected = true, isBackedUp = true, googleBackupAddress = null, name = "wallet",
        initialized = true
    )
    private val balances = listOf(xor, valToken, pswap).associate { it.id to walletAsset(it) }

    private val interactor = PolkaswapInteractorImpl(
        chainRegistry, walletRepository, accountRepository, repository, preferences, chainsRepository, EmptyCoroutineContext
    )

    private fun coreAsset(id: String, currencyId: String?, symbol: String, utility: Boolean = false) = CoreAsset(
        id = id, name = symbol, symbol = symbol, iconUrl = "", chainId = soraMainChainId, chainName = "SORA",
        chainIcon = null, isTestNet = false, priceId = null, precision = 18, staking = CoreAsset.StakingType.UNSUPPORTED,
        purchaseProviders = null, supportStakingPool = false, isUtility = utility, type = null, currencyId = currencyId,
        existentialDeposit = null, color = null, isNative = null
    )

    private fun chain(assets: List<CoreAsset>): Chain = mockk {
        every { id } returns soraMainChainId
        every { ecosystem } returns Ecosystem.Substrate
        every { this@mockk.assets } returns assets
        every { minSupportedVersion } returns VERSION
        every { externalApi } returns null
    }

    private fun walletAsset(config: CoreAsset): Asset = mockk {
        every { token } returns mockk { every { configuration } returns config }
    }

    @Before fun setUp() {
        coEvery { chainsRepository.getChain(any()) } returns chain
        coEvery { chainsRepository.chainWithAsset(any(), any()) } answers {
            chain to listOf(xor, valToken, pswap).first { it.id == secondArg<String>() }
        }
        coEvery { accountRepository.getSelectedMetaAccount() } returns metaAccount
        coEvery { walletRepository.getAsset(META_ID, accountId, any(), VERSION) } answers {
            balances.getValue(thirdArg<CoreAsset>().id)
        }
    }

    private fun quote(planks: String, vararg route: String) = QuoteResponse(BigInteger(planks), route.toList())

    private fun planks(amount: String) = BigDecimal(amount).scaleByPowerOfTen(18).toBigInteger()

    @Test fun defaultsStartOnSoraMainnetWithTheSmartMarketOfDexZero() {
        val defaults = PolkaswapInteractorImpl(chainRegistry, walletRepository, accountRepository, repository, preferences, chainsRepository)
        assertEquals(soraMainChainId, defaults.polkaswapChainId)
        assertEquals(mapOf(0 to listOf(Market.SMART)), defaults.availableMarkets)
        assertEquals(0, (defaults.bestDexIdFlow.value as LoadingState.Loaded).data)
    }

    @Test fun disclaimerFlagIsKeptInPreferences() = runTest {
        val accepted = MutableStateFlow(true)
        every { preferences.getBoolean(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false) } returns true
        every { preferences.booleanFlow(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false) } returns accepted

        assertTrue(interactor.hasReadDisclaimer)
        interactor.hasReadDisclaimer = false
        verify { preferences.putBoolean(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false) }
        assertTrue(interactor.observeHasReadDisclaimer().first())
    }

    @Test fun onlySoraNetworksCanBeSelected() {
        interactor.setChainId(soraTestChainId)
        assertEquals(soraTestChainId, interactor.polkaswapChainId)
        interactor.setChainId("polkadot")
        interactor.setChainId(null)
        assertEquals(soraTestChainId, interactor.polkaswapChainId)
        interactor.setChainId(soraMainChainId)
        assertEquals(soraMainChainId, interactor.polkaswapChainId)
    }

    @Test fun feeAssetIsTheSelectedAccountsUtilityBalance() = runTest {
        assertSame(balances.getValue("xor"), interactor.getFeeAsset())
        assertSame(balances.getValue("val"), interactor.getAsset("val"))
        coVerify { walletRepository.getAsset(META_ID, accountId, valToken, VERSION) }

        coEvery { chainsRepository.getChain(any()) } returns chain(listOf(valToken))
        assertNull(interactor.getFeeAsset())
        assertEquals(0, interactor.calcFakeFee().signum())
    }

    @Test fun assetFlowFollowsTheSelectedAccount() = runTest {
        every { accountRepository.selectedMetaAccountFlow() } returns flowOf(metaAccount)
        every { walletRepository.assetFlow(META_ID, accountId, pswap, VERSION) } returns flowOf(balances.getValue("pswap"))

        assertSame(balances.getValue("pswap"), interactor.assetFlow("pswap").first())
    }

    @Test fun dexesAndPairAvailabilityAreQueriedOnTheSelectedNetwork() = runTest {
        coEvery { repository.getAvailableDexes(soraMainChainId) } returns listOf(BigInteger.ZERO, BigInteger.ONE)
        coEvery { repository.isPairAvailable(soraMainChainId, XOR_ID, VAL_ID, any()) } answers { arg<Int>(3) != 1 }

        assertEquals(listOf(BigInteger.ZERO, BigInteger.ONE), interactor.getAvailableDexes())
        assertEquals(listOf(0, 2), interactor.getAvailableDexesForPair(XOR_ID, VAL_ID, listOf(0, 1, 2).map(Int::toBigInteger)))
    }

    @Test fun poolReservesWatchTheLiquiditySourcesOfTheMarket() = runTest {
        every { repository.observePoolXYKReserves(any(), any(), any()) } returns flowOf("xyk")
        every { repository.observePoolTBCReserves(any(), any()) } answers { flowOf("tbc-${secondArg<String>()}") }

        val smart = interactor.observePoolReserves(XOR_ID, VAL_ID, Market.SMART).toList()
        assertTrue(smart.isNotEmpty())
        assertTrue(smart.all { it in setOf("xyk", "tbc-$XOR_ID", "tbc-$VAL_ID") })
        verify(exactly = 1) {
            repository.observePoolXYKReserves(soraMainChainId, XOR_ID, VAL_ID)
            repository.observePoolTBCReserves(soraMainChainId, XOR_ID)
            repository.observePoolTBCReserves(soraMainChainId, VAL_ID)
        }

        assertEquals(listOf("xyk"), interactor.observePoolReserves(VAL_ID, PSWAP_ID, Market.XYK).toList())
        verify(exactly = 0) { repository.observePoolTBCReserves(any(), PSWAP_ID) }
        interactor.observePoolReserves(PSWAP_ID, XOR_ID, Market.TBC)
        verify(exactly = 0) { repository.observePoolXYKReserves(any(), PSWAP_ID, XOR_ID) }
        verify(exactly = 1) { repository.observePoolTBCReserves(soraMainChainId, PSWAP_ID) }
    }

    @Test fun exactInputPicksTheDexReturningMostAndAppliesSlippageBelow() = runTest {
        coEvery { repository.getSwapQuote(soraMainChainId, XOR_ID, VAL_ID, planks("2"), WithDesired.INPUT, emptyList(), 0) } returns
            quote(planks("3").toString(), XOR_ID)
        coEvery { repository.getSwapQuote(soraMainChainId, XOR_ID, VAL_ID, planks("2"), WithDesired.INPUT, emptyList(), 1) } returns
            quote(planks("4").toString(), XOR_ID, "unknown", VAL_ID)
        coEvery { repository.getSwapQuote(soraMainChainId, XOR_ID, VAL_ID, planks("2"), WithDesired.INPUT, emptyList(), 2) } returns null

        val details = interactor.calcDetails(
            listOf(0, 1, 2), balances.getValue("xor"), balances.getValue("val"), BigDecimal("2"), WithDesired.INPUT, 0.5, Market.SMART
        ).getOrThrow()!!

        assertEquals(1, details.bestDexId)
        assertEquals(0, BigDecimal("4").compareTo(details.amount))
        assertEquals(0, BigDecimal("3.98").compareTo(details.minMax))
        assertEquals(0, BigDecimal("0.5").compareTo(details.fromTokenOnToToken))
        assertEquals(0, BigDecimal("2").compareTo(details.toTokenOnFromToken))
        assertEquals("XOR  ➝  ?  ➝  VAL", details.route)
        assertSame(balances.getValue("xor"), details.feeAsset)
        assertEquals(1, (interactor.bestDexIdFlow.value as LoadingState.Loaded).data)
    }

    @Test fun exactOutputPicksTheCheapestDexAndAppliesSlippageAbove() = runTest {
        coEvery { repository.getSwapQuote(any(), VAL_ID, PSWAP_ID, planks("2"), WithDesired.OUTPUT, emptyList(), any()) } answers {
            QuoteResponse(if (arg<Int>(6) == 0) planks("3") else planks("4"))
        }

        val details = interactor.calcDetails(
            listOf(0, 1), balances.getValue("val"), balances.getValue("pswap"), BigDecimal("2"), WithDesired.OUTPUT, 1.0, Market.SMART
        ).getOrThrow()!!

        assertEquals(0, details.bestDexId)
        assertEquals(0, BigDecimal("3").compareTo(details.amount))
        assertEquals(0, BigDecimal("3.03").compareTo(details.minMax))
        assertEquals(0, BigDecimal("1.5").compareTo(details.fromTokenOnToToken))
        assertEquals(0, BigDecimal("0.666666666666666667").compareTo(details.toTokenOnFromToken))
        assertNull(details.route)
    }

    @Test fun marketsOutsideTheAvailableSourcesProduceNoQuote() = runTest {
        val result = interactor.calcDetails(
            listOf(0), balances.getValue("xor"), balances.getValue("val"), BigDecimal.ONE, WithDesired.INPUT, 0.5, Market.XYK
        )

        assertNull(result.getOrThrow())
        coVerify(exactly = 0) { repository.getSwapQuote(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun missingLiquidityFailsAndRestoresThePreviousBestDex() = runTest {
        coEvery { repository.getSwapQuote(any(), any(), any(), any(), any(), any(), any()) } returns null
        val before = interactor.bestDexIdFlow.value

        val result = interactor.calcDetails(
            listOf(0, 1), balances.getValue("xor"), balances.getValue("val"), BigDecimal.ONE, WithDesired.INPUT, 0.5, Market.SMART
        )

        assertTrue(result.exceptionOrNull() is InsufficientLiquidityException)
        assertSame(before, interactor.bestDexIdFlow.value)
        coVerify(exactly = 2) { repository.getSwapQuote(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun zeroQuoteMeansNothingToSwap() = runTest {
        coEvery { repository.getSwapQuote(any(), any(), any(), any(), any(), any(), any()) } returns quote("0")

        val result = interactor.calcDetails(
            listOf(3), balances.getValue("xor"), balances.getValue("val"), BigDecimal.ONE, WithDesired.INPUT, 0.5, Market.SMART
        )

        assertNull(result.getOrThrow())
        assertEquals(3, (interactor.bestDexIdFlow.value as LoadingState.Loaded).data)
    }

    @Test fun fetchedSourcesAlwaysOfferSmartAndUnlockSelectedMarkets() = runTest {
        coEvery { repository.getAvailableSources(soraMainChainId, XOR_ID, VAL_ID, listOf(0, 1)) } returns
            mapOf(0 to listOf(Market.XYK), 1 to listOf(Market.TBC, Market.XYK))

        val sources = interactor.fetchAvailableSources(balances.getValue("xor"), balances.getValue("val"), listOf(0, 1))

        assertEquals(setOf(Market.SMART, Market.XYK, Market.TBC), sources)
        assertEquals(mapOf(0 to listOf(Market.SMART, Market.XYK), 1 to listOf(Market.SMART, Market.TBC, Market.XYK)), interactor.availableMarkets)

        coEvery { repository.getSwapQuote(any(), any(), any(), any(), any(), listOf(Market.XYK), 0) } returns quote(planks("1").toString())
        val details = interactor.calcDetails(
            listOf(0), balances.getValue("xor"), balances.getValue("val"), BigDecimal.ONE, WithDesired.INPUT, 0.5, Market.XYK
        ).getOrThrow()
        assertEquals(0, BigDecimal.ONE.compareTo(details!!.amount))
    }

    @Test fun feeEstimatesFilterBySelectedMarket() = runTest {
        coEvery { repository.estimateSwapFee(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns BigInteger.TEN

        assertEquals(BigInteger.TEN, interactor.estimateSwapFee(1, XOR_ID, VAL_ID, BigInteger("5"), Market.SMART, WithDesired.INPUT))
        assertEquals(BigInteger.TEN, interactor.estimateSwapFee(2, XOR_ID, VAL_ID, BigInteger("6"), Market.TBC, WithDesired.OUTPUT))

        coVerify {
            repository.estimateSwapFee(soraMainChainId, 1, XOR_ID, VAL_ID, BigInteger("5"), BigInteger("5"), "Disabled", emptyList(), WithDesired.INPUT)
            repository.estimateSwapFee(
                soraMainChainId, 2, XOR_ID, VAL_ID, BigInteger("6"), BigInteger("6"), "AllowSelected",
                listOf("MulticollateralBondingCurvePool"), WithDesired.OUTPUT
            )
        }
    }

    @Test fun fakeFeeIsAOnePlankXorSelfSwapInXor() = runTest {
        coEvery {
            repository.estimateSwapFee(soraMainChainId, 0, XOR_ID, XOR_ID, BigInteger.ONE, BigInteger.ONE, "Disabled", emptyList(), WithDesired.INPUT)
        } returns planks("0.007")

        assertEquals(0, BigDecimal("0.007").compareTo(interactor.calcFakeFee()))
    }

    @Test fun feeAssetWithoutCurrencyIdHasNoFakeFee() = runTest {
        val bareXor = coreAsset("xor", null, "XOR", utility = true)
        coEvery { chainsRepository.getChain(any()) } returns chain(listOf(bareXor))
        coEvery { walletRepository.getAsset(META_ID, accountId, bareXor, VERSION) } returns walletAsset(bareXor)
        coEvery { chainsRepository.chainWithAsset(any(), "xor") } returns (chain(listOf(bareXor)) to bareXor)

        assertEquals(0, interactor.calcFakeFee().signum())
        coVerify(exactly = 0) { repository.estimateSwapFee(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun swapIsSubmittedOnTheSelectedNetwork() = runTest {
        coEvery { repository.swap(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success("0xhash")
        interactor.setChainId(soraTestChainId)

        val result = interactor.swap(1, XOR_ID, VAL_ID, BigInteger.TEN, BigInteger.ONE, "Disabled", emptyList(), WithDesired.INPUT)

        assertEquals("0xhash", result.getOrThrow())
        coVerify { repository.swap(soraTestChainId, 1, XOR_ID, VAL_ID, BigInteger.TEN, BigInteger.ONE, "Disabled", emptyList(), WithDesired.INPUT) }
        assertFalse(result.isFailure)
    }

    private companion object {
        const val META_ID = 4L
        const val VERSION = "2.0"
        const val XOR_ID = "0x0200000000000000000000000000000000000000000000000000000000000000"
        const val VAL_ID = "0x0200040000000000000000000000000000000000000000000000000000000000"
        const val PSWAP_ID = "0x0200050000000000000000000000000000000000000000000000000000000000"
    }
}
