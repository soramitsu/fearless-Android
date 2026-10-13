package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.chainAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerServiceInfo
import jp.co.soramitsu.common.data.network.solana.SolanaNativeBalance
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadata
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadataBatchResponse
import jp.co.soramitsu.common.data.network.solana.SolanaTokenBalance
import jp.co.soramitsu.common.data.network.solana.SolanaWalletAssetsResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletBalancesResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletStateResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletTransactionsResponse
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
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

class SolanaBalanceLoaderTest {

    @Test
    fun `meta account derives solana address from chain account public key`() {
        val chain = solanaChain(isTestNet = false)
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)

        assertEquals(account.address, metaAccount.address(chain))
        assertEquals(account.address, metaAccount.chainAddress(chain))
    }

    @Test
    fun `solana address resolution does not fall back to substrate account id`() {
        val chain = solanaChain(isTestNet = false)
        val metaAccount = metaAccount(
            chainAccounts = emptyMap(),
            substrateAccountId = ByteArray(32)
        )

        assertEquals(null, metaAccount.address(chain))
        assertEquals(null, metaAccount.chainAddress(chain))
    }

    @Test
    fun `loads native sol balance from validated mainnet address`() = runBlocking {
        val chain = solanaChain(isTestNet = false)
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)
        val client = FakeSolanaIndexerClient(response = balancesResponse(wallet = account.address, lamports = "2500000000"))

        val updates = SolanaBalanceLoader(chain, SolanaBalanceSync(client)).loadBalance(setOf(metaAccount))

        assertEquals(listOf(UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL), client.verifiedBaseUrls)
        assertEquals(listOf(account.address), client.receivedWallets)
        assertEquals(listOf(UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL), client.receivedBaseUrls)
        assertEquals(1, updates.size)
        assertEquals(chain.id, updates.single().chainId)
        assertEquals("SOL", updates.single().id)
        assertTrue(account.publicKey.contentEquals(updates.single().accountId))
        assertEquals(BigInteger("2500000000"), updates.single().freeInPlanks)
    }

    @Test
    fun `loads token balances from matching solana token mints`() = runBlocking {
        val chain = solanaChain(
            isTestNet = false,
            assets = listOf(
                solanaAsset(UniversalWalletRegistry.solanaMainnet.id, isTestNet = false),
                solanaTokenAsset(UniversalWalletRegistry.solanaMainnet.id, isTestNet = false)
            )
        )
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(
                wallet = account.address,
                lamports = "2500000000",
                tokens = listOf(tokenBalance(account.address, USDC_MINT, USDC_TOKEN_ACCOUNT, "42000000", 6, "spl-token"))
            )
        )

        val updates = SolanaBalanceLoader(chain, SolanaBalanceSync(client))
            .loadBalance(setOf(metaAccount))
            .sortedBy { it.id }

        assertEquals(listOf(UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL), client.verifiedBaseUrls)
        assertEquals(listOf("SOL", USDC_MINT), updates.map { it.id })
        assertEquals(BigInteger("2500000000"), updates[0].freeInPlanks)
        assertEquals(BigInteger("42000000"), updates[1].freeInPlanks)
        assertTrue(updates.all { account.publicKey.contentEquals(it.accountId) })
    }

    @Test
    fun `discovers positive spl and token 2022 holdings by mint without merging symbols`() = runBlocking {
        val chain = solanaChain(isTestNet = false)
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)
        val tokens = listOf(
            tokenBalance(account.address, USDC_MINT, USDC_TOKEN_ACCOUNT, "10", 6, "spl-token"),
            tokenBalance(account.address, SECOND_MINT, SECOND_TOKEN_ACCOUNT, "20", 6, "token-2022")
        )
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(wallet = account.address, tokens = tokens),
            metadata = tokens.map {
                SolanaTokenMetadata(
                    mint = it.mint,
                    exists = true,
                    program = it.program,
                    decimals = it.decimals,
                    name = "Duplicate symbol token",
                    symbol = "DUP",
                    syncedAt = 1L
                )
            }
        )
        val discovered = mutableListOf<Asset>()

        val updates = SolanaBalanceLoader(
            chain,
            SolanaBalanceSync(client),
            persistDiscoveredAssets = { discovered += it }
        ).loadBalance(setOf(metaAccount))

        assertEquals(setOf(USDC_MINT, SECOND_MINT), discovered.map { it.id }.toSet())
        assertTrue(discovered.all { it.symbol == "DUP" && it.type == ChainAssetType.Unknown && it.priceId == null })
        assertEquals(setOf("SOL", USDC_MINT, SECOND_MINT), updates.map { it.id }.toSet())
    }

    @Test
    fun `aggregates multiple token accounts for the same canonical mint`() = runBlocking {
        val chain = solanaChain(isTestNet = false)
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(
                wallet = account.address,
                tokens = listOf(
                    tokenBalance(account.address, USDC_MINT, USDC_TOKEN_ACCOUNT, "40", 6, "spl-token"),
                    tokenBalance(account.address, USDC_MINT, SECOND_TOKEN_ACCOUNT, "2", 6, "spl-token")
                )
            )
        )

        val update = SolanaBalanceLoader(chain, SolanaBalanceSync(client))
            .loadBalance(setOf(metaAccount))
            .single { it.id == USDC_MINT }

        assertEquals(BigInteger.valueOf(42), update.freeInPlanks)
    }

    @Test
    fun `successful complete scan zeros a previously held token omitted by indexer`() = runBlocking {
        val chain = solanaChain(
            isTestNet = false,
            assets = listOf(
                solanaAsset(UniversalWalletRegistry.solanaMainnet.id, isTestNet = false),
                solanaTokenAsset(UniversalWalletRegistry.solanaMainnet.id, isTestNet = false)
            )
        )
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(
                wallet = account.address,
                tokens = listOf(
                    tokenBalance(account.address, USDC_MINT, USDC_TOKEN_ACCOUNT, "42", 6, "spl-token")
                )
            )
        )
        val loader = SolanaBalanceLoader(chain, SolanaBalanceSync(client))

        assertEquals(
            BigInteger.valueOf(42),
            loader.loadBalance(setOf(metaAccount)).single { it.id == USDC_MINT }.freeInPlanks
        )

        client.balanceResponse = balancesResponse(wallet = account.address, tokens = emptyList())

        assertEquals(
            BigInteger.ZERO,
            loader.loadBalance(setOf(metaAccount)).single { it.id == USDC_MINT }.freeInPlanks
        )
    }

    @Test
    fun `malformed known token row fails scan and preserves last solana balances`() = runBlocking {
        val chain = solanaChain(
            isTestNet = false,
            assets = listOf(
                solanaAsset(UniversalWalletRegistry.solanaMainnet.id, isTestNet = false),
                solanaTokenAsset(UniversalWalletRegistry.solanaMainnet.id, isTestNet = false)
            )
        )
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(
                wallet = account.address,
                lamports = "55",
                tokens = listOf(
                    tokenBalance(account.address, USDC_MINT, USDC_TOKEN_ACCOUNT, "42", 6, "spl-token")
                )
            )
        )
        val scanState = NetworkScanStateStore(InMemoryPreferences())
        val loader = SolanaBalanceLoader(chain, SolanaBalanceSync(client), scanStateStore = scanState)

        val firstUpdates = loader.loadBalance(setOf(metaAccount))
        var storedSol = firstUpdates.single { it.id == "SOL" }.freeInPlanks
        var storedUsdc = firstUpdates.single { it.id == USDC_MINT }.freeInPlanks
        val successfulState = scanState.states.value.getValue(NetworkScanKey(metaAccount.id, chain.id))

        client.balanceResponse = balancesResponse(
            wallet = account.address,
            lamports = "0",
            tokens = listOf(
                tokenBalance(account.address, USDC_MINT, USDC_TOKEN_ACCOUNT, "-1", 6, "spl-token")
            )
        )
        loader.loadBalance(setOf(metaAccount)).forEach { update ->
            when (update.id) {
                "SOL" -> storedSol = update.freeInPlanks
                USDC_MINT -> storedUsdc = update.freeInPlanks
            }
        }

        assertEquals(BigInteger.valueOf(55), storedSol)
        assertEquals(BigInteger.valueOf(42), storedUsdc)
        val failedState = scanState.states.value.getValue(NetworkScanKey(metaAccount.id, chain.id))
        assertTrue(failedState.isStale)
        assertEquals(successfulState.lastSuccessMillis, failedState.lastSuccessMillis)
        assertEquals("INVALID_TOKEN_BALANCE", failedState.errorMessage)
    }

    @Test
    fun `inconsistent decimals across token accounts fail scan and preserve known balance`() = runBlocking {
        val chain = solanaChain(
            isTestNet = false,
            assets = listOf(
                solanaAsset(UniversalWalletRegistry.solanaMainnet.id, isTestNet = false),
                solanaTokenAsset(UniversalWalletRegistry.solanaMainnet.id, isTestNet = false)
            )
        )
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(
                wallet = account.address,
                tokens = listOf(
                    tokenBalance(account.address, USDC_MINT, USDC_TOKEN_ACCOUNT, "42", 6, "spl-token")
                )
            )
        )
        val scanState = NetworkScanStateStore(InMemoryPreferences())
        val loader = SolanaBalanceLoader(chain, SolanaBalanceSync(client), scanStateStore = scanState)

        var storedUsdc = loader.loadBalance(setOf(metaAccount))
            .single { it.id == USDC_MINT }
            .freeInPlanks
        val successfulState = scanState.states.value.getValue(NetworkScanKey(metaAccount.id, chain.id))

        client.balanceResponse = balancesResponse(
            wallet = account.address,
            tokens = listOf(
                tokenBalance(account.address, USDC_MINT, USDC_TOKEN_ACCOUNT, "40", 6, "spl-token"),
                tokenBalance(account.address, USDC_MINT, SECOND_TOKEN_ACCOUNT, "2", 9, "spl-token")
            )
        )
        loader.loadBalance(setOf(metaAccount))
            .singleOrNull { it.id == USDC_MINT }
            ?.let { storedUsdc = it.freeInPlanks }

        assertEquals(BigInteger.valueOf(42), storedUsdc)
        val failedState = scanState.states.value.getValue(NetworkScanKey(metaAccount.id, chain.id))
        assertTrue(failedState.isStale)
        assertEquals(successfulState.lastSuccessMillis, failedState.lastSuccessMillis)
        assertEquals("INVALID_TOKEN_BALANCE", failedState.errorMessage)
    }

    @Test
    fun `endpoint failure after success preserves last solana amount and marks scan stale`() = runBlocking {
        val chain = solanaChain(isTestNet = false)
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(wallet = account.address, lamports = "55")
        )
        val scanState = NetworkScanStateStore(InMemoryPreferences())
        val loader = SolanaBalanceLoader(chain, SolanaBalanceSync(client), scanStateStore = scanState)

        var storedAmount = loader.loadBalance(setOf(metaAccount)).single { it.id == "SOL" }.freeInPlanks
        client.balanceFailure = IllegalStateException("solana endpoint down")
        loader.loadBalance(setOf(metaAccount)).singleOrNull { it.id == "SOL" }?.let {
            storedAmount = it.freeInPlanks
        }

        assertEquals(BigInteger.valueOf(55), storedAmount)
        val state = scanState.states.value.getValue(NetworkScanKey(metaAccount.id, chain.id))
        assertTrue(state.isStale)
        assertTrue(state.lastSuccessMillis != null)
        assertEquals("solana endpoint down", state.errorMessage)
    }

    @Test
    fun `rejects malformed solana public key before balance fetch`() = runBlocking {
        val chain = solanaChain(isTestNet = false)
        val metaAccount = metaAccount(chain, publicKey = ByteArray(31), accountId = ByteArray(31))
        val client = FakeSolanaIndexerClient()

        val updates = SolanaBalanceLoader(chain, SolanaBalanceSync(client)).loadBalance(setOf(metaAccount))

        assertTrue(updates.isEmpty())
        assertTrue(client.receivedWallets.isEmpty())
        assertTrue(client.verifiedBaseUrls.isEmpty())
    }

    @Test
    fun `loads solana devnet balance through registry network`() = runBlocking {
        val chain = solanaChain(isTestNet = true)
        val account = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)
        val client = FakeSolanaIndexerClient(response = balancesResponse(wallet = account.address, lamports = "1"))

        val updates = SolanaBalanceLoader(chain, SolanaBalanceSync(client)).loadBalance(setOf(metaAccount))

        assertEquals(listOf(UniversalWalletRegistry.solanaDevnet.indexerBaseUrl), client.verifiedBaseUrls)
        assertEquals(listOf(account.address), client.receivedWallets)
        assertEquals(1, updates.size)
        assertEquals("SOL", updates.single().id)
        assertEquals(BigInteger.ONE, updates.single().freeInPlanks)
    }

    @Test
    fun `provider routes universal wallet solana chains to solana balance loader`() {
        val provider = BalanceLoaderProvider(
            chainRegistry = mock(ChainRegistry::class.java),
            remoteStorageSource = mock(RemoteStorageSource::class.java),
            ethereumRemoteSource = mock(EthereumRemoteSource::class.java),
            substrateSource = mock(SubstrateRemoteSource::class.java),
            operationDao = NoopOperationDao(),
            tonRemoteSource = mock(TonRemoteSource::class.java),
            chainsRepository = mock(ChainsRepository::class.java),
            tonSyncDataRepository = mock(TonSyncDataRepository::class.java),
            bitcoinIndexerClient = mock(BitcoinIndexerClient::class.java),
            solanaBalanceSync = SolanaBalanceSync(FakeSolanaIndexerClient()),
            irohaToriiClient = mock(IrohaToriiClient::class.java)
        )

        assertTrue(provider.invoke(solanaChain(isTestNet = false)) is SolanaBalanceLoader)
    }

    private class FakeSolanaIndexerClient(
        response: SolanaWalletBalancesResponse = balancesResponse(),
        private val metadata: List<SolanaTokenMetadata> = emptyList()
    ) : SolanaIndexerClient {
        var balanceResponse: SolanaWalletBalancesResponse = response
        var balanceFailure: Throwable? = null
        val verifiedBaseUrls = mutableListOf<String>()
        val receivedWallets = mutableListOf<String>()
        val receivedBaseUrls = mutableListOf<String?>()

        override suspend fun serviceInfo(baseUrl: String?): SolanaIndexerServiceInfo {
            error("Unexpected Solana service-info call")
        }

        override suspend fun verifyServiceInfo(baseUrl: String?): SolanaIndexerServiceInfo {
            verifiedBaseUrls += baseUrl.orEmpty()
            return SolanaIndexerServiceInfo(
                schemaVersion = 1,
                serviceId = "si.soramitsu.io",
                serviceName = "Solswap Indexer",
                ecosystem = "solana",
                chainId = "solana:mainnet",
                network = "mainnet",
                publicBaseUrl = "https://si.soramitsu.io",
                readOnly = true
            )
        }

        override suspend fun balances(wallet: String, baseUrl: String?): SolanaWalletBalancesResponse {
            receivedWallets += wallet
            receivedBaseUrls += baseUrl
            balanceFailure?.let { throw it }
            return balanceResponse
        }

        override suspend fun assets(wallet: String, baseUrl: String?): SolanaWalletAssetsResponse {
            error("Unexpected Solana assets call")
        }

        override suspend fun state(wallet: String, baseUrl: String?): SolanaWalletStateResponse {
            error("Unexpected Solana state call")
        }

        override suspend fun transactions(
            wallet: String,
            baseUrl: String?,
            before: String?,
            limit: Int
        ): SolanaWalletTransactionsResponse {
            error("Unexpected Solana transactions call")
        }

        override suspend fun tokenMetadata(mint: String, baseUrl: String?): SolanaTokenMetadata {
            error("Unexpected Solana metadata call")
        }

        override suspend fun tokenMetadataBatch(
            mints: List<String>,
            baseUrl: String?
        ): SolanaTokenMetadataBatchResponse = SolanaTokenMetadataBatchResponse(
            total = mints.size,
            syncedAt = 1L,
            tokens = metadata.filter { it.mint in mints }
        )
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

        fun balancesResponse(
            wallet: String = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk",
            lamports: String = "0",
            tokens: List<SolanaTokenBalance> = emptyList()
        ): SolanaWalletBalancesResponse {
            return SolanaWalletBalancesResponse(
                wallet = wallet,
                native = SolanaNativeBalance(lamports = lamports, uiAmountString = "0"),
                tokens = tokens,
                total = tokens.size + 1,
                syncedAt = 1_710_000_000_000L
            )
        }

        fun tokenBalance(
            owner: String,
            mint: String,
            accountAddress: String,
            amount: String,
            decimals: Int,
            program: String
        ): SolanaTokenBalance {
            return SolanaTokenBalance(
                accountAddress = accountAddress,
                mint = mint,
                owner = owner,
                program = program,
                programId = if (program == "token-2022") {
                    "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
                } else {
                    "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
                },
                amount = amount,
                decimals = decimals,
                uiAmountString = amount
            )
        }

        fun metaAccount(
            chain: Chain,
            publicKey: ByteArray,
            accountId: ByteArray
        ): MetaAccount {
            return metaAccount(
                chainAccounts = mapOf(
                    chain.id to MetaAccount.ChainAccount(
                        metaId = 1,
                        chain = chain,
                        publicKey = publicKey,
                        accountId = accountId,
                        cryptoType = CryptoType.ED25519,
                        accountName = "Solana"
                    )
                )
            )
        }

        fun metaAccount(
            chainAccounts: Map<String, MetaAccount.ChainAccount>,
            substrateAccountId: ByteArray? = null
        ): MetaAccount {
            return MetaAccount(
                id = 1,
                chainAccounts = chainAccounts,
                favoriteChains = emptyMap(),
                substratePublicKey = substrateAccountId,
                substrateCryptoType = CryptoType.SR25519,
                substrateAccountId = substrateAccountId,
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

        fun solanaChain(
            isTestNet: Boolean,
            assets: List<Asset>? = null
        ): Chain {
            val network = if (isTestNet) UniversalWalletRegistry.solanaDevnet else UniversalWalletRegistry.solanaMainnet

            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = network.name,
                minSupportedVersion = null,
                assets = assets ?: listOf(solanaAsset(network.id, isTestNet)),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(
                        Chain.ExternalApi.Section.Type.SOLANA,
                        network.indexerBaseUrl
                    ),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = isTestNet,
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

        fun solanaAsset(chainId: String, isTestNet: Boolean): Asset {
            return Asset(
                id = "SOL",
                name = "Solana",
                symbol = "SOL",
                iconUrl = "",
                chainId = chainId,
                chainName = "Solana",
                chainIcon = null,
                isTestNet = isTestNet,
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

        fun solanaTokenAsset(chainId: String, isTestNet: Boolean): Asset {
            return Asset(
                id = USDC_MINT,
                name = "USD Coin",
                symbol = "USDC",
                iconUrl = "",
                chainId = chainId,
                chainName = "Solana",
                chainIcon = null,
                isTestNet = isTestNet,
                priceId = null,
                precision = 6,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = false,
                type = ChainAssetType.Normal,
                currencyId = USDC_MINT,
                existentialDeposit = null,
                color = null,
                isNative = false
            )
        }

        const val USDC_MINT = "So11111111111111111111111111111111111111112"
        const val USDC_TOKEN_ACCOUNT = "9xQeWvG816bUx9EPfQ4vF5xXw4wa9VFeTuzA7h4sFnH"
        const val SECOND_MINT = "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5i7Twj9EonXy7"
        const val SECOND_TOKEN_ACCOUNT = "11111111111111111111111111111111"
    }
}
