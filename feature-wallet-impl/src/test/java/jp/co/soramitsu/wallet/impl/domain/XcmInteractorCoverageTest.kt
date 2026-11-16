package jp.co.soramitsu.wallet.impl.domain

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionJournal
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.CrossChainTransfer
import jp.co.soramitsu.xcm.XcmService
import jp.co.soramitsu.xcm.XcmTrackedSubmission
import jp.co.soramitsu.xcm.domain.CrossChainAssetIdentity
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import jp.co.soramitsu.xcm.domain.CrossChainRouteProvider
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import jp.co.soramitsu.xcm.domain.CrossChainRouteQuery
import jp.co.soramitsu.xcm.domain.XcmAsset
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route-filtered asset lists, the tracked cross-chain submission, and the fee and minimum queries of [XcmInteractor].
 */
class XcmInteractorCoverageTest {

    private val walletInteractor = mockk<WalletInteractor>()
    private val chainRegistry = mockk<ChainRegistry>()
    private val fetcher = mockk<XcmEntitiesFetcher>()
    private val accountInteractor = mockk<AccountInteractor>()
    private val xcmService = mockk<XcmService>()
    private val toggles = mockk<ProductFeatureToggleStore> { every { xcmMutationsEnabled } returns true }
    private val signingProvider = mockk<KeypairProvider>()

    private val values = ConcurrentHashMap<String, String>()
    private val preferences = mockk<Preferences> {
        every { durableStorageIdentity } returns Any()
        every { getString(any()) } answers { values[firstArg()] }
        every { replaceStringsDurably(any(), any()) } answers {
            secondArg<Set<String>>().forEach(values::remove)
            values.putAll(firstArg<Map<String, String>>())
            true
        }
    }
    private val reader = mockk<FinalizedExtrinsicReader> { coEvery { anchor(ORIGIN_ID) } returns ANCHOR }
    private val submissions = CrossChainSubmissions(CrossChainSubmissionJournal(preferences), reader)

    private val dot = WalletTestModels.asset(chainId = ORIGIN_ID)
    private val origin = WalletTestModels.chain(id = ORIGIN_ID, assets = listOf(dot))
    private val destination = WalletTestModels.chain(
        id = DESTINATION_ID,
        assets = listOf(WalletTestModels.asset(chainId = DESTINATION_ID))
    )
    private val sender = ByteArray(32) { 1 }

    init {
        coEvery { chainRegistry.getChain(ORIGIN_ID) } returns origin
        coEvery { chainRegistry.getChain(DESTINATION_ID) } returns destination
        coEvery { accountInteractor.selectedMetaAccount() } returns wallet(CryptoType.SR25519)
        every { xcmService.updateKeypairProvider(any(), any()) } just Runs
    }

    @Test
    fun `available assets are those with an approved XCM route or an available provider route`() = runBlocking {
        val xcmDot = WalletTestModels.asset(id = "dot", chainId = "polkadot", precision = 10)
        val lld = WalletTestModels.asset(id = "lld", symbol = "LLD", chainId = "liberland", precision = 12)
        val ksm = WalletTestModels.asset(id = "ksm", symbol = "KSM", chainId = "kusama", precision = 12)
        val otherPrecision = WalletTestModels.asset(id = "dot", chainId = "polkadot-12", precision = 12)
        every { walletInteractor.assetsFlow() } returns flowOf(
            listOf(xcmDot, lld, ksm, otherPrecision).map { AssetWithStatus(WalletTestModels.fundedAsset(it), true, true) }
        )
        coEvery { fetcher.getAvailableAssets(any(), any(), any()) } returns listOf(
            XcmAsset(id = "dot", symbol = "DOT", minAmount = null, originChainId = "polkadot", originAssetId = "dot", originAssetPrecision = 10),
            XcmAsset(id = "dot", symbol = "DOT", minAmount = null, originChainId = "polkadot-12", originAssetId = "dot", originAssetPrecision = 10)
        )
        val available = RecordingProvider("available", CrossChainAssetIdentity("liberland", "lld", "LLD"), hasRoute = true)
        val unavailable = RecordingProvider("unavailable", CrossChainAssetIdentity("kusama", "ksm", "KSM"), hasRoute = false)

        val assets = interactor(registry = CrossChainRouteProviderRegistry(listOf(available, unavailable)))
            .getAvailableAssetsFlow("polkadot").first()

        assertEquals(listOf(xcmDot, lld), assets.map { it.asset.token.configuration })
        assertEquals(CrossChainRouteQuery(originNetworkId = "polkadot"), available.queries.single())
        coVerify(exactly = 1) { fetcher.getAvailableAssets("polkadot", null, null) }
    }

    @Test
    fun `without a provider registry only approved XCM routes are offered`() = runBlocking {
        val xcmDot = WalletTestModels.asset(id = "dot", chainId = "polkadot", precision = 10)
        val lld = WalletTestModels.asset(id = "lld", symbol = "LLD", chainId = "liberland", precision = 12)
        every { walletInteractor.assetsFlow() } returns flowOf(
            listOf(xcmDot, lld).map { AssetWithStatus(WalletTestModels.fundedAsset(it), true, true) }
        )
        coEvery { fetcher.getAvailableAssets(any(), any(), any()) } returns listOf(
            XcmAsset(id = "dot", symbol = "DOT", minAmount = null, originChainId = "polkadot", originAssetId = "dot", originAssetPrecision = 10)
        )

        val assets = interactor(registry = null).getAvailableAssetsFlow(null).first()

        assertEquals(listOf(xcmDot), assets.map { it.asset.token.configuration })
    }

    @Test
    fun `a cross-chain transfer is sent through a tracked submission of the reviewed wallet`() = runBlocking {
        val submission = slot<XcmTrackedSubmission>()
        val senderAccountId = slot<ByteArray>()
        coEvery {
            xcmService.transfer(any(), any(), any(), capture(senderAccountId), any(), any(), capture(submission))
        } returns "0xhash"

        val result = interactor().performCrossChainTransfer(transfer(), walletId = WALLET_ID)

        assertEquals("0xhash", result.getOrThrow())
        assertEquals(WALLET_ID, submission.captured.walletId)
        assertSame(signingProvider, submission.captured.keypairProvider)
        assertArrayEquals(sender, senderAccountId.captured)
        // 1.5 DOT plus the 0.1 DOT destination fee, in planks.
        coVerify(exactly = 1) {
            xcmService.transfer(origin, destination, dot, any(), "recipient", BigInteger("16000000000"), any())
        }
    }

    @Test
    fun `a transfer cannot be sent without submission tracking or a signing provider`() = runBlocking {
        val untracked = interactor(submissions = null).performCrossChainTransfer(transfer(), WALLET_ID)
        val unsigned = interactor(signing = null).performCrossChainTransfer(transfer(), WALLET_ID)

        assertEquals("Cross-chain submissions cannot be tracked", untracked.exceptionOrNull()?.message)
        assertEquals("An authorized signing provider is required", unsigned.exceptionOrNull()?.message)
        coVerify(exactly = 0) { xcmService.transfer(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the destination fee is the service quote or unknown`() = runBlocking {
        coEvery { xcmService.getXcmDestinationFee(ORIGIN_ID, DESTINATION_ID, dot) } returns BigDecimal("0.2")
        coEvery { xcmService.getXcmDestinationFee(DESTINATION_ID, ORIGIN_ID, dot) } throws IllegalStateException("no route")

        assertEquals(BigDecimal("0.2"), interactor().getDestinationFee(ORIGIN_ID, DESTINATION_ID, dot))
        assertNull(interactor().getDestinationFee(DESTINATION_ID, ORIGIN_ID, dot))
    }

    @Test
    fun `an included destination fee is reported only when the service confirms it`() = runBlocking {
        coEvery { xcmService.isXcmDestinationFeeIncluded(ORIGIN_ID, DESTINATION_ID, dot) } returns true
        coEvery { xcmService.isXcmDestinationFeeIncluded(DESTINATION_ID, ORIGIN_ID, dot) } throws IllegalStateException("no route")

        assertTrue(interactor().isDestinationFeeIncluded(ORIGIN_ID, DESTINATION_ID, dot))
        assertFalse(interactor().isDestinationFeeIncluded(DESTINATION_ID, ORIGIN_ID, dot))
    }

    @Test
    fun `an origin fee is not quoted for a wallet without a Substrate crypto type`() = runBlocking {
        coEvery { accountInteractor.selectedMetaAccount() } returns wallet(cryptoType = null)

        val fee = interactor().getOriginFee(ORIGIN_ID, DESTINATION_ID, dot, BigDecimal.ONE)

        assertNull(fee)
        coVerify(exactly = 0) { xcmService.getXcmOriginFee(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a zero amount origin fee quote uses one planck`() = runBlocking {
        coEvery { xcmService.getXcmOriginFee(any(), any(), any(), any(), any()) } returns BigDecimal("0.01")

        val fee = interactor().getOriginFee(ORIGIN_ID, DESTINATION_ID, dot, BigDecimal.ZERO)

        assertEquals(BigDecimal("0.01"), fee)
        coVerify(exactly = 1) { xcmService.getXcmOriginFee(origin, DESTINATION_ID, dot, any(), BigInteger.ONE) }
    }

    @Test
    fun `the minimum amount comes from the service`() = runBlocking {
        coEvery { xcmService.getAmountMinLimit(ORIGIN_ID, DESTINATION_ID, dot) } returns BigInteger.TEN

        assertEquals(BigInteger.TEN, interactor().getAmountMinLimit(ORIGIN_ID, DESTINATION_ID, dot))
    }

    private fun interactor(
        registry: CrossChainRouteProviderRegistry? = null,
        signing: KeypairProvider? = signingProvider,
        submissions: CrossChainSubmissions? = this.submissions
    ) = XcmInteractor(
        walletInteractor = walletInteractor,
        chainRegistry = chainRegistry,
        xcmEntitiesFetcher = fetcher,
        accountInteractor = accountInteractor,
        runtimeFilesCache = mockk(),
        xcmService = xcmService,
        featureToggleStore = toggles,
        routeProviderRegistry = registry,
        signingKeypairProvider = signing,
        crossChainSubmissions = submissions
    )

    private fun transfer() = CrossChainTransfer(
        originChainId = ORIGIN_ID,
        destinationChainId = DESTINATION_ID,
        recipient = "recipient",
        amount = BigDecimal("1.5"),
        destinationFee = BigDecimal("0.1"),
        chainAsset = dot
    )

    private fun wallet(cryptoType: CryptoType?) = MetaAccount(
        id = WALLET_ID,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = sender,
        substrateCryptoType = cryptoType,
        substrateAccountId = sender,
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Wallet",
        initialized = true
    )

    private class RecordingProvider(
        override val providerId: String,
        private val asset: CrossChainAssetIdentity,
        private val hasRoute: Boolean
    ) : CrossChainRouteProvider {
        override val protocol: CrossChainProtocol = CrossChainProtocol.Liberland
        val queries = mutableListOf<CrossChainRouteQuery>()

        override suspend fun capability(query: CrossChainRouteQuery): CrossChainRouteCapability {
            queries += query
            return CrossChainRouteCapability(
                providerId = providerId,
                protocol = protocol,
                availability = if (hasRoute) CrossChainRouteAvailability.Available else CrossChainRouteAvailability.Unavailable,
                actionsEnabled = hasRoute,
                requiresAccount = true,
                requiresSigning = true,
                supportedAssets = setOf(asset),
                userFacingReason = if (hasRoute) null else "Not delivering"
            )
        }
    }

    private companion object {
        const val WALLET_ID = 7L
        val ORIGIN_ID = "6b".repeat(32)
        val DESTINATION_ID = "7e".repeat(32)
        val ANCHOR = FinalizedExtrinsicReader.Anchor("0x$ORIGIN_ID", BigInteger.valueOf(1_000), "0x" + "bb".repeat(32))
    }
}
