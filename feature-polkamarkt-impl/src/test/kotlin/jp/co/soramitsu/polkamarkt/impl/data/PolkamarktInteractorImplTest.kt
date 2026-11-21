package jp.co.soramitsu.polkamarkt.impl.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.config.MutationAuthorizationStore
import jp.co.soramitsu.common.data.network.config.MutationCapability
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.polkamarkt.api.PolkamarktAccountCapability
import jp.co.soramitsu.polkamarkt.api.PolkamarktClaimable
import jp.co.soramitsu.polkamarkt.api.PolkamarktDisplayStatus
import jp.co.soramitsu.polkamarkt.api.PolkamarktHistoryPoint
import jp.co.soramitsu.polkamarkt.api.PolkamarktMarket
import jp.co.soramitsu.polkamarkt.api.PolkamarktMutation
import jp.co.soramitsu.polkamarkt.api.PolkamarktOutcome
import jp.co.soramitsu.polkamarkt.api.PolkamarktPosition
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuoteRequest
import jp.co.soramitsu.polkamarkt.api.PolkamarktRuntimeCapabilities
import jp.co.soramitsu.polkamarkt.api.PolkamarktTrade
import jp.co.soramitsu.polkamarkt.api.PolkamarktTradeMode
import jp.co.soramitsu.polkamarkt.impl.di.PolkamarktModule
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class PolkamarktInteractorImplTest {
    private val endpoint = "https://indexer.example/graphql"
    private val chain = mockk<Chain>()
    private val chainRegistry = mockk<ChainRegistry>()
    private val indexer = mockk<PolkamarktIndexer>()
    private val runtime = mockk<PolkamarktRuntime>()
    private val accounts = mockk<AccountRepository>()
    private val wallets = mockk<WalletRepository>()
    private val extrinsics = mockk<ExtrinsicService>()
    private val polkaswap = mockk<PolkaswapInteractor>()
    private val toggles = mockk<ProductFeatureToggleStore>()
    private val authorization = mockk<MutationAuthorizationStore>()
    private val wallet = mockk<MetaAccount>()
    private val accountId = ByteArray(32) { 1 }
    private val capabilities = PolkamarktRuntimeCapabilities(
        browse = true, quoteBuy = true, quoteSell = true, marketState = true, buy = true, sell = true,
        claimMarket = true, claimCreatorFees = true, reasons = listOf("Runtime note")
    )
    private val openMarket = PolkamarktMarket(
        id = "7", title = "Rain tomorrow?", description = "Weather", category = "Weather",
        displayStatus = PolkamarktDisplayStatus.Open, runtimeOnly = true, volumeUsd = "5"
    )
    private val resolvedMarket = openMarket.copy(
        id = "8", title = "Resolved", displayStatus = PolkamarktDisplayStatus.Resolved,
        runtimeOnly = false
    )
    private val history = PolkamarktHistoryPoint("h1", "7", probabilityPercent = "40")
    private val position = PolkamarktPosition("p1", "7", yesShares = "3000000000000000000", noShares = "0.5")
    private val trade = PolkamarktTrade("t1", "7", side = "buy")
    private val claim = PolkamarktClaimable("8", "cnAlice", "Resolved", traderPayout = "5")
    private val interactor = PolkamarktInteractorImpl(
        chainRegistry, indexer, runtime, accounts, wallets, extrinsics, polkaswap, toggles, authorization
    )

    private val buy = PolkamarktMutation.Trade("7", PolkamarktTradeMode.Buy, PolkamarktOutcome.Yes, "1.5", "1.98")

    private fun asset(
        currency: String,
        amount: BigDecimal?,
        assetChain: String = soraMainChainId
    ): Asset = mockk {
        every { chainId } returns assetChain
        every { token.configuration.currencyId } returns currency
        every { transferable } returns amount
    }

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

    private fun balances(xor: BigDecimal?, kusd: BigDecimal?) {
        coEvery { wallets.getAssets(1L) } returns listOfNotNull(
            xor?.let { asset(XOR_ASSET_ID, it) },
            kusd?.let { asset(KUSD_ASSET_ID, it) },
            asset(XOR_ASSET_ID, BigDecimal.TEN, assetChain = "other-chain")
        )
    }

    @Before fun setUp() {
        mockkStatic("jp.co.soramitsu.account.api.domain.model.MetaAccountKt")
        every { chain.id } returns soraMainChainId
        every {
            chain.externalApi
        } returns Chain.ExternalApi(null, Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.SUBQUERY, endpoint), null)
        coEvery { chainRegistry.getChain(soraMainChainId) } returns chain
        every { wallet.id } returns 1L
        every { wallet.address(chain) } returns "cnAlice"
        every { wallet.accountId(chain) } returns accountId
        coEvery { accounts.getSelectedMetaAccount() } returns wallet
        coEvery { accounts.getMetaAccount(1L) } returns wallet
        coEvery { extrinsics.hasLocalSubstrateKey(chain, accountId) } returns true
        coEvery { runtime.currentBlock() } returns "100"
        coEvery { runtime.capabilities() } returns capabilities
        coEvery { runtime.markets(any()) } returns listOf(openMarket)
        coEvery { runtime.claimable(any(), any()) } returns listOf(claim)
        coEvery { indexer.markets(endpoint, "100") } returns listOf(openMarket.copy(runtimeOnly = false), resolvedMarket)
        coEvery { indexer.history(endpoint, any()) } returns listOf(history)
        coEvery { indexer.activity(endpoint, "cnAlice") } returns ParsedActivity(listOf(position), listOf(trade))
        balances(BigDecimal.TEN, BigDecimal("50"))
        every { toggles.polkamarktMutationsEnabled } returns true
        every { polkaswap.hasReadDisclaimer } returns true
    }

    @After fun tearDown() = unmockkAll()

    @Test fun snapshotCombinesRuntimeIndexerAndFundedSignableAccount() = runTest {
        val snapshot = interactor.snapshot("7")

        assertEquals("100", snapshot.currentBlock)
        assertEquals(listOf("7", "8"), snapshot.markets.map { it.id })
        assertTrue("runtime market keeps its authority marker", snapshot.markets.first().runtimeOnly)
        assertEquals(listOf(history), snapshot.history)
        assertEquals(listOf(position), snapshot.positions)
        assertEquals(listOf(trade), snapshot.trades)
        assertEquals(listOf(claim), snapshot.claimable)
        assertSame(capabilities, snapshot.capabilities)
        assertEquals(PolkamarktAccountCapability("cnAlice", signable = true, hasKusd = true, hasXorForFees = true), snapshot.account)
        assertFalse(snapshot.indexerStale)
        assertEquals(listOf("Runtime note"), snapshot.warnings)
        // Claims are checked for the account's positions and for every settled market.
        coVerify { runtime.claimable("cnAlice", listOf("7", "8")) }
    }

    @Test fun missingIndexerAndFailedRuntimeCatalogBecomeWarningsWithoutAnAccount() = runTest {
        every { chain.externalApi } returns null
        every { wallet.address(chain) } returns null
        every { wallet.accountId(chain) } returns null
        coEvery { runtime.markets(any()) } throws IllegalStateException("Runtime catalog offline")

        val snapshot = interactor.snapshot(null)

        assertTrue(snapshot.markets.isEmpty())
        assertTrue(snapshot.history.isEmpty())
        assertTrue(snapshot.positions.isEmpty())
        assertTrue(snapshot.claimable.isEmpty())
        assertTrue(snapshot.indexerStale)
        assertEquals(
            listOf("SORA Polkamarkt indexer is not configured.", "Runtime catalog offline", "Runtime note"),
            snapshot.warnings
        )
        assertEquals("Add a SORA account to trade or claim.", snapshot.account.reason)
        assertNull(snapshot.account.address)
        assertFalse(snapshot.account.signable)
        coVerify(exactly = 0) { runtime.claimable(any(), any()) }
    }

    @Test fun failuresWithoutMessagesUseDefaultWarnings() = runTest {
        coEvery { indexer.markets(endpoint, "100") } throws RuntimeException()
        coEvery { runtime.markets(any()) } throws RuntimeException()

        val snapshot = interactor.snapshot("7")

        assertTrue(snapshot.indexerStale)
        assertTrue("Polkamarkt indexer is unavailable." in snapshot.warnings)
        assertTrue("SORA runtime market catalog is unavailable." in snapshot.warnings)
    }

    @Test fun accountReasonExplainsTheFirstMissingRequirement() = runTest {
        coEvery { extrinsics.hasLocalSubstrateKey(chain, accountId) } returns false
        assertEquals(
            "This SORA account is watch-only or requires an unsupported external signer.",
            interactor.snapshot().account.reason
        )

        coEvery { extrinsics.hasLocalSubstrateKey(chain, accountId) } returns true
        balances(xor = null, kusd = BigDecimal.ONE)
        assertEquals("Balance unavailable. Refresh before trading or claiming.", interactor.snapshot().account.reason)

        balances(xor = BigDecimal.ZERO, kusd = BigDecimal.ONE)
        val noXor = interactor.snapshot().account
        assertEquals("Add XOR to pay SORA network fees.", noXor.reason)
        assertFalse(noXor.hasXorForFees)

        balances(xor = BigDecimal.ONE, kusd = BigDecimal.ZERO)
        val noKusd = interactor.snapshot().account
        assertEquals("Add KUSD collateral to buy market shares.", noKusd.reason)
        assertTrue(noKusd.hasXorForFees)
        assertFalse(noKusd.hasKusd)
    }

    @Test fun signabilityLookupFailureIsTreatedAsWatchOnly() = runTest {
        coEvery { accounts.getMetaAccount(1L) } throws IllegalStateException("db closed")
        assertFalse(interactor.snapshot().account.signable)
    }

    @Test fun buyQuoteReadsRuntimeAmountsAndAppliesOnePercentSlippage() = runTest {
        val request = PolkamarktQuoteRequest("7", PolkamarktTradeMode.Buy, PolkamarktOutcome.Yes, "1.5")
        coEvery { runtime.quote(request) } returns json(
            """{"marketId":"7","outcome":"No","sharesOut":"2000000000000000000",
               "collateralIn":"1500000000000000000","feeAmount":"10000000000000000"}"""
        )
        val form = slot<suspend ExtrinsicBuilder.() -> Unit>()
        coEvery { extrinsics.estimateFee(chain, false, capture(form)) } returns BigInteger("3000000000000000")

        val quote = interactor.quote(request)

        assertEquals("7", quote.marketId)
        assertEquals(PolkamarktTradeMode.Buy, quote.mode)
        assertEquals(PolkamarktOutcome.No, quote.outcome)
        assertEquals("1.5", quote.amount)
        assertEquals("0.01", quote.feeAmount)
        assertEquals("0.003", quote.networkFee)
        assertEquals("2", quote.resultAmount)
        assertEquals("1.98", quote.minimumResult)
        val args = builtCall(form, "buy")
        assertEquals(BigInteger("7"), args["market_id"])
        assertEquals(DictEnum.Entry("Yes", null), args["outcome"])
        assertEquals(BigInteger("1500000000000000000"), args["collateral_in"])
        assertEquals(BigInteger("1980000000000000000"), args["min_shares_out"])
    }

    @Test fun sellQuoteAcceptsSnakeCaseFieldsAndFallsBackToTheRequest() = runTest {
        val request = PolkamarktQuoteRequest("9", PolkamarktTradeMode.Sell, PolkamarktOutcome.No, "2")
        coEvery { runtime.quote(request) } returns json("""{"collateral_out":"900000000000000000"}""")
        val form = slot<suspend ExtrinsicBuilder.() -> Unit>()
        coEvery { extrinsics.estimateFee(chain, false, capture(form)) } returns BigInteger("1000000000000000")

        val quote = interactor.quote(request)

        assertEquals("9", quote.marketId)
        assertEquals(PolkamarktOutcome.No, quote.outcome)
        assertEquals("2", quote.amount)
        assertEquals("0", quote.feeAmount)
        assertEquals("0.9", quote.resultAmount)
        assertEquals("0.891", quote.minimumResult)
        val args = builtCall(form, "sell")
        assertEquals(BigInteger("2000000000000000000"), args["shares_in"])
        assertEquals(BigInteger("891000000000000000"), args["min_collateral_out"])
    }

    @Test fun quoteWithoutAResultIsRejected() = runTest {
        val request = PolkamarktQuoteRequest("7", PolkamarktTradeMode.Buy, PolkamarktOutcome.Yes, "1")
        coEvery { runtime.quote(request) } returns json("""{"marketId":"7"}""")

        val error = runCatching { interactor.quote(request) }.exceptionOrNull()

        assertEquals("SORA runtime returned no quote.", error?.message)
        coVerify(exactly = 0) { extrinsics.estimateFee(any<Chain>(), any<Boolean>(), any()) }
    }

    @Test fun disabledTogglesAndUnreadDisclaimerRefuseMutationsBeforeAnyRead() = runTest {
        every { toggles.polkamarktMutationsEnabled } returns false
        assertEquals(
            "Polkamarkt actions are temporarily disabled.",
            interactor.mutate(PolkamarktMutation.ClaimMarket("8")).exceptionOrNull()?.message
        )

        every { toggles.polkamarktMutationsEnabled } returns true
        every { polkaswap.hasReadDisclaimer } returns false
        assertEquals(
            "Accept the Polkaswap and SORA risk disclaimer first.",
            interactor.mutate(PolkamarktMutation.ClaimMarket("8")).exceptionOrNull()?.message
        )
        coVerify(exactly = 0) { accounts.getSelectedMetaAccount() }
    }

    @Test fun buyTradeIsSubmittedWithTheFreshQuoteAndPolkamarktAuthorization() = runTest {
        stubBuyQuote()
        val authorize = slot<(String) -> MutationExecutionGuard>()
        val form = slot<suspend ExtrinsicBuilder.() -> Unit>()
        coEvery {
            extrinsics.submitAuthorizedExtrinsic(chain, accountId, capture(authorize), false, null, null, capture(form))
        } returns Result.success("0xaccepted")
        val guard = mockk<MutationExecutionGuard>()
        val intent = "ab".repeat(32)
        every { authorization.acquire(MutationCapability.POLKAMARKT, intent) } returns guard

        val result = interactor.mutate(buy)

        assertEquals("0xaccepted", result.getOrThrow())
        assertSame(guard, authorize.captured(intent))
        verify { authorization.acquire(MutationCapability.POLKAMARKT, intent) }
        val args = builtCall(form, "buy")
        assertEquals(BigInteger("1500000000000000000"), args["collateral_in"])
        assertEquals(BigInteger("1980000000000000000"), args["min_shares_out"])
        coVerify { runtime.markets("100") }
    }

    @Test fun sellTradeCountsExactIndexedSharesForTheOutcome() = runTest {
        val sell = PolkamarktMutation.Trade("7", PolkamarktTradeMode.Sell, PolkamarktOutcome.Yes, "1", "0.891")
        coEvery { runtime.quote(PolkamarktQuoteRequest("7", PolkamarktTradeMode.Sell, PolkamarktOutcome.Yes, "1")) } returns
            json("""{"collateralOut":"900000000000000000","sharesIn":"1000000000000000000"}""")
        coEvery { extrinsics.estimateFee(chain, false, any()) } returns BigInteger("3000000000000000")
        val form = slot<suspend ExtrinsicBuilder.() -> Unit>()
        coEvery {
            extrinsics.submitAuthorizedExtrinsic(chain, accountId, any(), false, null, null, capture(form))
        } returns Result.success("0xsold")

        assertEquals("0xsold", interactor.mutate(sell).getOrThrow())
        val args = builtCall(form, "sell")
        assertEquals(BigInteger("1000000000000000000"), args["shares_in"])
        assertEquals(BigInteger("891000000000000000"), args["min_collateral_out"])
    }

    @Test fun sellBeyondTheHeldOutcomeSharesIsRefused() = runTest {
        // 0.5 NO shares are indexed as a display decimal; an outcome-tagged integer position adds codec shares.
        coEvery { indexer.activity(endpoint, "cnAlice") } returns ParsedActivity(
            listOf(position, PolkamarktPosition("p2", "7", outcome = "NO", shares = "250000000000000000")), emptyList()
        )
        val sell = PolkamarktMutation.Trade("7", PolkamarktTradeMode.Sell, PolkamarktOutcome.No, "1", "0.891")
        coEvery { runtime.quote(PolkamarktQuoteRequest("7", PolkamarktTradeMode.Sell, PolkamarktOutcome.No, "1")) } returns
            json("""{"collateralOut":"900000000000000000","sharesIn":"1000000000000000000"}""")
        coEvery { extrinsics.estimateFee(chain, false, any()) } returns BigInteger("3000000000000000")

        val error = interactor.mutate(sell).exceptionOrNull()

        assertEquals("The order exceeds the available market shares.", error?.message)
        coVerify(exactly = 0) { extrinsics.submitAuthorizedExtrinsic(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun tradeOnAMarketMissingFromTheRuntimeIsRefused() = runTest {
        stubBuyQuote()
        coEvery { runtime.markets(any()) } returns emptyList()

        val error = interactor.mutate(buy).exceptionOrNull()

        assertEquals("The selected market is not authoritative in the connected SORA runtime.", error?.message)
        coVerify(exactly = 0) { extrinsics.submitAuthorizedExtrinsic(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun claimMarketAndCreatorFeesBuildTheirRuntimeCalls() = runTest {
        val form = slot<suspend ExtrinsicBuilder.() -> Unit>()
        coEvery { extrinsics.estimateFee(chain, false, capture(form)) } returns BigInteger("3000000000000000")
        coEvery {
            extrinsics.submitAuthorizedExtrinsic(chain, accountId, any(), false, null, null, any())
        } returns Result.success("0xclaimed")

        assertEquals("0xclaimed", interactor.mutate(PolkamarktMutation.ClaimMarket("8")).getOrThrow())
        assertEquals(BigInteger("8"), builtCall(form, "claim_market")["market_id"])
        coVerify { runtime.claimable("cnAlice", listOf("8")) }

        coEvery { runtime.claimable("cnAlice", listOf("8")) } returns listOf(claim.copy(isCreator = true, creatorFees = "4"))
        assertEquals("0xclaimed", interactor.mutate(PolkamarktMutation.ClaimCreatorFees("8")).getOrThrow())
        assertEquals(BigInteger("8"), builtCall(form, "claim_creator_fees")["market_id"])
    }

    @Test fun claimWithoutAnAuthoritativeClaimIsRefused() = runTest {
        coEvery { extrinsics.estimateFee(chain, false, any()) } returns BigInteger("3000000000000000")
        coEvery { runtime.claimable("cnAlice", listOf("9")) } returns emptyList()

        val error = interactor.mutate(PolkamarktMutation.ClaimMarket("9")).exceptionOrNull()

        assertEquals("This market has no authoritative claim for the selected SORA account.", error?.message)
    }

    @Test fun accountWithoutSoraKeyOrSigningAbilityCannotMutate() = runTest {
        every { wallet.accountId(chain) } returns null
        assertEquals("Add a SORA account.", interactor.mutate(PolkamarktMutation.ClaimMarket("8")).exceptionOrNull()?.message)

        every { wallet.accountId(chain) } returns accountId
        coEvery { extrinsics.hasLocalSubstrateKey(chain, accountId) } returns false
        assertEquals(
            "This SORA account cannot sign in this wallet.",
            interactor.mutate(PolkamarktMutation.ClaimMarket("8")).exceptionOrNull()?.message
        )
    }

    @Test fun accountOrWalletChangesDuringAuthorizationAbortTheMutation() = runTest {
        // The snapshot reads "cnAlice", then the selected account resolves to another address.
        every { wallet.address(chain) } returnsMany listOf("cnAlice", "cnBob")
        assertEquals(
            "The selected SORA account changed. Refresh before confirming.",
            interactor.mutate(PolkamarktMutation.ClaimMarket("8")).exceptionOrNull()?.message
        )

        every { wallet.address(chain) } returns "cnAlice"
        val other = mockk<MetaAccount>()
        every { other.id } returns 2L
        every { other.accountId(chain) } returns accountId
        coEvery { extrinsics.estimateFee(chain, false, any()) } returns BigInteger("3000000000000000")
        coEvery { accounts.getSelectedMetaAccount() } returnsMany listOf(wallet, wallet, other)
        assertEquals(
            "The selected wallet changed. Review the transaction again.",
            interactor.mutate(PolkamarktMutation.ClaimMarket("8")).exceptionOrNull()?.message
        )
        coVerify(exactly = 0) { extrinsics.submitAuthorizedExtrinsic(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun togglesDisabledDuringAuthorizationStopBeforeSubmission() = runTest {
        coEvery { extrinsics.estimateFee(chain, false, any()) } returns BigInteger("3000000000000000")
        every { toggles.polkamarktMutationsEnabled } returnsMany listOf(true, false)

        val error = interactor.mutate(PolkamarktMutation.ClaimMarket("8")).exceptionOrNull()

        assertEquals("Polkamarkt actions are temporarily disabled.", error?.message)
        coVerify(exactly = 0) { extrinsics.submitAuthorizedExtrinsic(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun hiltModuleWiresTheRealImplementations() = runTest {
        val module = PolkamarktModule
        val realIndexer = module.providePolkamarktIndexer(OkHttpClient(), Gson())
        val realRuntime = module.providePolkamarktRuntime(chainRegistry, mockk())
        val provided = module.providePolkamarktInteractor(
            chainRegistry, realIndexer, realRuntime, accounts, wallets, extrinsics, polkaswap, toggles, authorization
        )
        assertTrue(provided is PolkamarktInteractorImpl)
        // The real indexer never queries history without a market and never reads activity without an account.
        assertTrue(realIndexer.history(endpoint, null).isEmpty())
        assertTrue(realIndexer.activity(endpoint, null).positions.isEmpty())
    }

    private fun stubBuyQuote() {
        coEvery { runtime.quote(PolkamarktQuoteRequest("7", PolkamarktTradeMode.Buy, PolkamarktOutcome.Yes, "1.5")) } returns
            json("""{"sharesOut":"2000000000000000000","collateralIn":"1500000000000000000","feeAmount":"10000000000000000"}""")
        coEvery { extrinsics.estimateFee(chain, false, any()) } returns BigInteger("3000000000000000")
    }

    private suspend fun builtCall(
        form: io.mockk.CapturingSlot<suspend ExtrinsicBuilder.() -> Unit>,
        callName: String
    ): Map<String, Any?> {
        val builder = mockk<ExtrinsicBuilder>()
        val args = slot<Map<String, Any?>>()
        every { builder.call("Polkamarkt", callName, capture(args)) } returns builder
        form.captured.invoke(builder)
        return args.captured
    }
}
