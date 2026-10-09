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
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionSpec
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcRequest
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatusResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.model.UniversalWalletRegistry
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
import org.junit.Assert.assertThrows
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
                    asset = ASSET_DEFINITION_ID,
                    quantity = "1.5",
                    scope = "global"
                ),
                IrohaAccountAssetListItem(
                    accountId = address,
                    asset = ASSET_DEFINITION_ID,
                    quantity = "0.25",
                    scope = "dataspace:1"
                )
            )
        )
        val metaAccount = metaAccount(chain, account.publicKey)

        val updates = IrohaBalanceLoader(chain, client).loadBalance(setOf(metaAccount))

        assertEquals(address, client.lastAccountId)
        assertEquals(UniversalWalletRegistry.taira.toriiBaseUrl, client.lastBaseUrl)
        assertEquals(IrohaToriiRoutes.MAX_LIMIT, client.lastLimit)
        assertEquals(0L, client.lastOffset)
        assertEquals(IrohaToriiRoutes.CountMode.Bounded, client.lastCountMode)
        assertEquals(IrohaToriiRoutes.MAX_LIMIT, client.lastDefinitionsLimit)
        assertEquals(0L, client.lastDefinitionsOffset)
        assertEquals(IrohaToriiRoutes.CountMode.Bounded, client.lastDefinitionsCountMode)
        assertEquals(1, updates.size)
        assertEquals(ASSET_DEFINITION_ID, updates.single().id)
        assertEquals(BigInteger("1750000000"), updates.single().freeInPlanks)
    }

    @Test
    fun `rejects wallet precision that disagrees with torii asset scale`() {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val client = FakeIrohaToriiClient(definitionScale = 8)

        assertThrows(IrohaAssetConfigurationException::class.java) {
            runBlocking {
                IrohaBalanceLoader(chain, client).loadBalance(setOf(metaAccount(chain, account.publicKey)))
            }
        }
    }

    @Test
    fun `rejects a missing torii asset scale instead of assuming wallet precision`() {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val client = FakeIrohaToriiClient(definitionScale = null)

        assertThrows(IrohaAssetConfigurationException::class.java) {
            runBlocking {
                IrohaBalanceLoader(chain, client).loadBalance(setOf(metaAccount(chain, account.publicKey)))
            }
        }
    }

    @Test
    fun `rejects every noncanonical or out of range torii quantity`() {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val invalidQuantities = listOf(
            " 1",
            "1.0",
            "1e0",
            "0.0000000001",
            BigInteger.ONE.shiftLeft(511).toString()
        )

        invalidQuantities.forEach { quantity ->
            val client = FakeIrohaToriiClient(
                items = listOf(
                    IrohaAccountAssetListItem(
                        accountId = address,
                        asset = ASSET_DEFINITION_ID,
                        quantity = quantity,
                        scope = "global"
                    )
                )
            )
            assertThrows(IrohaAssetConfigurationException::class.java) {
                runBlocking {
                    IrohaBalanceLoader(chain, client).loadBalance(setOf(metaAccount(chain, account.publicKey)))
                }
            }
        }
    }

    @Test
    fun `requires explicit complete pagination for definitions and account assets`() {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, account.publicKey)

        listOf<Boolean?>(null, true).forEach { hasMore ->
            listOf(
                FakeIrohaToriiClient(definitionHasMore = hasMore),
                FakeIrohaToriiClient(accountAssetsHasMore = hasMore)
            ).forEach { client ->
                assertThrows(IrohaAssetConfigurationException::class.java) {
                    runBlocking {
                        IrohaBalanceLoader(chain, client).loadBalance(setOf(metaAccount))
                    }
                }
            }
        }

        val wrongDefinitionMode = FakeIrohaToriiClient(definitionCountMode = "exact")
        assertThrows(IrohaAssetConfigurationException::class.java) {
            runBlocking {
                IrohaBalanceLoader(chain, wrongDefinitionMode).loadBalance(setOf(metaAccount))
            }
        }
    }

    @Test
    fun `requires bounded count mode and exact canonical account asset fields`() {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val canonical = IrohaAccountAssetListItem(
            accountId = address,
            asset = ASSET_DEFINITION_ID,
            quantity = "1",
            scope = "global"
        )
        val invalidClients = listOf(
            FakeIrohaToriiClient(
                items = listOf(canonical),
                accountAssetsCountMode = "exact"
            ),
            FakeIrohaToriiClient(items = listOf(canonical.copy(accountId = null))),
            FakeIrohaToriiClient(items = listOf(canonical.copy(accountId = address.dropLast(1) + "1"))),
            FakeIrohaToriiClient(items = listOf(canonical.copy(assetId = ASSET_DEFINITION_ID))),
            FakeIrohaToriiClient(items = listOf(canonical.copy(asset = "not-canonical"))),
            FakeIrohaToriiClient(items = listOf(canonical.copy(scope = null))),
            FakeIrohaToriiClient(items = listOf(canonical.copy(scope = "rewards"))),
            FakeIrohaToriiClient(items = listOf(canonical.copy(scope = "dataspace:01"))),
            FakeIrohaToriiClient(
                items = listOf(canonical.copy(scope = "dataspace:18446744073709551616"))
            )
        )

        invalidClients.forEach { client ->
            assertThrows(IrohaAssetConfigurationException::class.java) {
                runBlocking {
                    IrohaBalanceLoader(chain, client).loadBalance(
                        setOf(metaAccount(chain, account.publicKey))
                    )
                }
            }
        }
    }

    @Test
    fun `accepts canonical numeric maximum at wallet precision`() = runBlocking {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val maximum = BigInteger.ONE.shiftLeft(511).subtract(BigInteger.ONE)
        val updates = IrohaBalanceLoader(
            chain,
            FakeIrohaToriiClient(
                items = listOf(
                    IrohaAccountAssetListItem(
                        accountId = address,
                        asset = ASSET_DEFINITION_ID,
                        quantity = maximum.toString(),
                        scope = "global"
                    )
                )
            )
        ).loadBalance(setOf(metaAccount(chain, account.publicKey)))

        assertEquals(maximum.multiply(BigInteger.TEN.pow(9)), updates.single().freeInPlanks)
    }

    @Test
    fun `rejects duplicate asset scope rows`() {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val duplicate = IrohaAccountAssetListItem(
            accountId = address,
            asset = ASSET_DEFINITION_ID,
            quantity = "1",
            scope = "global"
        )

        assertThrows(IrohaAssetConfigurationException::class.java) {
            runBlocking {
                IrohaBalanceLoader(
                    chain,
                    FakeIrohaToriiClient(items = listOf(duplicate, duplicate.copy(quantity = "2")))
                ).loadBalance(setOf(metaAccount(chain, account.publicKey)))
            }
        }
    }

    @Test
    fun `rejects cross-scope balance sums above the precision adjusted numeric maximum`() {
        val chain = irohaChain()
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val address = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val maxQuantity = BigInteger.ONE.shiftLeft(511).subtract(BigInteger.ONE)
        val client = FakeIrohaToriiClient(
            items = listOf(
                IrohaAccountAssetListItem(
                    accountId = address,
                    asset = ASSET_DEFINITION_ID,
                    quantity = maxQuantity.toString(),
                    scope = "global"
                ),
                IrohaAccountAssetListItem(
                    accountId = address,
                    asset = ASSET_DEFINITION_ID,
                    quantity = "0.000000001",
                    scope = "dataspace:1"
                )
            )
        )

        assertThrows(IrohaAssetConfigurationException::class.java) {
            runBlocking {
                IrohaBalanceLoader(chain, client).loadBalance(
                    setOf(metaAccount(chain, account.publicKey))
                )
            }
        }
    }

    @Test
    fun `rejects noncanonical iroha chain identities`() {
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)

        listOf(
            UniversalWalletRegistry.taira.id,
            UniversalWalletRegistry.taira.chainId.uppercase(),
            UniversalWalletRegistry.nexus.id,
            "unknown-iroha-chain"
        ).forEach { chainId ->
            val chain = irohaChain().copy(id = chainId)
            assertThrows(IrohaAssetConfigurationException::class.java) {
                runBlocking {
                    IrohaBalanceLoader(chain, FakeIrohaToriiClient()).loadBalance(
                        setOf(metaAccount(chain, account.publicKey))
                    )
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                provider().invoke(chain)
            }
        }
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
        private val items: List<IrohaAccountAssetListItem> = emptyList(),
        private val definitionScale: Int? = 9,
        private val definitionHasMore: Boolean? = false,
        private val definitionCountMode: String = "bounded",
        private val accountAssetsHasMore: Boolean? = false,
        private val accountAssetsCountMode: String = "bounded"
    ) : IrohaToriiClient {
        var lastAccountId: String? = null
            private set
        var lastBaseUrl: String? = null
            private set
        var lastLimit: Int? = null
            private set
        var lastOffset: Long? = null
            private set
        var lastCountMode: IrohaToriiRoutes.CountMode? = null
            private set
        var lastDefinitionsLimit: Int? = null
            private set
        var lastDefinitionsOffset: Long? = null
            private set
        var lastDefinitionsCountMode: IrohaToriiRoutes.CountMode? = null
            private set

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
            lastAccountId = accountId
            lastBaseUrl = baseUrl
            lastLimit = limit
            lastOffset = offset
            lastCountMode = countMode

            return IrohaAccountAssetListResponse(
                items = items,
                hasMore = accountAssetsHasMore,
                countMode = accountAssetsCountMode,
                total = items.size.toLong()
            )
        }

        override suspend fun assetDefinitions(
            baseUrl: String?,
            limit: Int?,
            offset: Long?,
            countMode: IrohaToriiRoutes.CountMode?,
            network: UniversalWalletRegistry.IrohaNetwork
        ): IrohaAssetDefinitionListResponse {
            lastDefinitionsLimit = limit
            lastDefinitionsOffset = offset
            lastDefinitionsCountMode = countMode
            return IrohaAssetDefinitionListResponse(
                items = listOf(
                    IrohaAssetDefinitionListItem(
                        id = ASSET_DEFINITION_ID,
                        name = "XOR",
                        alias = null,
                        spec = IrohaAssetDefinitionSpec(scale = definitionScale)
                    )
                ),
                hasMore = definitionHasMore,
                countMode = definitionCountMode
            )
        }

        override suspend fun submitTransactionAndWait(
            noritoBytes: ByteArray,
            expectedHash: String,
            timeoutMillis: Long,
            pollIntervalMillis: Long,
            network: UniversalWalletRegistry.IrohaNetwork,
            baseUrl: String?
        ): jp.co.soramitsu.common.data.network.iroha.IrohaSubmitAndWaitOutcome =
            throw NotImplementedError()

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
        const val ASSET_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"

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
                id = network.chainId,
                paraId = null,
                rank = null,
                name = "Taira Testnet",
                minSupportedVersion = null,
                assets = listOf(irohaAsset(network.chainId)),
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
                id = ASSET_DEFINITION_ID,
                name = "XOR",
                symbol = "XOR",
                iconUrl = "",
                chainId = chainId,
                chainName = "Taira Testnet",
                chainIcon = null,
                isTestNet = true,
                priceId = null,
                precision = 9,
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
