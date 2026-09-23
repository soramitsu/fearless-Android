package jp.co.soramitsu.liquiditypools.impl.data

import androidx.room.withTransaction
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.androidfoundation.format.addHexPrefix
import jp.co.soramitsu.androidfoundation.format.mapBalance
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.fromHex
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.PoolDao
import jp.co.soramitsu.coredb.model.BasicPoolLocal
import jp.co.soramitsu.coredb.model.UserPoolJoinedLocal
import jp.co.soramitsu.coredb.model.UserPoolJoinedLocalNullable
import jp.co.soramitsu.coredb.model.UserPoolLocal
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.dataType.uint32
import jp.co.soramitsu.fearless_utils.scale.sizedByteArray
import jp.co.soramitsu.fearless_utils.scale.uint128
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojoList
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.scale
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.liquiditypools.data.PoolDataDto
import jp.co.soramitsu.liquiditypools.data.PoolsRepository
import jp.co.soramitsu.liquiditypools.domain.LiquidityMutationAction
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.domain.model.UserPoolData
import jp.co.soramitsu.liquiditypools.impl.data.network.liquidityAdd
import jp.co.soramitsu.liquiditypools.impl.data.network.removeLiquidity
import jp.co.soramitsu.liquiditypools.impl.util.PolkaswapFormulas
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.supervisorScope
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private val DEPOSIT_LIQUIDITY_CALL = SoraRuntimeCall(Modules.POOL_XYK, "deposit_liquidity")
private val REMOVE_LIQUIDITY_CALL = SoraRuntimeCall(Modules.POOL_XYK, "withdraw_liquidity")
private val INITIALIZE_POOL_CALL = SoraRuntimeCall(Modules.POOL_XYK, "initialize_pool")
private val REGISTER_PAIR_CALL = SoraRuntimeCall("TradingPair", "register")
private val BATCH_ALL_CALL = SoraRuntimeCall("Utility", "batch_all")
private val REMOVE_LIQUIDITY_CALLS = setOf(REMOVE_LIQUIDITY_CALL)

private fun addLiquidityCalls(pairEnabled: Boolean, pairPresented: Boolean): Set<SoraRuntimeCall> = buildSet {
    add(DEPOSIT_LIQUIDITY_CALL)
    if (!pairPresented) {
        add(INITIALIZE_POOL_CALL)
        add(BATCH_ALL_CALL)
        if (!pairEnabled) add(REGISTER_PAIR_CALL)
    }
}

private data class AddLiquidityRequest(
    val chainId: ChainId,
    val address: String,
    val tokenBase: CanonicalSoraAsset,
    val tokenTarget: CanonicalSoraAsset,
    val amountBase: BigDecimal,
    val amountTarget: BigDecimal,
    val pairEnabled: Boolean,
    val pairPresented: Boolean,
    val slippageTolerance: Double
)

private data class RemoveLiquidityRequest(
    val chainId: ChainId,
    val tokenBase: CanonicalSoraAsset,
    val tokenTarget: CanonicalSoraAsset,
    val markerAssetDesired: BigDecimal,
    val firstAmountMin: BigDecimal,
    val secondAmountMin: BigDecimal
)

private data class ValidatedAddLiquidity(
    val context: SoraMutationContext,
    val tokenBase: Asset,
    val tokenTarget: Asset,
    val dexId: Int,
    val amountBase: BigInteger,
    val amountTarget: BigInteger,
    val amountBaseMin: BigInteger,
    val amountTargetMin: BigInteger
)

private data class ValidatedRemoveLiquidity(
    val context: SoraMutationContext,
    val tokenBase: Asset,
    val tokenTarget: Asset,
    val dexId: Int,
    val markerAssetDesired: BigInteger,
    val firstAmountMin: BigInteger,
    val secondAmountMin: BigInteger
)

@Suppress("LargeClass", "MagicNumber")
open class PoolsRepositoryImpl constructor(
    private val extrinsicService: ExtrinsicService,
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val poolDao: PoolDao,
    private val db: AppDatabase,
    private val featureToggleStore: ProductFeatureToggleStore,
    private val mutationAuthorizer: SoraDeFiMutationAuthorizer
) : PoolsRepository {
    override val poolsChainId = soraMainChainId

    override suspend fun mutationCapabilityReason(chainId: ChainId, action: LiquidityMutationAction): String? =
        mutationAuthorizer.capabilityReason(
            feature = SoraMutationFeature.Liquidity,
            chainId = chainId,
            requiredCalls = when (action) {
                LiquidityMutationAction.Add -> setOf(DEPOSIT_LIQUIDITY_CALL)
                LiquidityMutationAction.Remove -> REMOVE_LIQUIDITY_CALLS
            }
        )

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

        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request,
            mapper = pojo<Boolean>().nonNull()
        )
    }

    fun ByteArray.mapAssetId() = this.toList().map { it.toInt().toBigInteger() }
    fun String.mapAssetId() = this.fromHex().mapAssetId()
    fun String.mapCodeToken() = Struct.Instance(
        mapOf("code" to this.mapAssetId())
    )

    fun RuntimeSnapshot.reservesKeyToken(baseTokenId: String): String = this.metadata.module(Modules.POOL_XYK)
        .storage("Reserves")
        .storageKey(
            this,
            baseTokenId.mapCodeToken(),
        )

    suspend fun getStorageHex(chainId: ChainId, storageKey: String): String? =
        chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = pojo<String>(),
        ).result

    suspend fun getStateKeys(chainId: ChainId, partialKey: String): List<String> =
        chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = StateKeys(listOf(partialKey)),
            mapper = pojoList<String>(),
        ).result ?: emptyList()

    class StateKeys(params: List<Any>) : RuntimeRequest("state_getKeys", params)

    fun ByteArray.mapCodeToken() = Struct.Instance(
        mapOf("code" to this.mapAssetId())
    )

    object PoolPropertiesResponse : Schema<PoolPropertiesResponse>() {
        val first by sizedByteArray(32)
        val second by sizedByteArray(32)
    }

    suspend fun getPoolReserveAccount(
        chainId: ChainId,
        baseTokenId: String,
        tokenId: ByteArray
    ): ByteArray? {
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(chainId)
        val storageKey = runtimeOrNull?.metadata
            ?.module(Modules.POOL_XYK)
            ?.storage("Properties")?.storageKey(
                runtimeOrNull,
                baseTokenId.mapCodeToken(),
                tokenId.mapCodeToken(),
            )
            ?: return null

        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(PoolPropertiesResponse),
        )
            .result
            ?.let { storage ->
                storage[storage.schema.first]
            }
    }

    fun String.assetIdFromKey() = this.takeLast(64).addHexPrefix()

    object TotalIssuance : Schema<TotalIssuance>() {
        val value by uint128()
    }

    suspend fun getPoolTotalIssuances(chainId: ChainId, reservesAccountId: ByteArray): BigInteger? {
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(chainId)
        val storageKey = runtimeOrNull?.metadata?.module(Modules.POOL_XYK)
            ?.storage("TotalIssuances")
            ?.storageKey(runtimeOrNull, reservesAccountId)
            ?: return null
        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(TotalIssuance),
        )
            .result
            ?.let { storage ->
                storage[storage.schema.value]
            }
    }

    override suspend fun getBasicPool(
        chainId: ChainId,
        baseTokenId: String,
        targetTokenId: String
    ): BasicPoolData? {
        val poolLocal = poolDao.getBasicPool(baseTokenId, targetTokenId) ?: return null

        val soraChain = chainRegistry.getChain(chainId)
        val soraAssets = soraChain.assets
        val baseAsset = soraAssets.firstOrNull {
            it.currencyId == baseTokenId
        } ?: return null
        val targetAsset = soraAssets.firstOrNull {
            it.currencyId == targetTokenId
        }

        return BasicPoolData(
            baseToken = baseAsset,
            targetToken = targetAsset,
            baseReserves = poolLocal.reserveBase,
            targetReserves = poolLocal.reserveTarget,
            totalIssuance = poolLocal.totalIssuance,
            reserveAccount = poolLocal.reservesAccount
        )
    }

    @Suppress("NestedBlockDepth")
    override suspend fun getBasicPools(chainId: ChainId): List<BasicPoolData> {
        val runtimeOrNull = chainRegistry.getRuntimeOrNull(chainId)
        val storage = runtimeOrNull?.metadata
            ?.module(Modules.POOL_XYK)
            ?.storage("Reserves")

        val list = mutableListOf<BasicPoolData>()

        val soraChain = chainRegistry.getChain(chainId)

        val wallet = accountRepository.getSelectedMetaAccount()

        val accountId = wallet.accountId(soraChain)
        val soraAssets = soraChain.assets

        soraAssets.forEach { asset ->
            val currencyId = asset.currencyId
            val key = currencyId?.let { runtimeOrNull?.reservesKeyToken(it) }
            key?.let {
                getStateKeys(chainId, it).forEach { storageKey ->
                    val targetToken = storageKey.assetIdFromKey()
                    getStorageHex(chainId, storageKey)?.let { storageHex ->
                        storage?.type?.value
                            ?.fromHex(runtimeOrNull, storageHex)
                            ?.safeCast<List<BigInteger>>()?.let { reserves ->

                                val reserveAccount = getPoolReserveAccount(
                                    chainId,
                                    currencyId,
                                    targetToken.fromHex()
                                )
                                val total = reserveAccount?.let {
                                    getPoolTotalIssuances(chainId, it)
                                }?.let {
                                    mapBalance(it, asset.precision)
                                }
                                val targetAsset =
                                    soraAssets.firstOrNull { it.currencyId == targetToken }
                                val reserveAccountAddress =
                                    reserveAccount?.let { soraChain.addressOf(it) } ?: ""

                                val element = BasicPoolData(
                                    baseToken = asset,
                                    targetToken = targetAsset,
                                    baseReserves = mapBalance(reserves[0], asset.precision),
                                    targetReserves = mapBalance(reserves[1], asset.precision),
                                    totalIssuance = total ?: BigDecimal.ZERO,
                                    reserveAccount = reserveAccountAddress
                                )

                                list.add(
                                    element
                                )
                            }
                    }
                }
            }
        }

        return list
    }

    override suspend fun getUserPoolData(
        chainId: ChainId,
        address: String,
        baseTokenId: String,
        targetTokenId: ByteArray
    ): PoolDataDto? {
        return coroutineScope {
            val reservesDeferred =
                async { getPairWithXorReserves(chainId, baseTokenId, targetTokenId) }
            val totalIssuanceAndPropertiesDeferred =
                async {
                    getPoolTotalIssuanceAndProperties(
                        chainId,
                        baseTokenId,
                        targetTokenId,
                        address
                    )
                }
            val chainDeferred = async { chainRegistry.getChain(chainId) }

            val reserves = kotlin.runCatching { reservesDeferred.await() }.getOrNull()
                ?: return@coroutineScope null

            val totalIssuanceAndProperties =
                kotlin.runCatching { totalIssuanceAndPropertiesDeferred.await() }.getOrNull()
                    ?: return@coroutineScope null

            val reservesAccount = chainDeferred.await()
                .addressOf(totalIssuanceAndProperties.third)

            PoolDataDto(
                baseTokenId,
                targetTokenId.toHexString(true),
                reserves.first,
                reserves.second,
                totalIssuanceAndProperties.first,
                totalIssuanceAndProperties.second,
                reservesAccount,
            )
        }
    }

    override suspend fun calcAddLiquidityNetworkFee(
        chainId: ChainId,
        address: String,
        tokenBase: Asset,
        tokenTarget: Asset,
        tokenBaseAmount: BigDecimal,
        tokenTargetAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal? {
        val amountFromMin = PolkaswapFormulas.calculateMinAmount(tokenBaseAmount, slippageTolerance)
        val amountToMin = PolkaswapFormulas.calculateMinAmount(tokenTargetAmount, slippageTolerance)
        val dexId = getPoolBaseTokenDexId(chainId, tokenBase.currencyId)
        val chain = chainRegistry.getChain(chainId)
        val accountId = chain.accountIdOf(address)

        val fee = extrinsicService.estimateFee(chain, accountId) {
            liquidityAdd(
                dexId = dexId,
                baseTokenId = tokenBase.currencyId,
                targetTokenId = tokenTarget.currencyId,
                pairPresented = pairPresented,
                pairEnabled = pairEnabled,
                tokenBaseAmount = tokenBase.planksFromAmount(tokenBaseAmount),
                tokenTargetAmount = tokenTarget.planksFromAmount(tokenTargetAmount),
                amountBaseMin = tokenBase.planksFromAmount(amountFromMin),
                amountTargetMin = tokenTarget.planksFromAmount(amountToMin),
            )
        }

        val feeToken = chain.utilityAsset
        return feeToken?.amountFromPlanks(fee)
    }

    override suspend fun calcRemoveLiquidityNetworkFee(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
    ): BigDecimal? {
        val chain = chainRegistry.getChain(chainId)
        val baseTokenId = tokenBase.currencyId ?: return null
        val targetTokenId = tokenTarget.currencyId ?: return null
        val accountId = accountRepository.getSelectedMetaAccount().substrateAccountId ?: return null

        val fee = extrinsicService.estimateFee(chain, accountId) {
            removeLiquidity(
                dexId = getPoolBaseTokenDexId(chainId, baseTokenId),
                outputAssetIdA = baseTokenId,
                outputAssetIdB = targetTokenId,
                markerAssetDesired = tokenBase.planksFromAmount(BigDecimal.ONE),
                outputAMin = tokenBase.planksFromAmount(BigDecimal.ONE),
                outputBMin = tokenTarget.planksFromAmount(BigDecimal.ONE),
            )
        }
        val feeToken = chain.utilityAsset
        return feeToken?.amountFromPlanks(fee)
    }

    override suspend fun getPoolBaseTokenDexId(chainId: ChainId, tokenId: String?): Int {
        return getPoolBaseTokens(chainId).first {
            it.second == tokenId
        }.first
    }

    private suspend fun getPoolBaseTokens(chainId: ChainId): List<Pair<Int, String>> {
        val runtimeSnapshot = chainRegistry.awaitRuntimeProvider(chainId).get()
        val metadataStorage = runtimeSnapshot.metadata
            .module("DEXManager")
            .storage("DEXInfos")

        val partialKey = metadataStorage.storageKey()
        val connection = chainRegistry.awaitConnection(chainId)

        val storageKeys = connection.socketService.executeAsync(
            request = StateKeys(listOf(partialKey)),
            mapper = pojoList<String>().nonNull()
        )
        return supervisorScope {
            val poolBaseTokensDeferred = storageKeys.map { storageKey ->
                async {
                    val storage = connection.socketService.executeAsync(
                        request = GetStorageRequest(listOf(storageKey)),
                        mapper = pojo<String>().nonNull()
                    )
                    val storageType = metadataStorage.type.value!!
                    val storageRawData = storageType.fromHex(runtimeSnapshot, storage)

                    (storageRawData as? Struct.Instance)?.let { instance ->
                        instance.mapToToken("baseAssetId")?.let { token ->
                            storageKey.takeInt32() to token
                        }
                    }
                }
            }
            poolBaseTokensDeferred.awaitAll().filterNotNull()
        }
    }

    fun Struct.Instance.mapToToken(field: String) = this.get<Struct.Instance>(field)?.getTokenId()?.toHexString(true)

    fun String.takeInt32() = uint32.fromHex(this.takeLast(8)).toInt()

    private suspend fun getPoolTotalIssuanceAndProperties(
        chainId: ChainId,
        baseTokenId: String,
        tokenId: ByteArray,
        address: String
    ): Triple<BigInteger, BigInteger, ByteArray>? {
        return getPoolReserveAccount(chainId, baseTokenId, tokenId)?.let { account ->
            getPoolTotalIssuances(
                chainId,
                account
            )?.let {
                val provider = getPoolProviders(
                    chainId,
                    account,
                    address
                )
                Triple(it, provider, account)
            }
        }
    }

    object PoolProviders : Schema<PoolProviders>() {
        val value by uint128()
    }

    private suspend fun getPoolProviders(
        chainId: ChainId,
        reservesAccountId: ByteArray,
        currentAddress: String
    ): BigInteger {
        val storageKey =
            chainRegistry.getRuntimeOrNull(chainId)?.let {
                it.metadata.module(Modules.POOL_XYK)
                    .storage("PoolProviders").storageKey(
                        it,
                        reservesAccountId,
                        currentAddress.toAccountId()
                    )
            } ?: return BigInteger.ZERO
        return runCatching {
            chainRegistry.awaitConnection(chainId).socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = scale(PoolProviders),
            )
                .let { storage ->
                    storage.result?.let {
                        it[it.schema.value]
                    } ?: BigInteger.ZERO
                }
        }.getOrElse {
            it.printStackTrace()
            throw it
        }
    }

    object ReservesResponse : Schema<ReservesResponse>() {
        val first by uint128()
        val second by uint128()
    }

    private suspend fun getPairWithXorReserves(
        chainId: ChainId,
        baseTokenId: String,
        tokenId: ByteArray
    ): Pair<BigInteger, BigInteger>? {
        val storageKey =
            chainRegistry.getRuntimeOrNull(chainId)?.reservesKey(baseTokenId, tokenId)
                ?: return null
        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = scale(ReservesResponse),
        )
            .result
            ?.let { storage ->
                storage[storage.schema.first] to storage[storage.schema.second]
            }
    }

    fun RuntimeSnapshot.reservesKey(baseTokenId: String, tokenId: ByteArray): String =
        this.metadata.module(Modules.POOL_XYK)
            .storage("Reserves")
            .storageKey(
                this,
                baseTokenId.mapCodeToken(),
                tokenId.mapCodeToken(),
            )

    @Suppress("UNCHECKED_CAST", "ThrowsCount")
    fun SubscriptionChange.storageChange(): SubscribeStorageResult {
        val result = params.result as? Map<*, *>
            ?: throw IllegalArgumentException("${params.result} is not a valid storage result")

        val block = result["block"] as? String
            ?: throw IllegalArgumentException("$result is not a valid storage result")
        val changes = result["changes"] as? List<List<String>>
            ?: throw IllegalArgumentException("$result is not a valid storage result")

        return SubscribeStorageResult(block, changes)
    }

    // changes are in format [[storage key, value], [..], ..]
    class SubscribeStorageResult(val block: String, val changes: List<List<String?>>) {
        fun getSingleChange() = changes.first()[1]
    }

    fun Struct.Instance.getTokenId() = get<List<*>>("code")
        ?.map { (it as BigInteger).toByte() }
        ?.toByteArray()

    private suspend fun getUserPoolsTokenIdsKeys(chainId: ChainId, address: String): List<String> {
        val accountPoolsKey = chainRegistry.getRuntimeOrNull(chainId)?.accountPoolsKey(address)
        return chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = StateKeys(listOfNotNull(accountPoolsKey)),
            mapper = pojoList<String>().nonNull()
        )
    }

    private suspend fun getUserPoolsTokenIds(chainId: ChainId, address: String): List<Pair<String, List<ByteArray>>> {
        val storageKeys = getUserPoolsTokenIdsKeys(chainId, address)
        return storageKeys.map { storageKey ->
            chainRegistry.awaitConnection(chainId).socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey)),
                mapper = pojo<String>().nonNull(),
            )
                .let { storage ->
                    val storageType =
                        chainRegistry.getRuntimeOrNull(chainId)?.metadata?.module(Modules.POOL_XYK)
                            ?.storage("AccountPools")?.type?.value!!
                    val storageRawData =
                        storageType.fromHex(chainRegistry.getRuntimeOrNull(chainId)!!, storage)
                    val tokens: List<ByteArray> = if (storageRawData is List<*>) {
                        storageRawData.filterIsInstance<Struct.Instance>()
                            .mapNotNull { struct ->
                                struct.getTokenId()
                            }
                    } else {
                        emptyList()
                    }
                    storageKey.assetIdFromKey() to tokens
                }
        }
    }

    override suspend fun observeRemoveLiquidity(
        chainId: ChainId,
        tokenBase: Asset,
        tokenTarget: Asset,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal
    ): Result<String>? = runCatching {
        val request = RemoveLiquidityRequest(
            chainId = chainId,
            tokenBase = CanonicalSoraAsset(tokenBase),
            tokenTarget = CanonicalSoraAsset(tokenTarget),
            markerAssetDesired = markerAssetDesired,
            firstAmountMin = firstAmountMin,
            secondAmountMin = secondAmountMin
        )
        val preliminary = validateRemoveLiquidity(request, BigInteger.ZERO)
        val feeInPlanks = extrinsicService.estimateFee(
            chain = preliminary.context.chain,
            accountId = preliminary.context.accountId
        ) {
            removeLiquidity(
                dexId = preliminary.dexId,
                outputAssetIdA = requireNotNull(preliminary.tokenBase.currencyId),
                outputAssetIdB = requireNotNull(preliminary.tokenTarget.currencyId),
                markerAssetDesired = preliminary.markerAssetDesired,
                outputAMin = preliminary.firstAmountMin,
                outputBMin = preliminary.secondAmountMin
            )
        }

        val finalContext = validateRemoveLiquidity(
            request = request,
            feeInPlanks = feeInPlanks,
            expectedIdentity = preliminary.context.identity
        )
        check(featureToggleStore.polkaswapMutationsEnabled) {
            "Polkaswap liquidity actions are temporarily disabled"
        }
        extrinsicService.submitExtrinsic(
            chain = finalContext.context.chain,
            accountId = finalContext.context.accountId
        ) {
            removeLiquidity(
                dexId = finalContext.dexId,
                outputAssetIdA = requireNotNull(finalContext.tokenBase.currencyId),
                outputAssetIdB = requireNotNull(finalContext.tokenTarget.currencyId),
                markerAssetDesired = finalContext.markerAssetDesired,
                outputAMin = finalContext.firstAmountMin,
                outputBMin = finalContext.secondAmountMin
            )
        }.getOrThrow()
    }

    override suspend fun observeAddLiquidity(
        chainId: ChainId,
        address: String,
        tokenBase: Asset,
        tokenTarget: Asset,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): Result<String>? = runCatching {
        val request = AddLiquidityRequest(
            chainId = chainId,
            address = address,
            tokenBase = CanonicalSoraAsset(tokenBase),
            tokenTarget = CanonicalSoraAsset(tokenTarget),
            amountBase = amountBase,
            amountTarget = amountTarget,
            pairEnabled = pairEnabled,
            pairPresented = pairPresented,
            slippageTolerance = slippageTolerance
        )
        val preliminary = validateAddLiquidity(request, BigInteger.ZERO)
        val feeInPlanks = extrinsicService.estimateFee(
            chain = preliminary.context.chain,
            accountId = preliminary.context.accountId,
            useBatchAll = !pairPresented
        ) {
            liquidityAdd(
                dexId = preliminary.dexId,
                baseTokenId = preliminary.tokenBase.currencyId,
                targetTokenId = preliminary.tokenTarget.currencyId,
                pairPresented = pairPresented,
                pairEnabled = pairEnabled,
                tokenBaseAmount = preliminary.amountBase,
                tokenTargetAmount = preliminary.amountTarget,
                amountBaseMin = preliminary.amountBaseMin,
                amountTargetMin = preliminary.amountTargetMin
            )
        }

        val finalContext = validateAddLiquidity(
            request = request,
            feeInPlanks = feeInPlanks,
            expectedIdentity = preliminary.context.identity
        )
        check(featureToggleStore.polkaswapMutationsEnabled) {
            "Polkaswap liquidity actions are temporarily disabled"
        }
        extrinsicService.submitExtrinsic(
            chain = finalContext.context.chain,
            accountId = finalContext.context.accountId,
            useBatchAll = !pairPresented
        ) {
            liquidityAdd(
                dexId = finalContext.dexId,
                baseTokenId = finalContext.tokenBase.currencyId,
                targetTokenId = finalContext.tokenTarget.currencyId,
                pairPresented = pairPresented,
                pairEnabled = pairEnabled,
                tokenBaseAmount = finalContext.amountBase,
                tokenTargetAmount = finalContext.amountTarget,
                amountBaseMin = finalContext.amountBaseMin,
                amountTargetMin = finalContext.amountTargetMin
            )
        }.getOrThrow()
    }

    private suspend fun validateAddLiquidity(
        request: AddLiquidityRequest,
        feeInPlanks: BigInteger,
        expectedIdentity: SoraMutationIdentity? = null
    ): ValidatedAddLiquidity {
        check(request.slippageTolerance.isFinite() && request.slippageTolerance >= 0.0 && request.slippageTolerance < 100.0) {
            "Liquidity slippage must be at least zero and below 100 percent"
        }
        val context = mutationAuthorizer.authorize(
            feature = SoraMutationFeature.Liquidity,
            chainId = request.chainId,
            requestedAssets = listOf(request.tokenBase, request.tokenTarget),
            requiredCalls = addLiquidityCalls(request.pairEnabled, request.pairPresented),
            expectedIdentity = expectedIdentity
        )
        check(request.address == context.accountAddress) {
            "Liquidity account does not match the selected SORA account"
        }
        val base = context.assets[0]
        val target = context.assets[1]
        check(base.currencyId != target.currencyId) { "Liquidity assets must be different" }
        val amountBase = base.exactPositivePlanks(request.amountBase, "Base liquidity amount")
        val amountTarget = target.exactPositivePlanks(request.amountTarget, "Target liquidity amount")
        val minimumBaseAmount = PolkaswapFormulas.calculateMinAmount(
            request.amountBase,
            request.slippageTolerance
        )
        val minimumTargetAmount = PolkaswapFormulas.calculateMinAmount(
            request.amountTarget,
            request.slippageTolerance
        )
        check(minimumBaseAmount <= request.amountBase && minimumTargetAmount <= request.amountTarget) {
            "Liquidity minimum cannot exceed the desired amount"
        }
        val amountBaseMin = base.exactPositivePlanks(
            minimumBaseAmount.setScale(base.precision, RoundingMode.DOWN),
            "Minimum base output"
        )
        val amountTargetMin = target.exactPositivePlanks(
            minimumTargetAmount.setScale(target.precision, RoundingMode.DOWN),
            "Minimum target output"
        )
        mutationAuthorizer.requireFreshBalances(
            context = context,
            spends = listOf(
                SoraAssetSpend(base, amountBase, "Base asset balance is insufficient for this liquidity deposit"),
                SoraAssetSpend(target, amountTarget, "Target asset balance is insufficient for this liquidity deposit")
            ),
            feeInPlanks = feeInPlanks
        )
        val dexId = getPoolBaseTokenDexId(request.chainId, base.currencyId)
        val submissionContext = reauthorizeLiquidityContext(
            context = context,
            chainId = request.chainId,
            requestedAssets = listOf(request.tokenBase, request.tokenTarget),
            requiredCalls = addLiquidityCalls(request.pairEnabled, request.pairPresented),
            expectedIdentity = expectedIdentity
        )
        check(request.address == submissionContext.accountAddress) {
            "Liquidity account does not match the final selected SORA account"
        }
        return ValidatedAddLiquidity(
            context = submissionContext,
            tokenBase = submissionContext.assets[0],
            tokenTarget = submissionContext.assets[1],
            dexId = dexId,
            amountBase = amountBase,
            amountTarget = amountTarget,
            amountBaseMin = amountBaseMin,
            amountTargetMin = amountTargetMin
        )
    }

    private suspend fun validateRemoveLiquidity(
        request: RemoveLiquidityRequest,
        feeInPlanks: BigInteger,
        expectedIdentity: SoraMutationIdentity? = null
    ): ValidatedRemoveLiquidity {
        val context = mutationAuthorizer.authorize(
            feature = SoraMutationFeature.Liquidity,
            chainId = request.chainId,
            requestedAssets = listOf(request.tokenBase, request.tokenTarget),
            requiredCalls = REMOVE_LIQUIDITY_CALLS,
            expectedIdentity = expectedIdentity
        )
        val base = context.assets[0]
        val target = context.assets[1]
        check(base.currencyId != target.currencyId) { "Liquidity assets must be different" }
        val markerAssetDesired = base.exactPositivePlanks(
            request.markerAssetDesired,
            "Liquidity position amount"
        )
        val firstAmountMin = base.exactPositivePlanks(request.firstAmountMin, "Minimum base output")
        val secondAmountMin = target.exactPositivePlanks(request.secondAmountMin, "Minimum target output")
        val baseCurrencyId = requireNotNull(base.currencyId)
        val targetCurrencyId = requireNotNull(target.currencyId)
        val freshPool = getUserPoolData(
            chainId = request.chainId,
            address = context.accountAddress,
            baseTokenId = baseCurrencyId,
            targetTokenId = targetCurrencyId.fromHex()
        ) ?: error("Fresh SORA liquidity position is unavailable")
        check(freshPool.baseAssetId == baseCurrencyId && freshPool.assetId == targetCurrencyId) {
            "Fresh liquidity position does not match the exact requested pool"
        }
        check(markerAssetDesired <= freshPool.poolProvidersBalance) {
            "Liquidity position balance is insufficient for this withdrawal"
        }
        check(firstAmountMin <= freshPool.reservesFirst && secondAmountMin <= freshPool.reservesSecond) {
            "Minimum liquidity output exceeds the fresh pool reserves"
        }
        mutationAuthorizer.requireFreshBalances(context, emptyList(), feeInPlanks)
        val dexId = getPoolBaseTokenDexId(request.chainId, baseCurrencyId)
        val submissionContext = reauthorizeLiquidityContext(
            context = context,
            chainId = request.chainId,
            requestedAssets = listOf(request.tokenBase, request.tokenTarget),
            requiredCalls = REMOVE_LIQUIDITY_CALLS,
            expectedIdentity = expectedIdentity
        )
        return ValidatedRemoveLiquidity(
            context = submissionContext,
            tokenBase = submissionContext.assets[0],
            tokenTarget = submissionContext.assets[1],
            dexId = dexId,
            markerAssetDesired = markerAssetDesired,
            firstAmountMin = firstAmountMin,
            secondAmountMin = secondAmountMin
        )
    }

    private suspend fun reauthorizeLiquidityContext(
        context: SoraMutationContext,
        chainId: ChainId,
        requestedAssets: List<CanonicalSoraAsset>,
        requiredCalls: Set<SoraRuntimeCall>,
        expectedIdentity: SoraMutationIdentity?
    ): SoraMutationContext {
        if (expectedIdentity == null) return context

        return mutationAuthorizer.authorize(
            feature = SoraMutationFeature.Liquidity,
            chainId = chainId,
            requestedAssets = requestedAssets,
            requiredCalls = requiredCalls,
            expectedIdentity = context.identity
        )
    }

    override suspend fun updateAccountPools(chainId: ChainId, address: String) = supervisorScope {
        val assets = chainRegistry.getChain(chainId).assets
        val tokenIds = getUserPoolsTokenIds(chainId, address)
        val poolsDeferred = tokenIds.map { (baseTokenId, tokensId) ->
            async {
                val baseToken = assets.firstOrNull {
                    it.currencyId == baseTokenId
                } ?: return@async emptyList()

                val xorPrecision = baseToken.precision

                val poolData = tokensId.map { tokenId ->
                    async { getUserPoolData(chainId, address, baseTokenId, tokenId) }
                }

                poolData.awaitAll().filterNotNull().mapNotNull pool@{ poolDataDto ->
                    val token = assets.firstOrNull {
                        it.currencyId == poolDataDto.assetId
                    } ?: return@pool null
                    val tokenPrecision = token.precision

                    val basicPoolLocal = BasicPoolLocal(
                        tokenIdBase = baseTokenId,
                        tokenIdTarget = poolDataDto.assetId,
                        reserveBase = mapBalance(
                            poolDataDto.reservesFirst,
                            xorPrecision
                        ),
                        reserveTarget = mapBalance(
                            poolDataDto.reservesSecond,
                            tokenPrecision
                        ),
                        totalIssuance = mapBalance(
                            poolDataDto.totalIssuance,
                            xorPrecision
                        ),
                        reservesAccount = poolDataDto.reservesAccount,
                    )

                    val userPoolLocal = UserPoolLocal(
                        accountAddress = address,
                        userTokenIdBase = baseTokenId,
                        userTokenIdTarget = poolDataDto.assetId,
                        poolProvidersBalance = mapBalance(
                            poolDataDto.poolProvidersBalance,
                            xorPrecision
                        )
                    )

                    return@pool UserPoolJoinedLocal(
                        userPoolLocal = userPoolLocal,
                        basicPoolLocal = basicPoolLocal,
                    )
                }
            }
        }

        val pools = poolsDeferred.awaitAll().flatten()

        db.withTransaction {
            poolDao.clearTable(address)
            poolDao.insertBasicPools(
                pools.map {
                    it.basicPoolLocal
                }
            )
            poolDao.insertUserPools(
                pools.map {
                    it.userPoolLocal
                }
            )
        }
    }

    override suspend fun updateBasicPools(chainId: ChainId) = coroutineScope {
        val runtimeOrNull = chainRegistry.awaitRuntimeProvider(chainId).get()
        val storage = runtimeOrNull.metadata
            .module(Modules.POOL_XYK)
            .storage("Reserves")

        val soraChain = chainRegistry.getChain(chainId)
        val assets = soraChain.assets

        val basicPoolsLocalDeferred = getPoolBaseTokens(chainId).map { (dexId, tokenId) ->
            async {
                val asset = assets.firstOrNull { it.currencyId == tokenId } ?: return@async null
                val key = runtimeOrNull.reservesKeyToken(tokenId)

                val basicPoolDeferred = getStateKeys(chainId, key).map { storageKey ->
                    async basicPoolOperation@{
                        val targetToken = storageKey.assetIdFromKey()
                        val storageHex =
                            getStorageHex(chainId, storageKey) ?: return@basicPoolOperation null
                        val reserves = storage.type.value
                            ?.fromHex(runtimeOrNull, storageHex)
                            ?.safeCast<List<BigInteger>>() ?: return@basicPoolOperation null

                        val reserveAccount = getPoolReserveAccount(
                            chainId,
                            tokenId,
                            targetToken.fromHex()
                        )

                        val total = reserveAccount?.let {
                            getPoolTotalIssuances(chainId, it)
                        }?.let {
                            mapBalance(it, asset.precision)
                        }

                        val reserveAccountAddress =
                            reserveAccount?.let { soraChain.addressOf(it) } ?: ""

                        BasicPoolLocal(
                            tokenIdBase = tokenId,
                            tokenIdTarget = targetToken,
                            reserveBase = mapBalance(reserves[0], asset.precision),
                            reserveTarget = mapBalance(
                                reserves[1],
                                asset.precision
                            ),
                            totalIssuance = total ?: BigDecimal.ZERO,
                            reservesAccount = reserveAccountAddress,
                        )
                    }
                }
                basicPoolDeferred.awaitAll().filterNotNull()
            }
        }
        val list = basicPoolsLocalDeferred.awaitAll().filterNotNull().flatten()

        val minus = poolDao.getBasicPools().filter { db ->
            list.find { it.tokenIdBase == db.tokenIdBase && it.tokenIdTarget == db.tokenIdTarget } == null
        }
        poolDao.deleteBasicPools(minus)
        poolDao.insertBasicPools(list)
    }

    private suspend fun getAssets(): List<Asset> {
        return chainRegistry.getChain(poolsChainId).assets
    }

    override fun subscribePool(
        address: String,
        baseTokenId: String,
        targetTokenId: String
    ): Flow<CommonPoolData> {
        return poolDao.subscribePool(address, baseTokenId, targetTokenId).map { pool ->
            val assets = getAssets()
            mapPoolLocalToData(pool, assets)
        }
            .mapNotNull { it }
    }

    override fun subscribePools(address: String): Flow<List<CommonPoolData>> {
        return poolDao.subscribeAllPools(address).map { pools ->
            val assets = getAssets()
            pools.mapNotNull { poolLocal ->
                mapPoolLocalToData(poolLocal, assets)
            }
        }
    }

    fun RuntimeSnapshot.accountPoolsKey(address: String): String = this.metadata.module(Modules.POOL_XYK)
        .storage("AccountPools")
        .storageKey(this, address.toAccountId())

    private fun mapPoolLocalToData(poolLocal: UserPoolJoinedLocalNullable, assets: List<Asset>): CommonPoolData? {
        val baseToken = assets.firstOrNull {
            it.currencyId == poolLocal.basicPoolLocal.tokenIdBase
        } ?: return null
        val token = assets.firstOrNull {
            it.currencyId == poolLocal.basicPoolLocal.tokenIdTarget
        } ?: return null

        val basicPoolData = BasicPoolData(
            baseToken = baseToken,
            targetToken = token,
            baseReserves = poolLocal.basicPoolLocal.reserveBase,
            targetReserves = poolLocal.basicPoolLocal.reserveTarget,
            totalIssuance = poolLocal.basicPoolLocal.totalIssuance,
            reserveAccount = poolLocal.basicPoolLocal.reservesAccount
        )

        val userPoolData = poolLocal.userPoolLocal?.let { userPoolLocal ->
            val basePooled = PolkaswapFormulas.calculatePooledValue(
                poolLocal.basicPoolLocal.reserveBase,
                userPoolLocal.poolProvidersBalance,
                poolLocal.basicPoolLocal.totalIssuance,
                baseToken.precision,
            )
            val secondPooled = PolkaswapFormulas.calculatePooledValue(
                poolLocal.basicPoolLocal.reserveTarget,
                userPoolLocal.poolProvidersBalance,
                poolLocal.basicPoolLocal.totalIssuance,
                token.precision,
            )
            val share = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                userPoolLocal.poolProvidersBalance,
                poolLocal.basicPoolLocal.totalIssuance,
            )
            UserPoolData(
                basePooled = basePooled,
                targetPooled = secondPooled,
                poolShare = share,
                poolProvidersBalance = userPoolLocal.poolProvidersBalance,
            )
        }
        return CommonPoolData(
            basic = basicPoolData,
            user = userPoolData,
        )
    }
}
