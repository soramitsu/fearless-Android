package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.OutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.zip.GZIPInputStream
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.config.AppConfigRemote
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqOraclePricePoint
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.TransferService
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.TransferServiceProvider
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRepositoryImplCoverageTest {

    private val substrateSource = mockk<SubstrateRemoteSource>()
    private val ethereumSource = mockk<EthereumRemoteSource>()
    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val phishingDao = mockk<PhishingDao>()
    private val coingeckoApi = mockk<CoingeckoApi>()
    private val chainRegistry = mockk<ChainRegistry>()
    private val remoteConfigFetcher = mockk<RemoteConfigFetcher>()
    private val accountRepository = mockk<AccountRepository>()
    private val chainsRepository = mockk<ChainsRepository>()
    private val extrinsicService = mockk<ExtrinsicService>()
    private val pricesSyncService = mockk<PricesSyncService>()
    private val transferServiceProvider = mockk<TransferServiceProvider>()
    private val storage = LocksStorage()
    private val polkadot: Chain = WalletTestModels.chain()
    private val dot = polkadot.assets.single()

    @Test
    fun `rates, asset preferences and transfer quotes are delegated`(): Unit = runBlocking {
        coEvery { pricesSyncService.sync() } returns Unit
        val service = mockk<TransferService>()
        val transfer = transfer()
        every { transferServiceProvider.provide(polkadot, any(), any()) } returns service
        coEvery { service.getTransferMaxAmount(transfer) } returns BigDecimal("9.5")
        every { service.observeTransferFee(transfer) } returns flowOf(BigDecimal("0.01"))
        val preferences = listOf(AssetUpdateItem(1L, "polkadot", WalletTestModels.substrateAccountId, "dot", 0, true, "dot"))
        val repository = repository()

        repository.syncAssetsRates("usd")
        repository.updateAssetsHidden(preferences)

        coVerify(exactly = 1) { pricesSyncService.sync() }
        coVerify(exactly = 1) { assetDao.updateOrInsertAssetPreferences(preferences) }
        assertEquals(BigDecimal("9.5"), repository.getTransferMaxAmount(polkadot, transfer))
        assertEquals(listOf(BigDecimal("0.01")), repository.observeTransferFee(polkadot, transfer).toList())
    }

    @Test
    fun `bitcoin transfers skip substrate validity checks`(): Unit = runBlocking {
        val bitcoin = WalletTestModels.chain(id = UniversalWalletRegistry.bitcoinMainnet.id)
        val transfer = Transfer("bc1-sender", "bc1-recipient", BigDecimal.ONE, bitcoin.assets.single())

        val status = repository().checkTransferValidity(1L, ByteArray(20), bitcoin, transfer, null, false)

        assertSame(TransferValidityLevel.Ok, status)
        coVerify(exactly = 0) { transferServiceProvider.provide(any(), any(), any()) }
    }

    @Test
    fun `total balance reads ethereum through the EVM source and ton reports one`(): Unit = runBlocking {
        val ethereum = WalletTestModels.chain(id = "eth-coverage", ecosystem = Ecosystem.Ethereum)
        val eth = ethereum.assets.single()
        coEvery { ethereumSource.getTotalBalance(eth, ethereum, any()) } returns Result.success(BigInteger.valueOf(42))
        val ton = WalletTestModels.chain(id = "ton-coverage", ecosystem = Ecosystem.Ton)

        val repository = repository()

        assertEquals(BigInteger.valueOf(42), repository.getTotalBalance(eth, ethereum, WalletTestModels.ETH_A))
        assertEquals(BigInteger.ONE, repository.getTotalBalance(ton.assets.single(), ton, "EQ-anything"))
        coVerify { ethereumSource.getTotalBalance(eth, ethereum, match { it.contentEquals(ByteArray(20) { 0x11 }) }) }
    }

    @Test
    fun `phishing lookups ignore case and return the stored row`(): Unit = runBlocking {
        val row = PhishingLocal(address = "0xAbC", name = "Fake exchange", type = "scam", subtype = null)
        coEvery { phishingDao.getAllAddresses() } returns listOf("0xAbC", "5Grw")
        coEvery { phishingDao.getPhishingInfo("0xAbC") } returns row
        val repository = repository()

        assertTrue(repository.isAddressFromPhishingList("0xabc"))
        assertFalse(repository.isAddressFromPhishingList("0xdef"))
        assertSame(row, repository.getPhishingInfo("0xAbC"))
    }

    @Test
    fun `substrate balance, rate and staking account reads are delegated`(): Unit = runBlocking {
        val accountId = WalletTestModels.substrateAccountId
        val rates = mapOf<BigInteger, EqOraclePricePoint?>(BigInteger.ONE to null)
        val eqInfo = EqAccountInfo(BigInteger.ZERO, EqAccountData(BigInteger.ZERO, emptyMap()))
        val stash = ByteArray(32) { 8 }
        coEvery { substrateSource.getAccountFreeBalance(dot, accountId) } returns BigInteger.valueOf(11)
        coEvery { substrateSource.getAccountSpendableBalance(dot, accountId) } returns BigInteger.valueOf(7)
        coEvery { substrateSource.getEquilibriumAssetRates(dot) } returns rates
        coEvery { substrateSource.getEquilibriumAccountInfo(dot, accountId) } returns eqInfo
        coEvery { substrateSource.getControllerAccount("polkadot", accountId) } returns accountId
        coEvery { substrateSource.getStashAccount("polkadot", accountId) } returns stash
        val repository = repository()

        assertEquals(BigInteger.valueOf(11), repository.getAccountFreeBalance(dot, accountId))
        assertEquals(BigInteger.valueOf(7), repository.getAccountSpendableBalance(dot, accountId))
        assertSame(rates, repository.getEquilibriumAssetRates(dot))
        assertSame(eqInfo, repository.getEquilibriumAccountInfo(dot, accountId))
        assertSame(accountId, repository.getControllerAccount("polkadot", accountId))
        assertSame(stash, repository.getStashAccount("polkadot", accountId))
    }

    @Test
    fun `coingecko price is cached per currency and a missing price is not`(): Unit = runBlocking {
        coEvery { coingeckoApi.getSingleAssetPrice(priceIds = "dot", currency = "usd") } returns mapOf("dot" to mapOf("usd" to 7.5))
        coEvery { coingeckoApi.getSingleAssetPrice(priceIds = "dot", currency = "jpy") } returns mapOf("dot" to emptyMap<String, Double>())
        val repository = repository()

        assertEquals(BigDecimal("7.5"), repository.getSingleAssetPriceCoingecko("dot", "usd"))
        assertEquals(BigDecimal("7.5"), repository.getSingleAssetPriceCoingecko("dot", "usd"))
        assertNull(repository.getSingleAssetPriceCoingecko("dot", "jpy"))
        assertNull(repository.getSingleAssetPriceCoingecko("dot", "jpy"))

        coVerify(exactly = 1) { coingeckoApi.getSingleAssetPrice(priceIds = "dot", currency = "usd") }
        coVerify(exactly = 2) { coingeckoApi.getSingleAssetPrice(priceIds = "dot", currency = "jpy") }
    }

    @Test
    fun `remote app config is fetched from the config endpoint`(): Unit = runBlocking {
        val config = AppConfigRemote(minSupportedVersion = "4.3.0", excludedVersions = listOf("4.2.9"))
        coEvery { remoteConfigFetcher.getAppConfig(any()) } returns config

        assertSame(config, repository().getRemoteConfig())
    }

    @Test
    fun `vesting locked amount is the ormlvest entry of Balances Locks`(): Unit = runBlocking {
        stubSelectedAccount()
        coEvery { chainRegistry.getRuntimeOrNull("polkadot") } returns POLKADOT
        storage.scale = "0x08" + lock("vesting ", 1) + lock("ormlvest", 42)

        val locked = repository().getVestingLockedAmount("polkadot")

        assertEquals(BigInteger.valueOf(42), locked)
        assertEquals(1, storage.reads)
    }

    @Test
    fun `vesting locked amount is unknown without a runtime, a locks storage or a stored value`(): Unit = runBlocking {
        stubSelectedAccount()
        val noLocks = RuntimeSnapshot(
            mockk<TypeRegistry>(),
            RuntimeMetadata(BigInteger.valueOf(14), emptyMap(), ExtrinsicMetadata(BigInteger.valueOf(4), emptyList()))
        )
        coEvery { chainRegistry.getRuntimeOrNull("polkadot") } returnsMany listOf(null, noLocks)
        val repository = repository()

        assertNull(repository.getVestingLockedAmount("polkadot"))
        assertNull(repository.getVestingLockedAmount("polkadot"))
        assertEquals(0, storage.reads)
        val locksType = POLKADOT.metadata.balances().storage("Locks").returnType()
        assertNull(repository.bindVestingLockedAmount(null, POLKADOT, locksType))
        assertNull(repository.bindVestingLockedAmount("0x04" + lock("staking ", 5), POLKADOT, locksType))
    }

    @Test
    fun `claim fee is zero without an account and claims are submitted for the account`(): Unit = runBlocking {
        coEvery { chainsRepository.getChain("polkadot") } returns polkadot
        coEvery { accountRepository.getSelectedMetaAccount() } returnsMany listOf(
            WalletTestModels.metaAccount(substrate = null),
            WalletTestModels.metaAccount()
        )
        coEvery { extrinsicService.estimateFee(polkadot, any(), any(), any(), any(), any()) } returns BigInteger.TEN
        coEvery { extrinsicService.submitExtrinsic(polkadot, any(), any(), any(), any(), any()) } returns Result.success("0xclaim")
        val repository = repository()

        assertEquals(BigInteger.ZERO, repository.estimateClaimRewardsFee("polkadot"))
        assertEquals(BigInteger.TEN, repository.estimateClaimRewardsFee("polkadot"))
        assertEquals("0xclaim", repository.claimRewards(polkadot, WalletTestModels.substrateAccountId).getOrThrow())
        coVerify(exactly = 1) {
            extrinsicService.estimateFee(polkadot, match { it.contentEquals(WalletTestModels.substrateAccountId) }, any(), any(), any(), any())
        }
    }

    private fun stubSelectedAccount() {
        coEvery { accountRepository.getSelectedMetaAccount() } returns WalletTestModels.metaAccount()
        coEvery { chainsRepository.getChain("polkadot") } returns polkadot
    }

    private fun transfer() = Transfer(
        sender = polkadot.addressOf(WalletTestModels.substrateAccountId),
        recipient = polkadot.addressOf(ByteArray(32) { 9 }),
        amount = BigDecimal.ONE,
        chainAsset = dot
    )

    private fun repository() = WalletRepositoryImpl(
        substrateSource = substrateSource,
        ethereumSource = ethereumSource,
        operationDao = mockk(),
        httpExceptionHandler = HttpExceptionHandler(mockk()),
        phishingApi = mockk(),
        assetDao = assetDao,
        walletConstants = mockk(),
        phishingDao = phishingDao,
        coingeckoApi = coingeckoApi,
        chainRegistry = chainRegistry,
        remoteConfigFetcher = remoteConfigFetcher,
        accountRepository = accountRepository,
        chainsRepository = chainsRepository,
        extrinsicService = extrinsicService,
        remoteStorageSource = storage,
        pricesSyncService = pricesSyncService,
        transferServiceProvider = transferServiceProvider,
        tonRemoteSource = mockk(),
        portfolioVisibility = mockk(),
        currentAccountAssetReader = mockk()
    )

    /** Answers storage reads with [scale] on the Polkadot runtime snapshot. */
    private class LocksStorage : StorageDataSource {
        var scale: String? = null
        var reads = 0

        override suspend fun <T> query(
            chainId: String,
            keyBuilder: (RuntimeSnapshot) -> String?,
            at: String?,
            binding: (String?, RuntimeSnapshot) -> T
        ): T {
            reads++
            requireNotNull(keyBuilder(POLKADOT))
            return binding(scale, POLKADOT)
        }

        override suspend fun <K, T> queryKeys(
            chainId: String,
            keysBuilder: (RuntimeSnapshot) -> Map<String, K>,
            at: String?,
            binding: (String?, RuntimeSnapshot) -> T
        ): Map<K, T> = error("not used")

        override fun <T> observe(chainId: String, keyBuilder: (RuntimeSnapshot) -> String?, binder: (String?, RuntimeSnapshot) -> T): Flow<T> =
            error("not used")

        override suspend fun <K, T> queryByPrefix(
            chainId: String,
            prefixKeyBuilder: (RuntimeSnapshot) -> String?,
            keyExtractor: (String) -> K,
            binding: (String?, RuntimeSnapshot, K) -> T
        ): Map<K, T> = error("not used")

        override suspend fun <T> queryChildState(
            chainId: String,
            storageKeyBuilder: (RuntimeSnapshot) -> String,
            childKeyBuilder: suspend OutputStream.(RuntimeSnapshot) -> Unit,
            binder: (String?, RuntimeSnapshot) -> T
        ): T = error("not used")
    }

    private companion object {
        val POLKADOT: RuntimeSnapshot by lazy {
            val loader = requireNotNull(WalletRepositoryImplCoverageTest::class.java.classLoader)
            val metadata = GZIPInputStream(requireNotNull(loader.getResourceAsStream("parity/polkadot_metadata_v14.hex.gz")))
                .bufferedReader().use { it.readText() }.trim()
            val types = requireNotNull(loader.getResourceAsStream("parity/polkadot_parity_types.json"))
                .bufferedReader().use { it.readText() }
            RuntimeFactory(JsonFactory.create()).constructRuntime(metadata, types, 2005000).runtime
        }

        /** pallet_balances BalanceLock: id ([u8; 8]), amount (u128), reasons (All). */
        fun lock(id: String, amount: Int): String {
            require(id.length == 8)
            val idHex = id.encodeToByteArray().joinToString("") { "%02x".format(it) }
            val amountHex = "%02x".format(amount) + "00".repeat(15)
            return idHex + amountHex + "02"
        }
    }
}
