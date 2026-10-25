package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.*
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.model.cache.*
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.TransferService
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.TransferServiceProvider
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class WalletRepositoryCurrencyAdoptionTest {
    private val account get() = ByteArray(32) { 7 }
    private val recipient get() = ByteArray(32) { 8 }.toAddress(42)
    private fun config(id: String = "asset", chainId: String = "chain", symbol: String = "PUB",
                       precision: Int = 2, utility: Boolean = true) = CoreAsset(
        id, "Public token", symbol, "", chainId, chainId, null, false, "public-price", precision,
        CoreAsset.StakingType.UNSUPPORTED, null, false, utility, ChainAssetType.Normal,
        null, null, null, true
    )
    private fun chain(configs: List<CoreAsset> = listOf(config()), version: String = "7.0"): Chain = mockk {
        every { id } returns configs.first().chainId
        every { ecosystem } returns Ecosystem.Substrate
        every { isEthereumBased } returns false
        every { externalApi } returns null
        every { assets } returns configs
        every { assetsById } returns configs.associateBy { it.id }
        every { minSupportedVersion } returns version
        every { isTestNet } returns false
    }
    private fun meta(id: Long = 7, owner: ByteArray = account,
                     overrides: Map<String, MetaAccount.ChainAccount> = emptyMap()) = MetaAccount(
        id, overrides, emptyMap(), null, CryptoType.SR25519, owner, null, null, null,
        true, true, null, "Public wallet", true
    )
    private fun number(value: String?): CachedNumericState<BigInteger> = CachedNumericDecoder.integer(
        value?.toByteArray()?.let { CachedNumericCell("text", it.size.toLong(), it) }
            ?: CachedNumericCell("null", 0, null)
    )
    private fun row(configuration: CoreAsset = config(), owner: ByteArray = account,
                    free: String? = "1000", frozen: String? = null, enabled: Boolean? = null,
                    fiat: String = "usd", rate: String = "2") = CachedAssetWithPriceRead(
        CachedAssetRead(configuration.id, configuration.chainId, owner, 7, "obsolete-price-pointer", 5,
            enabled, false, "Public account", null, number(free), number(null), number(frozen),
            number(null), number(null), number(null), number(null)),
        CachedTokenPriceRead("public-price", "wrong-saved-symbol", CachedNumericState.Known(BigDecimal(rate)),
            CachedNumericState.Missing, CachedFiatCurrencyIdentity.Known(fiat))
    )
    private fun snapshot(owner: MetaAccount = meta(), catalog: List<Chain> = listOf(chain()),
                         rows: List<CachedAssetWithPriceRead> = listOf(row()), fiat: String = "usd") =
        CurrentAccountAssetSnapshot(CurrentAccountAssetContext(owner, FiatValuationContext(fiat)),
            catalog.associateBy { it.id }, rows, true)
    private fun ready(snapshot: CurrentAccountAssetSnapshot) = SelectedAccountAssetSnapshot.Ready(snapshot)

    private class StorageFailure(val marker: Any) : RuntimeException("public fixture failure")
    private suspend fun failure(block: suspend () -> Unit): Throwable {
        var observed: Throwable? = null
        try { block() } catch (error: Throwable) { observed = error }
        return requireNotNull(observed) { "Expected operation to fail" }
    }
    private fun assertUnknown(asset: Asset) {
        assertNull(asset.total)
        assertNull(asset.transferable)
        assertNull(asset.fiatAmount)
        assertNull(asset.token.fiatRate)
    }

    private inner class Fixture(val initial: CurrentAccountAssetSnapshot = snapshot()) {
        val reader = mockk<CurrentAccountAssetReader>()
        val dao = mockk<AssetDao>()
        val chains = mockk<ChainsRepository>()
        val constants = mockk<WalletConstants>()
        val substrate = mockk<SubstrateRemoteSource>()
        val service = mockk<TransferService>()
        val services = mockk<TransferServiceProvider>()
        val visibility = mockk<PortfolioVisibility> { every { hasTrustedPrice(any()) } returns true }
        val repository = WalletRepositoryImpl(
            substrateSource = substrate, ethereumSource = mockk(), operationDao = mockk(),
            httpExceptionHandler = mockk(), phishingApi = mockk(), assetDao = dao,
            walletConstants = constants, phishingDao = mockk(), coingeckoApi = mockk(),
            chainRegistry = mockk(), remoteConfigFetcher = mockk(), accountRepository = mockk(),
            chainsRepository = chains, extrinsicService = mockk(), remoteStorageSource = mockk(),
            pricesSyncService = mockk(), transferServiceProvider = services, tonRemoteSource = mockk(),
            portfolioVisibility = visibility, currentAccountAssetReader = reader
        )
        init {
            every { reader.observeAccountSnapshots(7) } returns flowOf(ready(initial))
            coEvery { reader.getSnapshot(7) } returns initial
            every { services.provide(any()) } returns service
            coEvery { service.getTransferFee(any()) } returns BigDecimal.ONE
            coEvery { substrate.getTotalBalance(any(), any()) } returns BigInteger.ZERO
            coEvery { constants.existentialDeposit(any()) } returns BigInteger.ZERO
            coEvery { constants.tip(any()) } returns BigInteger.ZERO
        }
        fun states(vararg states: SelectedAccountAssetSnapshot) {
            every { reader.observeAccountSnapshots(7) } returns flowOf(*states)
        }
        suspend fun validate(configuration: CoreAsset = config(), requested: ByteArray = account,
                             onChain: Chain = initial.chainsById.getValue("chain")) =
            repository.checkTransferValidity(7, requested, onChain,
                Transfer("public-sender", recipient, BigDecimal.ONE, configuration), null, false)
    }

    @Test fun listUsesCapturedWalletRevisionCatalogAndFiatTogether() = runTest {
        val catalog = chain()
        val override = MetaAccount.ChainAccount(7, catalog, account, account, CryptoType.SR25519, "Current account")
        val captured = snapshot(meta(overrides = mapOf("chain" to override)), listOf(catalog),
            listOf(row(fiat = "eur", rate = "3")), "eur")
        val f = Fixture(captured)
        val result = f.repository.assetsFlow(meta()).first().single()
        assertTrue(result.hasChainAccount)
        assertTrue(result.hasAccount)
        assertSame(catalog.assets.single(), result.asset.token.configuration)
        assertEquals("eur", result.asset.token.fiatContext?.currencyId)
        assertEquals(0, result.asset.fiatAmount!!.compareTo(BigDecimal("30")))
        verify { listOf(f.dao, f.chains) wasNot Called }
    }

    @Test fun listRevokesPriorValuesOnLoadingAndDeletion() = runTest {
        val f = Fixture()
        f.states(ready(f.initial), SelectedAccountAssetSnapshot.Loading(f.initial.context),
            ready(f.initial), SelectedAccountAssetSnapshot.AwaitingSelection)
        assertEquals(listOf(1, 0, 1, 0), f.repository.assetsFlow(meta()).toList().map { it.size })
    }

    @Test fun listFailureRevokesThenPreservesExactFailure() = runTest {
        val f = Fixture()
        val injected = StorageFailure(Any())
        every { f.reader.observeAccountSnapshots(7) } returns flow { emit(ready(f.initial)); throw injected }
        val sizes = mutableListOf<Int>()
        assertSame(injected, failure { f.repository.assetsFlow(meta()).collect { sizes += it.size } })
        assertEquals(listOf(1, 0), sizes)
    }

    @Test fun listCancellationPropagatesWithoutInventingAnotherSnapshot() = runTest {
        val f = Fixture()
        val cancelled = CancellationException("public cancellation")
        every { f.reader.observeAccountSnapshots(7) } returns flow { emit(ready(f.initial)); throw cancelled }
        val sizes = mutableListOf<Int>()
        assertSame(cancelled, failure { f.repository.assetsFlow(meta()).collect { sizes += it.size } })
        assertEquals(listOf(1), sizes)
    }

    @Test fun oneShotListKeepsDamagedAndMissingQuantitiesUnknown() = runTest {
        for (free in listOf(null, "bad", "-1")) {
            val f = Fixture(snapshot(rows = listOf(row(free = free))))
            val asset = f.repository.getAssets(7).single()
            assertNull(asset.total)
            assertNull(asset.fiatAmount)
            assertEquals("usd", asset.token.fiatContext?.currencyId)
        }
    }

    @Test fun oneShotListRejectsDifferentWalletSnapshot() = runTest {
        val f = Fixture(snapshot(owner = meta(id = 8)))
        assertTrue(failure { f.repository.getAssets(7) } is IllegalArgumentException)
        assertTrue(f.repository.assetsFlow(meta()).first().isEmpty())
    }

    @Test fun singleAssetFlowRevokesLoadingAndDeletedValues() = runTest {
        val f = Fixture()
        f.states(ready(f.initial), SelectedAccountAssetSnapshot.Loading(f.initial.context),
            ready(f.initial), SelectedAccountAssetSnapshot.AwaitingSelection)
        val values = f.repository.assetFlow(7, account, config(), null).toList()
        assertEquals(4, values.size)
        assertNotNull(values[0].fiatAmount)
        assertUnknown(values[1])
        assertNotNull(values[2].fiatAmount)
        assertUnknown(values[3])
    }

    @Test fun singleAssetFailurePublishesUnknownThenPreservesFailure() = runTest {
        val f = Fixture()
        val injected = StorageFailure(Any())
        every { f.reader.observeAccountSnapshots(7) } returns flow { emit(ready(f.initial)); throw injected }
        val values = mutableListOf<Asset>()
        assertSame(injected, failure { f.repository.assetFlow(7, account, config(), null).collect { values += it } })
        assertEquals(2, values.size)
        assertUnknown(values.last())
    }

    @Test fun singleAssetAdoptsCurrentCatalogAndNeverCallerPrecision() = runTest {
        val replacement = config(precision = 4)
        val current = chain(listOf(replacement), "8.0")
        val f = Fixture(snapshot(catalog = listOf(current)))
        val asset = f.repository.assetFlow(7, account, config(), "old").first()
        assertSame(replacement, asset.token.configuration)
        assertEquals("8.0", asset.minSupportedVersion)
        assertEquals(0, asset.total!!.compareTo(BigDecimal("0.1")))
    }

    @Test fun singleAssetRevokesRemovedCatalogAndChangedAccountOverride() = runTest {
        val f = Fixture()
        f.states(ready(f.initial), ready(snapshot(catalog = emptyList())))
        assertUnknown(f.repository.assetFlow(7, account, config(), null).toList().last())
        val changed = snapshot(owner = meta(owner = ByteArray(32) { 9 }))
        f.states(ready(changed))
        assertUnknown(f.repository.assetFlow(7, account, config(), null).first())
        coEvery { f.reader.getSnapshot(7) } returns changed
        assertNull(f.repository.getAsset(7, account, config(), null))
    }

    @Test fun observedAccountKeyIsCopiedBeforeCallerMutation() = runTest {
        val f = Fixture()
        val requested = account
        val flow = f.repository.assetFlow(7, requested, config(), null)
        requested[0] = 99
        assertArrayEquals(account, flow.first().accountId)
    }

    @Test fun oneShotAccountKeyIsCopiedBeforeSuspendingRead() = runTest {
        val f = Fixture()
        val requested = account
        coEvery { f.reader.getSnapshot(7) } coAnswers { requested[0] = 99; f.initial }
        assertArrayEquals(account, f.repository.getAsset(7, requested, config(), null)!!.accountId)
    }

    @Test fun exactDamagedRowWinsOverHealthyEmptyAccountFallback() = runTest {
        val f = Fixture(snapshot(rows = listOf(row(owner = byteArrayOf()), row(free = "bad"))))
        val asset = f.repository.getAsset(7, account, config(), null)!!
        assertTrue(asset.freeInPlanksState is AssetAmountState.Unavailable)
        assertNull(asset.total)
        assertArrayEquals(account, asset.accountId)
    }

    @Test fun emptyAccountFallbackChangesIdentityOnlyAndCopiesInput() = runTest {
        val fallback = row(owner = byteArrayOf(), free = "bad")
        val f = Fixture(snapshot(rows = listOf(fallback)))
        val result = f.repository.getAsset(7, account, config(), null)!!
        assertArrayEquals(account, result.accountId)
        assertTrue(result.freeInPlanksState is AssetAmountState.Unavailable)
        assertTrue(fallback.asset.accountId.isEmpty())
        assertNull(result.fiatAmount)
    }

    @Test fun absentExactAssetIsNullAndNeverKnownZero() = runTest {
        val f = Fixture(snapshot(rows = emptyList()))
        assertNull(f.repository.getAsset(7, account, config(), null))
        assertUnknown(f.repository.assetFlow(7, account, config(), null).first())
    }

    @Test fun selectedCurrencyMismatchDoesNotReuseStoredQuote() = runTest {
        val f = Fixture(snapshot(fiat = "eur"))
        val asset = f.repository.getAsset(7, account, config(), null)!!
        assertEquals(0, asset.total!!.compareTo(BigDecimal.TEN))
        assertEquals("eur", asset.token.fiatContext?.currencyId)
        assertNull(asset.fiatAmount)
        assertNull(asset.token.fiatRate)
    }

    @Test fun validationUsesOneSnapshotAfterRemoteFeeAndRecipientReads() = runTest {
        val f = Fixture()
        var remoteDone = false
        coEvery { f.substrate.getTotalBalance(any(), any()) } coAnswers { remoteDone = true; BigInteger.ZERO }
        coEvery { f.reader.getSnapshot(7) } coAnswers { assertTrue(remoteDone); f.initial }
        assertEquals(TransferValidityLevel.Ok, f.validate())
        coVerify(exactly = 1) { f.reader.getSnapshot(7) }
        verify { f.dao wasNot Called }
    }

    @Test fun validationCopiesRequestedAccountBeforeRemoteAwait() = runTest {
        val f = Fixture()
        val requested = account
        coEvery { f.service.getTransferFee(any()) } coAnswers { requested[0] = 99; BigDecimal.ONE }
        assertEquals(TransferValidityLevel.Ok, f.validate(requested = requested))
    }

    @Test fun validationRejectsUnknownQuantityAndCurrentAccountMismatch() = runTest {
        for (free in listOf(null, "bad", "-1")) {
            val f = Fixture(snapshot(rows = listOf(row(free = free))))
            assertTrue(failure { f.validate() } is IllegalArgumentException)
        }
        val f = Fixture(snapshot(owner = meta(owner = ByteArray(32) { 9 })))
        assertTrue(failure { f.validate() } is IllegalArgumentException)
    }

    @Test fun validationRejectsCatalogReplacementDuringRemoteWork() = runTest {
        val f = Fixture()
        val replacement = snapshot(catalog = listOf(chain(listOf(config(precision = 4)))))
        coEvery { f.reader.getSnapshot(7) } returns replacement
        assertTrue(failure { f.validate() } is IllegalArgumentException)
    }

    @Test fun validationRequiresKnownUtilityBalanceFromSameSnapshot() = runTest {
        val token = config(utility = false)
        val utility = config(id = "fee", symbol = "FEE")
        val catalog = chain(listOf(token, utility))
        val missingFee = Fixture(snapshot(catalog = listOf(catalog), rows = listOf(row(token))))
        assertTrue(failure { missingFee.validate(token) } is IllegalArgumentException)
        val f = Fixture(snapshot(catalog = listOf(catalog), rows = listOf(row(token), row(utility))))
        assertEquals(TransferValidityLevel.Ok, f.validate(token))
        coVerify(exactly = 1) { f.reader.getSnapshot(7) }
    }

    @Test fun validationDoesNotReplaceFailedFeeWithZero() = runTest {
        val f = Fixture()
        val injected = StorageFailure(Any())
        coEvery { f.service.getTransferFee(any()) } throws injected
        assertSame(injected, failure { f.validate() })
        coVerify(exactly = 0) { f.reader.getSnapshot(any()) }
    }

    @Test fun validationPropagatesTipCancellationWithoutReadingBalances() = runTest {
        val f = Fixture()
        val injected = CancellationException("tip cancelled")
        coEvery { f.constants.tip(any()) } throws injected
        assertSame(injected, failure { f.validate() })
        coVerify(exactly = 0) { f.reader.getSnapshot(any()) }
    }

    @Test fun fundedNetworksUseCapturedRowsAndCurrencyAndClearOnLoading() = runTest {
        val f = Fixture(snapshot(rows = listOf(row(fiat = "eur", rate = "3")), fiat = "eur"))
        f.states(ready(f.initial), SelectedAccountAssetSnapshot.Loading(f.initial.context))
        val values = f.repository.observeChainsPerAsset(7, "asset").toList()
        assertEquals("eur", values.first().values.single()!!.token.fiatContext?.currencyId)
        assertEquals(0, values.first().values.single()!!.fiatAmount!!.compareTo(BigDecimal("30")))
        assertTrue(values.last().isEmpty())
        verify { listOf(f.dao, f.chains) wasNot Called }
    }

    @Test fun fundedNetworksRespectHiddenAutoUnknownAndExplicitShown() = runTest {
        for ((row, count) in listOf(
            row(enabled = false) to 0, row(free = "0") to 0, row(free = null) to 0,
            row(free = "bad") to 0, row(free = null, enabled = true) to 1, row() to 1
        )) {
            val f = Fixture(snapshot(rows = listOf(row)))
            assertEquals(count, f.repository.observeChainsPerAsset(7, "asset").first().size)
        }
    }

    @Test fun fundedNetworksRejectAmbiguousRegistryIdInsteadOfPickingFirst() = runTest {
        val one = config(chainId = "one", symbol = "ONE")
        val two = config(chainId = "two", symbol = "TWO")
        for (catalog in listOf(listOf(chain(listOf(one)), chain(listOf(two))),
            listOf(chain(listOf(two)), chain(listOf(one))))) {
            val f = Fixture(snapshot(catalog = catalog, rows = listOf(row(one), row(two))))
            assertTrue(f.repository.observeChainsPerAsset(7, "asset").first().isEmpty())
        }
    }

    @Test fun fundedNetworksPreferExactSymbolWithoutChangingAmountAssociation() = runTest {
        val exact = config()
        val xc = config(id = "xc-asset", symbol = "xcPUB")
        for (rows in listOf(listOf(row(xc, free = "2000"), row(exact)), listOf(row(exact), row(xc, free = "2000")))) {
            val f = Fixture(snapshot(catalog = listOf(chain(listOf(xc, exact))), rows = rows))
            val selected = f.repository.observeChainsPerAsset(7, "asset").first().values.single()!!
            assertEquals("asset", selected.id)
            assertEquals(0, selected.total!!.compareTo(BigDecimal.TEN))
        }
    }

    @Test fun fundedNetworksFailureRevokesAndPreservesCause() = runTest {
        val f = Fixture()
        val injected = StorageFailure(Any())
        every { f.reader.observeAccountSnapshots(7) } returns flow { emit(ready(f.initial)); throw injected }
        val sizes = mutableListOf<Int>()
        assertSame(injected, failure { f.repository.observeChainsPerAsset(7, "asset").collect { sizes += it.size } })
        assertEquals(listOf(1, 0), sizes)
    }
    @Test fun singleAndFundedObserversPropagateCancellationAndRunCleanup() = runTest {
        val f = Fixture()
        for (single in listOf(true, false)) {
            val injected = CancellationException("public observer cancellation")
            var cleaned = false
            every { f.reader.observeAccountSnapshots(7) } returns flow {
                try { emit(ready(f.initial)); throw injected } finally { cleaned = true }
            }
            val values = mutableListOf<Any>()
            val actual = failure {
                if (single) f.repository.assetFlow(7, account, config(), null).collect { values += it }
                else f.repository.observeChainsPerAsset(7, "asset").collect { values += it }
            }
            assertSame(injected, actual)
            assertTrue(cleaned)
            assertEquals(1, values.size)
        }
    }

}
