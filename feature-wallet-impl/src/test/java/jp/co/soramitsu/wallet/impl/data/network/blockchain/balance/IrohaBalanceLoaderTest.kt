package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcRequest
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatusResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.iroha.IrohaTransactionSubmissionReceipt
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.TonSyncDataRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class IrohaBalanceLoaderTest {

    @Test
    fun `loads taira account asset balances through torii`() = runBlocking {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val client = FakeIrohaToriiClient(
            items = listOf(
                IrohaAccountAssetListItem(
                    accountId = address,
                    asset = "xor#sora",
                    quantity = "1.5"
                ),
                IrohaAccountAssetListItem(
                    accountId = address,
                    asset = "xor#sora",
                    quantity = "0.25",
                    scope = "rewards"
                )
            )
        )
        val metaAccount = metaAccount(chain, account.publicKey)

        val updates = IrohaBalanceLoader(chain, client).loadBalance(setOf(metaAccount))

        assertEquals(address, client.lastAccountId)
        assertEquals(UniversalWalletRegistry.taira.toriiBaseUrl, client.lastBaseUrl)
        assertEquals(IrohaToriiRoutes.MAX_LIMIT, client.lastLimit)
        assertEquals(IrohaToriiRoutes.CountMode.Bounded, client.lastCountMode)
        assertEquals(1, updates.size)
        assertEquals("xor#sora", updates.single().id)
        assertEquals(BigInteger("1750000000000000000"), updates.single().freeInPlanks)
    }

    @Test
    fun `paginates and discovers every held iroha asset definition`() = runBlocking {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val items = (0..IrohaToriiRoutes.MAX_LIMIT).map { index ->
            IrohaAccountAssetListItem(
                accountId = address,
                asset = "asset$index#portfolio",
                assetAlias = "DUP",
                quantity = "1"
            )
        }
        val client = FakeIrohaToriiClient(items)
        val discovered = mutableListOf<Asset>()

        val updates = IrohaBalanceLoader(
            chain,
            client,
            persistDiscoveredAssets = { discovered += it }
        ).loadBalance(setOf(metaAccount(chain, account.publicKey)))

        assertEquals(listOf(0L, IrohaToriiRoutes.MAX_LIMIT.toLong()), client.offsets)
        assertEquals(items.size, discovered.size)
        assertEquals(items.size, discovered.map { it.id }.distinct().size)
        assertTrue(discovered.all { it.symbol == "DUP" && it.type == ChainAssetType.Unknown && it.priceId == null })
        assertEquals(items.size, updates.count { it.freeInPlanks?.signum() == 1 })
        assertEquals(BigInteger.ZERO, updates.single { it.id == "xor#sora" }.freeInPlanks)
    }

    @Test
    fun `uses asset definition precision instead of observed quantity scale`() = runBlocking {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val client = FakeIrohaToriiClient(
            items = listOf(IrohaAccountAssetListItem(address, "usd#bank", quantity = "1")),
            definitions = listOf(
                IrohaAssetDefinitionListItem(
                    id = "usd#bank",
                    name = "Bank USD",
                    alias = "USD",
                    metadata = mapOf("precision" to 2.0)
                )
            )
        )
        val discovered = mutableListOf<Asset>()

        val update = IrohaBalanceLoader(chain, client, persistDiscoveredAssets = { discovered += it })
            .loadBalance(setOf(metaAccount(chain, account.publicKey)))
            .single { it.id == "usd#bank" }

        assertEquals(2, discovered.single().precision)
        assertEquals(BigInteger.valueOf(100), update.freeInPlanks)
    }

    @Test
    fun `paginates definitions beyond torii limit before converting held amount`() = runBlocking {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val heldDefinitionId = "late500#catalog"
        val definitions = (0 until IrohaToriiRoutes.MAX_LIMIT).map { index ->
            IrohaAssetDefinitionListItem(id = "early$index#catalog")
        } + IrohaAssetDefinitionListItem(
            id = heldDefinitionId,
            alias = "LATE",
            metadata = mapOf("precision" to 4)
        )
        val client = FakeIrohaToriiClient(
            items = listOf(IrohaAccountAssetListItem(address, heldDefinitionId, quantity = "1")),
            definitions = definitions
        )

        val update = IrohaBalanceLoader(chain, client)
            .loadBalance(setOf(metaAccount(chain, account.publicKey)))
            .single { it.id == heldDefinitionId }

        assertEquals(listOf(0L, IrohaToriiRoutes.MAX_LIMIT.toLong()), client.definitionOffsets)
        assertEquals(BigInteger("10000"), update.freeInPlanks)
    }

    @Test
    fun `successful complete scan zeros a previously held asset omitted by torii`() = runBlocking {
        val baseChain = irohaChain()
        val previousHolding = irohaAsset(baseChain.id).copy(
            id = "usd#bank",
            name = "Bank USD",
            symbol = "USD",
            precision = 2,
            isUtility = false,
            isNative = false,
            type = ChainAssetType.Unknown,
            currencyId = "usd#bank"
        )
        val chain = baseChain.copy(assets = baseChain.assets + previousHolding)
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val client = FakeIrohaToriiClient(
            items = listOf(IrohaAccountAssetListItem(address, "usd#bank", quantity = "1.25"))
        )
        val loader = IrohaBalanceLoader(chain, client)
        val metaAccount = metaAccount(chain, account.publicKey)

        assertEquals(
            BigInteger.valueOf(125),
            loader.loadBalance(setOf(metaAccount)).single { it.id == "usd#bank" }.freeInPlanks
        )

        client.accountAssetItems = emptyList()

        assertEquals(
            BigInteger.ZERO,
            loader.loadBalance(setOf(metaAccount)).single { it.id == "usd#bank" }.freeInPlanks
        )
    }

    @Test
    fun `mixed valid and malformed iroha quantities fail scan without partial balance`() = runBlocking {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val client = FakeIrohaToriiClient(
            items = listOf(IrohaAccountAssetListItem(address, "xor#sora", quantity = "2"))
        )
        val metaAccount = metaAccount(chain, account.publicKey)
        val scanState = NetworkScanStateStore(InMemoryPreferences())
        val loader = IrohaBalanceLoader(chain, client, scanStateStore = scanState)

        var storedAmount = loader.loadBalance(setOf(metaAccount)).single().freeInPlanks
        val successfulState = scanState.states.value.getValue(NetworkScanKey(metaAccount.id, chain.id))
        client.accountAssetItems = listOf(
            IrohaAccountAssetListItem(address, "xor#sora", quantity = "3"),
            IrohaAccountAssetListItem(address, "xor#sora", quantity = "not-a-quantity")
        )
        loader.loadBalance(setOf(metaAccount)).singleOrNull()?.let { storedAmount = it.freeInPlanks }

        assertEquals(BigInteger("2000000000000000000"), storedAmount)
        val failedState = scanState.states.value.getValue(NetworkScanKey(metaAccount.id, chain.id))
        assertTrue(failedState.isStale)
        assertEquals(successfulState.lastSuccessMillis, failedState.lastSuccessMillis)
        assertEquals("Malformed Iroha quantity for xor#sora", failedState.errorMessage)
    }

    @Test
    fun `endpoint failure after success preserves last iroha amount and marks scan stale`() = runBlocking {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val client = FakeIrohaToriiClient(
            items = listOf(IrohaAccountAssetListItem(address, "xor#sora", quantity = "2"))
        )
        val metaAccount = metaAccount(chain, account.publicKey)
        val scanState = NetworkScanStateStore(InMemoryPreferences())
        val loader = IrohaBalanceLoader(chain, client, scanStateStore = scanState)

        var storedAmount = loader.loadBalance(setOf(metaAccount)).single().freeInPlanks
        client.accountAssetsFailure = IllegalStateException("iroha endpoint down")
        loader.loadBalance(setOf(metaAccount)).singleOrNull()?.let { storedAmount = it.freeInPlanks }

        assertEquals(BigInteger("2000000000000000000"), storedAmount)
        val state = scanState.states.value.getValue(NetworkScanKey(metaAccount.id, chain.id))
        assertTrue(state.isStale)
        assertTrue(state.lastSuccessMillis != null)
        assertEquals("iroha endpoint down", state.errorMessage)
    }

    @Test
    fun `provider routes universal wallet iroha chains to torii iroha loader`() = runBlocking {
        val loader = provider().invoke(irohaChain())

        assertTrue(loader is IrohaBalanceLoader)
    }

    private fun provider(
        irohaToriiClient: IrohaToriiClient = FakeIrohaToriiClient()
    ): BalanceLoaderProvider {
        return BalanceLoaderProvider(
            chainRegistry = mock(ChainRegistry::class.java),
            remoteStorageSource = mock(RemoteStorageSource::class.java),
            ethereumRemoteSource = mock(EthereumRemoteSource::class.java),
            substrateSource = mock(SubstrateRemoteSource::class.java),
            operationDao = NoopOperationDao(),
            tonRemoteSource = mock(TonRemoteSource::class.java),
            chainsRepository = mock(ChainsRepository::class.java),
            tonSyncDataRepository = mock(TonSyncDataRepository::class.java),
            bitcoinIndexerClient = mock(BitcoinIndexerClient::class.java),
            solanaBalanceSync = SolanaBalanceSync(mock(SolanaIndexerClient::class.java)),
            irohaToriiClient = irohaToriiClient
        )
    }

    private class FakeIrohaToriiClient(
        items: List<IrohaAccountAssetListItem> = emptyList(),
        private val definitions: List<IrohaAssetDefinitionListItem> = emptyList()
    ) : IrohaToriiClient {
        var accountAssetItems: List<IrohaAccountAssetListItem> = items
        var accountAssetsFailure: Throwable? = null
        var lastAccountId: String? = null
            private set
        var lastBaseUrl: String? = null
            private set
        var lastLimit: Int? = null
            private set
        var lastCountMode: IrohaToriiRoutes.CountMode? = null
            private set
        val offsets = mutableListOf<Long>()
        val definitionOffsets = mutableListOf<Long>()

        override suspend fun health(baseUrl: String?): String = "ok"

        override suspend fun accounts(
            baseUrl: String?,
            limit: Int?,
            offset: Long?,
            countMode: IrohaToriiRoutes.CountMode?
        ): IrohaAccountListResponse = throw NotImplementedError()

        override suspend fun account(
            accountId: String,
            baseUrl: String?,
            network: UniversalWalletRegistry.IrohaNetwork
        ): IrohaAccountListItem = throw NotImplementedError()

        override suspend fun accountAssets(
            accountId: String,
            baseUrl: String?,
            limit: Int?,
            offset: Long?,
            countMode: IrohaToriiRoutes.CountMode?,
            asset: String?,
            scope: String?,
            network: UniversalWalletRegistry.IrohaNetwork
        ): IrohaAccountAssetListResponse {
            accountAssetsFailure?.let { throw it }
            lastAccountId = accountId
            lastBaseUrl = baseUrl
            lastLimit = limit
            lastCountMode = countMode
            val resolvedOffset = offset ?: 0L
            offsets += resolvedOffset
            val resolvedLimit = limit ?: accountAssetItems.size
            val pageItems = accountAssetItems.drop(resolvedOffset.toInt()).take(resolvedLimit)

            return IrohaAccountAssetListResponse(
                items = pageItems,
                hasMore = resolvedOffset + pageItems.size < accountAssetItems.size,
                countMode = countMode?.apiValue ?: "bounded",
                total = accountAssetItems.size.toLong()
            )
        }

        override suspend fun assetDefinitions(baseUrl: String?): IrohaAssetDefinitionListResponse =
            IrohaAssetDefinitionListResponse(
                items = definitions,
                hasMore = false,
                countMode = "exact",
                total = definitions.size.toLong()
            )

        override suspend fun assetDefinitionsPage(
            baseUrl: String?,
            limit: Int?,
            offset: Long?,
            countMode: IrohaToriiRoutes.CountMode?
        ): IrohaAssetDefinitionListResponse {
            val resolvedOffset = offset ?: 0L
            val resolvedLimit = limit ?: definitions.size
            definitionOffsets += resolvedOffset
            val pageItems = definitions.drop(resolvedOffset.toInt()).take(resolvedLimit)
            return IrohaAssetDefinitionListResponse(
                items = pageItems,
                hasMore = resolvedOffset + pageItems.size < definitions.size,
                countMode = countMode?.apiValue ?: "bounded",
                total = definitions.size.toLong()
            )
        }

        override suspend fun submitTransaction(
            noritoBytes: ByteArray,
            baseUrl: String?
        ): IrohaTransactionSubmissionReceipt = throw NotImplementedError()

        override suspend fun transactionStatus(
            hash: String,
            baseUrl: String?,
            scope: IrohaToriiRoutes.TransactionStatusScope
        ): IrohaPipelineTransactionStatusResponse = throw NotImplementedError()

        override suspend fun mcpCapabilities(
            network: UniversalWalletRegistry.IrohaNetwork,
            baseUrl: String?
        ): Map<String, Any?> = throw NotImplementedError()

        override suspend fun mcpJsonRpc(
            request: IrohaMcpJsonRpcRequest,
            network: UniversalWalletRegistry.IrohaNetwork,
            baseUrl: String?
        ): IrohaMcpJsonRpcResponse = throw NotImplementedError()
    }

    private class NoopOperationDao : OperationDao() {
        override suspend fun insert(operation: OperationLocal) = Unit

        override suspend fun insertAll(operations: List<OperationLocal>) = Unit

        override fun observe(
            address: String,
            chainId: String,
            chainAssetId: String,
            statusUp: OperationLocal.Status
        ): Flow<List<OperationLocal>> = flowOf(emptyList())

        override suspend fun getOperation(hash: String): OperationLocal? = null

        override suspend fun getOperations(): List<OperationLocal> = emptyList()

        override suspend fun getCompletedModuleOperations(
            address: String,
            chainId: String,
            chainAssetId: String,
            module: String,
            status: OperationLocal.Status
        ): List<OperationLocal> = emptyList()

        override fun observeOperations(): Flow<List<OperationLocal>> = flowOf(emptyList())

        override fun observeOperations(chainId: String): Flow<List<OperationLocal>> = flowOf(emptyList())

        override fun observeOperationAddresses(chainId: String, address: String, limit: Int): Flow<List<String>> = flowOf(emptyList())

        override fun getOperationAddresses(chainId: String, address: String, limit: Int): List<String> = emptyList()

        override suspend fun clearBySource(
            address: String,
            chainId: String,
            chainAssetId: String,
            source: OperationLocal.Source
        ): Int = 0

        override suspend fun clearOld(
            address: String,
            chainId: String,
            chainAssetId: String,
            minTime: Long
        ): Int = 0

        override suspend fun clearByHashes(
            address: String,
            chainId: String,
            chainAssetId: String,
            hashes: Set<String>
        ): Int = 0
    }

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        fun metaAccount(chain: Chain, publicKey: ByteArray): MetaAccount {
            return MetaAccount(
                id = 1,
                chainAccounts = mapOf(
                    chain.id to MetaAccount.ChainAccount(
                        metaId = 1,
                        chain = chain,
                        publicKey = publicKey,
                        accountId = publicKey,
                        cryptoType = CryptoType.ED25519,
                        accountName = "Iroha"
                    )
                ),
                favoriteChains = emptyMap(),
                substratePublicKey = ByteArray(32),
                substrateCryptoType = CryptoType.SR25519,
                substrateAccountId = ByteArray(32),
                ethereumAddress = null,
                ethereumPublicKey = null,
                tonPublicKey = null,
                isSelected = true,
                isBackedUp = true,
                googleBackupAddress = null,
                name = "Wallet",
                initialized = true
            )
        }

        fun irohaChain(): Chain {
            val network = UniversalWalletRegistry.taira

            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = "Taira Testnet",
                minSupportedVersion = null,
                assets = listOf(irohaAsset(network.id)),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(
                        Chain.ExternalApi.Section.Type.IROHA,
                        network.toriiBaseUrl ?: "https://taira.sora.org"
                    ),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = true,
                hasCrowdloans = false,
                parentId = null,
                supportStakingPool = false,
                isEthereumChain = false,
                chainlinkProvider = false,
                supportNft = false,
                isUsesAppId = false,
                identityChain = null,
                ecosystem = Ecosystem.Substrate,
                remoteAssetsSource = null
            )
        }

        fun irohaAsset(chainId: String): Asset {
            return Asset(
                id = "xor#sora",
                name = "XOR",
                symbol = "XOR",
                iconUrl = "",
                chainId = chainId,
                chainName = "Taira Testnet",
                chainIcon = null,
                isTestNet = true,
                priceId = null,
                precision = 18,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = true,
                type = ChainAssetType.Normal,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = true
            )
        }
    }
}
