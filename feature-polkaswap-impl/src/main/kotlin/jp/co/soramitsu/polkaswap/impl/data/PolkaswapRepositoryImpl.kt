package jp.co.soramitsu.polkaswap.impl.data

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.common.data.network.config.PolkaswapRemoteConfig
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.dexManager
import jp.co.soramitsu.common.utils.poolTBC
import jp.co.soramitsu.common.utils.poolXYK
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.models.backStrings
import jp.co.soramitsu.polkaswap.api.models.toFilters
import jp.co.soramitsu.polkaswap.api.models.toMarkets
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.bindings.bindDexInfos
import jp.co.soramitsu.polkaswap.impl.data.network.blockchain.swap
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojoList
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

class PolkaswapRepositoryImpl @Inject constructor(
    private val remoteConfigFetcher: RemoteConfigFetcher,
    private val remoteStorage: StorageDataSource,
    private val extrinsicService: ExtrinsicService,
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val featureToggleStore: ProductFeatureToggleStore,
    private val preferences: Preferences,
    private val walletRepository: WalletRepository,
) : PolkaswapRepository {

    override suspend fun getAvailableDexes(chainId: ChainId): List<BigInteger> {
        val remoteDexes = runCatching { dexInfos(chainId).keys }.getOrNull() ?: emptySet()
        val config = runCatching { getPolkaswapConfig().availableDexIds.map { it.code } }.getOrNull() ?: emptyList()
        return remoteDexes.filter { it in config }
    }

    private suspend fun dexInfos(chainId: ChainId): Map<BigInteger, String?> {
        waitForChain(chainId)
        return remoteStorage.queryByPrefix(
            prefixKeyBuilder = { it.metadata.dexManager()?.storage("DEXInfos")?.storageKey() },
            keyExtractor = { it.u32ArgumentFromStorageKey() },
            chainId = chainId
        ) { scale, runtime, _ ->
            scale?.let { bindDexInfos(it, runtime) }
        }
    }

    private suspend fun getPolkaswapConfig(): PolkaswapRemoteConfig {
        return remoteConfigFetcher.getPolkaswapConfig()
    }

    override fun observePoolXYKReserves(chainId: ChainId, fromTokenId: String, toTokenId: String): Flow<String> {
        return flow { emit(waitForChain(chainId)) }.flatMapLatest {
            remoteStorage.observe(
                chainId = chainId,
                keyBuilder = {
                    val from = Struct.Instance(
                        mapOf("code" to fromTokenId.fromHex().toList().map { it.toInt().toBigInteger() })
                    )
                    val to = Struct.Instance(
                        mapOf("code" to toTokenId.fromHex().toList().map { it.toInt().toBigInteger() })
                    )
                    it.metadata.poolXYK()?.storage("Reserves")?.storageKey(it, from, to)
                }
            ) { scale, _ ->
                scale.orEmpty()
            }
        }
    }

    override fun observePoolTBCReserves(chainId: ChainId, tokenId: String): Flow<String> {
        return flow { emit(waitForChain(chainId)) }.flatMapLatest {
            remoteStorage.observe(
                chainId = chainId,
                keyBuilder = {
                    val token = Struct.Instance(
                        mapOf("code" to tokenId.fromHex().toList().map { it.toInt().toBigInteger() })
                    )
                    it.metadata.poolTBC()?.storage("CollateralReserves")?.storageKey(it, token)
                }
            ) { scale, _ ->
                scale.orEmpty()
            }
        }
    }

    // Because if we get chain from the ChainRegistry, it will emit a chain
    // only after runtime for this chain will be ready
    private suspend fun waitForChain(chainId: String): Chain {
        return chainRegistry.getChain(chainId)
    }

    override suspend fun isPairAvailable(
        chainId: ChainId,
        tokenFromId: String,
        tokenToId: String,
        dexId: Int
    ): Boolean {
        val request = RuntimeRequest(
            method = "liquidityProxy_isPathAvailable",
            params = listOf(
                dexId,
                tokenFromId,
                tokenToId
            )
        )

        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(request, mapper = pojo<Boolean>().nonNull())
    }

    override suspend fun getSwapQuote(
        chainId: ChainId,
        tokenFromId: String,
        tokenToId: String,
        amount: BigInteger,
        desired: WithDesired,
        curMarkets: List<Market>,
        dexId: Int
    ): QuoteResponse? {
        return try {
            val request = RuntimeRequest(
                method = "liquidityProxy_quote",
                params = listOf(
                    dexId,
                    tokenFromId,
                    tokenToId,
                    amount.toString(),
                    desired.backString,
                    curMarkets.backStrings(),
                    curMarkets.toFilters()
                )
            )

            chainRegistry.awaitConnection(chainId).socketService.executeAsync(request, mapper = pojo<QuoteResponse>()).result
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun estimateSwapFee(
        chainId: ChainId,
        dexId: Int,
        inputAssetId: String,
        outputAssetId: String,
        amount: BigInteger,
        limit: BigInteger,
        filter: String,
        markets: List<String>,
        desired: WithDesired
    ): BigInteger {

        val chain = chainRegistry.getChain(chainId)
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain)
            ?: throw IllegalStateException("Add a SORA account to estimate this swap")
        return extrinsicService.estimateFee(chain, accountId) {
            swap(dexId, inputAssetId, outputAssetId, amount, limit, filter, markets, desired)
        }
    }

    override suspend fun swap(
        chainId: ChainId,
        dexId: Int,
        inputAssetId: String,
        outputAssetId: String,
        amount: BigInteger,
        limit: BigInteger,
        filter: String,
        markets: List<String>,
        desired: WithDesired
    ): Result<String> {
        return runCatching {
            val request = SwapSubmissionRequest(
                chainId = chainId,
                dexId = dexId,
                inputCurrencyId = inputAssetId,
                outputCurrencyId = outputAssetId,
                amount = amount,
                limit = limit,
                filter = filter,
                markets = markets,
                desired = desired
            )
            val preliminary = validateSwapSubmission(
                request = request,
                feeInPlanks = BigInteger.ZERO,
                expectedAuthorization = null
            )
            val feeInPlanks = extrinsicService.estimateFee(preliminary.chain, preliminary.accountId) {
                swap(dexId, inputAssetId, outputAssetId, amount, limit, filter, markets, desired)
            }

            // Fee estimation must not silently move authorization to a different wallet, account,
            // runtime or connection. Any drift invalidates the review and requires confirmation.
            val finalContext = validateSwapSubmission(
                request = request,
                feeInPlanks = feeInPlanks,
                expectedAuthorization = preliminary.authorization
            )

            // No asynchronous work is allowed between these final policy checks and submission.
            check(featureToggleStore.polkaswapMutationsEnabled) {
                "Polkaswap actions are temporarily disabled"
            }
            check(preferences.getBoolean(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false)) {
                "Accept the Polkaswap disclaimer before swapping"
            }
            extrinsicService.submitExtrinsic(finalContext.chain, finalContext.accountId) {
                swap(dexId, inputAssetId, outputAssetId, amount, limit, filter, markets, desired)
            }.getOrThrow()
        }
    }

    private suspend fun validateSwapSubmission(
        request: SwapSubmissionRequest,
        feeInPlanks: BigInteger,
        expectedAuthorization: SwapAuthorizationIdentity?
    ): ValidatedSwapContext {
        check(featureToggleStore.polkaswapMutationsEnabled) {
            "Polkaswap actions are temporarily disabled"
        }
        check(preferences.getBoolean(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false)) {
            "Accept the Polkaswap disclaimer before swapping"
        }
        check(request.chainId == soraMainChainId || request.chainId == soraTestChainId) {
            "Polkaswap swaps require an exact SORA network"
        }
        check(request.amount > BigInteger.ZERO) { "Swap amount must be greater than zero" }
        check(request.limit > BigInteger.ZERO) { "Swap limit must be greater than zero" }
        check(request.inputCurrencyId.isNotBlank() && request.outputCurrencyId.isNotBlank()) {
            "Swap assets must have exact registered currency ids"
        }
        check(request.inputCurrencyId != request.outputCurrencyId) {
            "Swap input and output assets must be different"
        }

        val authorization = resolveSwapAuthorization(request, expectedAuthorization)
        val chain = authorization.chain
        val accountId = authorization.accountId
        val metaId = authorization.authorization.account.metaId

        val inputAsset = chain.assets.singleOrNull { it.currencyId == request.inputCurrencyId }
            ?: throw IllegalStateException("Input currency id is not uniquely registered on SORA")
        chain.assets.singleOrNull { it.currencyId == request.outputCurrencyId }
            ?: throw IllegalStateException("Output currency id is not uniquely registered on SORA")
        val feeAsset = chain.assets.singleOrNull { it.isUtility && it.symbol.equals("XOR", true) }
            ?: throw IllegalStateException("The exact XOR fee asset is unavailable on SORA")

        val inputBalance = walletRepository.getAsset(
            metaId,
            accountId,
            inputAsset,
            chain.minSupportedVersion
        ) ?: throw IllegalStateException("Input asset balance is unavailable")
        val feeBalance = if (feeAsset.id == inputAsset.id) {
            inputBalance
        } else {
            walletRepository.getAsset(
                metaId,
                accountId,
                feeAsset,
                chain.minSupportedVersion
            ) ?: throw IllegalStateException("XOR fee balance is unavailable")
        }
        validateBalances(request, inputBalance, feeBalance, inputAsset.id == feeAsset.id, feeInPlanks)

        val finalAuthorization = if (expectedAuthorization == null) {
            authorization
        } else {
            // Balances are asynchronous. Re-pin every authority input after they resolve so the
            // submit call cannot inherit a wallet/runtime/node change that happened meanwhile.
            resolveSwapAuthorization(request, expectedAuthorization)
        }
        return ValidatedSwapContext(
            chain = finalAuthorization.chain,
            accountId = finalAuthorization.accountId,
            authorization = finalAuthorization.authorization
        )
    }

    private suspend fun resolveSwapAuthorization(
        request: SwapSubmissionRequest,
        expected: SwapAuthorizationIdentity?
    ): ValidatedSwapContext {
        val chain = chainRegistry.getChain(request.chainId)
        check(chain.id == request.chainId) { "Resolved SORA chain identity does not match the request" }
        val runtime = chainRegistry.getRuntimeOrNull(request.chainId)
            ?: throw IllegalStateException("SORA runtime is unavailable")
        val connection = chainRegistry.awaitConnection(request.chainId)
        check(connection.chain.id == request.chainId) {
            "Resolved SORA connection identity does not match the request"
        }

        val metaAccount = accountRepository.getSelectedMetaAccount()
        val accountId = metaAccount.accountId(chain)?.copyOf()
            ?: throw IllegalStateException("Add a SORA account before swapping")
        check(!accountRepository.isWalletRecoveryRequired(metaAccount.id)) {
            "Recover this wallet's signing material before swapping"
        }
        val hasSigningMaterial = if (metaAccount.hasChainAccount(chain.id)) {
            accountRepository.getChainAccountSecrets(metaAccount.id, chain.id) != null
        } else {
            accountRepository.getSubstrateSecrets(metaAccount.id) != null
        }
        check(hasSigningMaterial) {
            "This SORA account is watch-only or has no supported signer"
        }

        val authorization = SwapAuthorizationIdentity(
            account = metaAccount.swapAccountIdentity(chain, accountId),
            runtime = runtime,
            connection = connection,
            socketService = connection.socketService,
            selectedNodeUrl = connection.selectedNode?.url,
            connectionState = connection.state.value.authorizationIdentity()
        )
        authorization.requireMatches(expected)

        return ValidatedSwapContext(chain, accountId, authorization)
    }

    private fun validateBalances(
        request: SwapSubmissionRequest,
        inputBalance: WalletAsset,
        feeBalance: WalletAsset,
        inputIsFeeAsset: Boolean,
        feeInPlanks: BigInteger
    ) {
        check(feeInPlanks >= BigInteger.ZERO) { "XOR fee estimate must not be negative" }
        val maximumInput = if (request.desired == WithDesired.INPUT) request.amount else request.limit
        val requiredInput = maximumInput + if (inputIsFeeAsset) feeInPlanks else BigInteger.ZERO
        check(inputBalance.sendAvailableInPlanks >= requiredInput) {
            "Input asset balance is insufficient for this swap"
        }
        if (!inputIsFeeAsset) {
            check(feeBalance.sendAvailableInPlanks >= feeInPlanks) {
                "XOR balance is insufficient for the network fee"
            }
        }
    }

    private data class SwapSubmissionRequest(
        val chainId: ChainId,
        val dexId: Int,
        val inputCurrencyId: String,
        val outputCurrencyId: String,
        val amount: BigInteger,
        val limit: BigInteger,
        val filter: String,
        val markets: List<String>,
        val desired: WithDesired
    )

    private data class ValidatedSwapContext(
        val chain: Chain,
        val accountId: ByteArray,
        val authorization: SwapAuthorizationIdentity
    )

    private data class SwapAccountIdentity(
        val metaId: Long,
        val accountIdHex: String,
        val accountAddress: String?,
        val source: String,
        val publicKeyHex: String?,
        val cryptoType: String?
    )

    private data class SwapAuthorizationIdentity(
        val account: SwapAccountIdentity,
        val runtime: RuntimeSnapshot,
        val connection: ChainConnection,
        val socketService: SocketService,
        val selectedNodeUrl: String?,
        val connectionState: SwapConnectionStateIdentity
    ) {
        fun requireMatches(expected: SwapAuthorizationIdentity?) {
            if (expected == null) return

            check(account.metaId == expected.account.metaId) {
                "The selected wallet changed. Review and confirm this swap again."
            }
            check(account == expected.account) {
                "The selected SORA account changed. Review and confirm this swap again."
            }
            check(runtime === expected.runtime) {
                "The SORA runtime changed. Review and confirm this swap again."
            }
            check(
                connection === expected.connection &&
                    socketService === expected.socketService &&
                    selectedNodeUrl == expected.selectedNodeUrl &&
                    connectionState == expected.connectionState
            ) {
                "The SORA connection changed. Review and confirm this swap again."
            }
        }
    }

    private data class SwapConnectionStateIdentity(
        val kind: String,
        val url: String?,
        val reconnectAttempt: Int?
    )

    private fun MetaAccount.swapAccountIdentity(
        chain: Chain,
        accountId: ByteArray
    ): SwapAccountIdentity {
        val chainAccount = chainAccounts[chain.id]
        return SwapAccountIdentity(
            metaId = id,
            accountIdHex = accountId.toHexString(withPrefix = true),
            accountAddress = chain.addressOf(accountId),
            source = chainAccount?.let { "chain:${it.metaId}" } ?: "substrate",
            publicKeyHex = (chainAccount?.publicKey ?: substratePublicKey)
                ?.toHexString(withPrefix = true),
            cryptoType = (chainAccount?.cryptoType ?: substrateCryptoType)?.name
        )
    }

    private fun SocketStateMachine.State.authorizationIdentity(): SwapConnectionStateIdentity {
        return when (this) {
            is SocketStateMachine.State.WaitingForReconnect ->
                SwapConnectionStateIdentity("waiting", url, attempt)
            is SocketStateMachine.State.Connecting ->
                SwapConnectionStateIdentity("connecting", url, attempt)
            is SocketStateMachine.State.Connected ->
                SwapConnectionStateIdentity("connected", url, null)
            SocketStateMachine.State.Disconnected ->
                SwapConnectionStateIdentity("disconnected", null, null)
            is SocketStateMachine.State.Paused ->
                SwapConnectionStateIdentity("paused", url, null)
        }
    }

    override suspend fun getAvailableSources(chainId: ChainId, tokenId1: String, tokenId2: String, dexes: List<Int>): Map<Int, List<Market>> {
        return dexes.associateWith { dexId ->
            getEnabledMarkets(chainId, dexId, tokenId1, tokenId2)
        }
    }

    private suspend fun getEnabledMarkets(chainId: ChainId, dexId: Int, tokenId1: String, tokenId2: String): List<Market> {
        return try {
            val request = RuntimeRequest(
                "liquidityProxy_listEnabledSourcesForPath",
                listOf(dexId, tokenId1, tokenId2)
            )
            return chainRegistry.awaitConnection(chainId).socketService.executeAsync(request, mapper = pojoList<String>().nonNull()).toMarkets()
        } catch (e: RpcException) {
            listOf()
        }
    }
}
