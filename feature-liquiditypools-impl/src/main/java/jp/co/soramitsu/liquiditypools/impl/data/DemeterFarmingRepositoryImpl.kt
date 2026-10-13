package jp.co.soramitsu.liquiditypools.impl.data

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.androidfoundation.format.addHexPrefix
import jp.co.soramitsu.androidfoundation.format.isZero
import jp.co.soramitsu.androidfoundation.format.mapBalance
import jp.co.soramitsu.androidfoundation.format.orZero
import jp.co.soramitsu.androidfoundation.format.safeCast
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.rpc.retrieveAllValues
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.liquiditypools.data.DemeterFarmingRepository
import jp.co.soramitsu.liquiditypools.data.PoolsRepository
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import jp.co.soramitsu.liquiditypools.impl.data.network.demeterClaimRewards
import jp.co.soramitsu.liquiditypools.impl.data.network.demeterDeposit
import jp.co.soramitsu.liquiditypools.impl.data.network.demeterWithdraw
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.core.models.Asset as CoreAsset

private const val DEMETER_FARMING_PALLET = "DemeterFarmingPlatform"
private val DEMETER_DEPOSIT_CALL = SoraRuntimeCall(DEMETER_FARMING_PALLET, "deposit")
private val DEMETER_WITHDRAW_CALL = SoraRuntimeCall(DEMETER_FARMING_PALLET, "withdraw")
private val DEMETER_CLAIM_CALL = SoraRuntimeCall(DEMETER_FARMING_PALLET, "get_rewards")

private data class DemeterMutationRequest(
    val chainId: ChainId,
    val base: CanonicalSoraAsset,
    val target: CanonicalSoraAsset,
    val reward: CanonicalSoraAsset,
    val amount: BigDecimal?
)

private data class ValidatedDemeterMutation(
    val context: SoraMutationContext,
    val base: CoreAsset,
    val target: CoreAsset,
    val reward: CoreAsset,
    val amountInPlanks: BigInteger? = null
)

// The existing repository owns both legacy read paths and final mutation revalidation.
@Suppress("LargeClass")
open class DemeterFarmingRepositoryImpl(
    private val chainRegistry: ChainRegistry,
    private val bulkRetriever: BulkRetriever,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val poolsRepository: PoolsRepository,
    private val extrinsicService: ExtrinsicService,
    private val featureToggleStore: ProductFeatureToggleStore,
    private val mutationAuthorizer: SoraDeFiMutationAuthorizer,
) : DemeterFarmingRepository {

    private val cachedFarmedPools = ConcurrentHashMap<String, List<DemeterFarmingPool>>()
    private val cachedFarmedBasicPools = ConcurrentHashMap<String, List<DemeterFarmingBasicPool>>()

    override suspend fun mutationCapabilityReason(chainId: String, action: DemeterMutationAction): String? =
        mutationAuthorizer.capabilityReason(
            feature = SoraMutationFeature.Demeter,
            chainId = chainId,
            requiredCalls = setOf(
                when (action) {
                    DemeterMutationAction.Deposit -> DEMETER_DEPOSIT_CALL
                    DemeterMutationAction.Withdraw -> DEMETER_WITHDRAW_CALL
                    DemeterMutationAction.Claim -> DEMETER_CLAIM_CALL
                }
            )
        )

    override suspend fun getFarmedPools(chainId: String): List<DemeterFarmingPool>? {
        val soraAccountAddress = accountRepository.getSelectedAccount(chainId).address

        if (cachedFarmedPools.containsKey(soraAccountAddress)) return cachedFarmedPools[soraAccountAddress]
        cachedFarmedPools.remove(soraAccountAddress)

        val baseFarms = getFarmedBasicPools(chainId)
        val soraAssets = getSoraAssets(chainId)

        val calculated = getDemeter(chainId, soraAccountAddress)
            ?.filter { it.farm && it.amount.isZero().not() }
            ?.mapNotNull { demeter ->
                val base = baseFarms.firstOrNull { base ->
                    base.tokenBase.token.configuration.currencyId == demeter.base &&
                            base.tokenTarget.token.configuration.currencyId == demeter.pool &&
                            base.tokenReward.token.configuration.currencyId == demeter.reward
                } ?: return@mapNotNull null

                val baseTokenMapped = soraAssets.firstOrNull {
                    it.token.configuration.currencyId == demeter.base
                } ?: return@mapNotNull null
                val poolTokenMapped = soraAssets.firstOrNull {
                    it.token.configuration.currencyId == demeter.pool
                } ?: return@mapNotNull null
                val rewardTokenMapped = soraAssets.firstOrNull {
                    it.token.configuration.currencyId == demeter.reward
                } ?: return@mapNotNull null

                DemeterFarmingPool(
                    tokenBase = baseTokenMapped,
                    tokenTarget = poolTokenMapped,
                    tokenReward = rewardTokenMapped,
                    apr = base.apr,
                    amount = mapBalance(demeter.amount, poolTokenMapped.token.configuration.precision),
                    amountReward = mapBalance(demeter.rewardAmount, rewardTokenMapped.token.configuration.precision),
                )
            } ?: return null
        return cachedFarmedPools.getOrPut(soraAccountAddress) { calculated }
    }

    override suspend fun getAvailablePools(chainId: ChainId): List<DemeterFarmingBasicPool> {
        if (cachedFarmedBasicPools[chainId] == null) {
            val rewardTokens = getRewardTokens(chainId)

            val soraAssets = getSoraAssets(chainId)

            cachedFarmedBasicPools[chainId] = getAllFarms(chainId)
                .mapNotNull { basic ->
                    runCatching {
                        val baseTokenMapped = soraAssets.firstOrNull {
                            it.token.configuration.currencyId == basic.base
                        } ?: return@mapNotNull null
                        val poolTokenMapped = soraAssets.firstOrNull {
                            it.token.configuration.currencyId == basic.pool
                        } ?: return@mapNotNull null
                        val rewardTokenMapped = soraAssets.firstOrNull {
                            it.token.configuration.currencyId == basic.reward
                        } ?: return@mapNotNull null
                        val rewardToken = rewardTokens.find { it.token == basic.reward }
                        val emission = getEmission(basic, rewardToken, rewardTokenMapped.token.configuration.precision)
                        val total = mapBalance(basic.totalTokensInPool, poolTokenMapped.token.configuration.precision)
                        val poolTokenPrice = poolTokenMapped.token.fiatRate.orZero()
                        val rewardTokenPrice = rewardTokenMapped.token.fiatRate.orZero()
                        val tvl = if (basic.isFarm) {
                            poolsRepository.getBasicPool(chainId, basic.base, basic.pool)?.let { pool ->
                                val kf = pool.targetReserves.div(pool.totalIssuance)
                                kf.times(total).times(2.toBigDecimal()).times(poolTokenPrice)
                            } ?: BigDecimal.ZERO
                        } else {
                            total.times(poolTokenPrice)
                        }

                        val apr = if (tvl.isZero()) {
                            BigDecimal.ZERO
                        } else {
                            emission
                                .times(BLOCKS_PER_YEAR.toBigDecimal())
                                .times(rewardTokenPrice)
                                .div(tvl).times(100.toBigDecimal())
                        }

                        DemeterFarmingBasicPool(
                            tokenBase = baseTokenMapped,
                            tokenTarget = poolTokenMapped,
                            tokenReward = rewardTokenMapped,
                            apr = apr.toDouble(),
                            tvl = tvl,
                            fee = mapBalance(basic.depositFee, baseTokenMapped.token.configuration.precision).toDouble()
                                .times(100.0),
                        )
                    }.getOrNull()
                }
        }

        return cachedFarmedBasicPools[chainId].orEmpty()
    }

    private suspend fun getFarmedBasicPools(chainId: ChainId): List<DemeterFarmingBasicPool> =
        getAvailablePools(chainId)

    override suspend fun refresh(chainId: String) {
        cachedFarmedBasicPools.remove(chainId)
        cachedFarmedPools.clear()
    }

    override suspend fun deposit(
        chainId: String,
        pool: DemeterFarmingBasicPool,
        amount: BigDecimal
    ): Result<String> = runCatching {
        val request = DemeterMutationRequest(
            chainId = chainId,
            base = CanonicalSoraAsset(pool.tokenBase.token.configuration),
            target = CanonicalSoraAsset(pool.tokenTarget.token.configuration),
            reward = CanonicalSoraAsset(pool.tokenReward.token.configuration),
            amount = amount
        )
        val preliminary = validateDeposit(request, BigInteger.ZERO)
        val feeInPlanks = extrinsicService.estimateFee(
            preliminary.context.chain,
            preliminary.context.accountId
        ) {
            demeterDeposit(
                requireNotNull(preliminary.base.currencyId),
                requireNotNull(preliminary.target.currencyId),
                requireNotNull(preliminary.reward.currencyId),
                requireNotNull(preliminary.amountInPlanks)
            )
        }
        val finalContext = validateDeposit(request, feeInPlanks, preliminary.context.identity)
        check(featureToggleStore.demeterMutationsEnabled) {
            "Demeter actions are temporarily unavailable"
        }
        extrinsicService.submitExtrinsic(finalContext.context.chain, finalContext.context.accountId) {
            demeterDeposit(
                requireNotNull(finalContext.base.currencyId),
                requireNotNull(finalContext.target.currencyId),
                requireNotNull(finalContext.reward.currencyId),
                requireNotNull(finalContext.amountInPlanks)
            )
        }.getOrThrow()
    }.onSuccess {
        refresh(chainId)
    }

    override suspend fun withdraw(
        chainId: String,
        pool: DemeterFarmingPool,
        amount: BigDecimal
    ): Result<String> = runCatching {
        val request = DemeterMutationRequest(
            chainId = chainId,
            base = CanonicalSoraAsset(pool.tokenBase.token.configuration),
            target = CanonicalSoraAsset(pool.tokenTarget.token.configuration),
            reward = CanonicalSoraAsset(pool.tokenReward.token.configuration),
            amount = amount
        )
        val preliminary = validateWithdraw(request, BigInteger.ZERO)
        val feeInPlanks = extrinsicService.estimateFee(
            preliminary.context.chain,
            preliminary.context.accountId
        ) {
            demeterWithdraw(
                requireNotNull(preliminary.base.currencyId),
                requireNotNull(preliminary.target.currencyId),
                requireNotNull(preliminary.reward.currencyId),
                requireNotNull(preliminary.amountInPlanks)
            )
        }
        val finalContext = validateWithdraw(request, feeInPlanks, preliminary.context.identity)
        check(featureToggleStore.demeterMutationsEnabled) {
            "Demeter actions are temporarily unavailable"
        }
        extrinsicService.submitExtrinsic(finalContext.context.chain, finalContext.context.accountId) {
            demeterWithdraw(
                requireNotNull(finalContext.base.currencyId),
                requireNotNull(finalContext.target.currencyId),
                requireNotNull(finalContext.reward.currencyId),
                requireNotNull(finalContext.amountInPlanks)
            )
        }.getOrThrow()
    }.onSuccess {
        refresh(chainId)
    }

    override suspend fun claimRewards(chainId: String, pool: DemeterFarmingPool): Result<String> = runCatching {
        val request = DemeterMutationRequest(
            chainId = chainId,
            base = CanonicalSoraAsset(pool.tokenBase.token.configuration),
            target = CanonicalSoraAsset(pool.tokenTarget.token.configuration),
            reward = CanonicalSoraAsset(pool.tokenReward.token.configuration),
            amount = null
        )
        val preliminary = validateClaim(request, BigInteger.ZERO)
        val feeInPlanks = extrinsicService.estimateFee(
            preliminary.context.chain,
            preliminary.context.accountId
        ) {
            demeterClaimRewards(
                requireNotNull(preliminary.base.currencyId),
                requireNotNull(preliminary.target.currencyId),
                requireNotNull(preliminary.reward.currencyId)
            )
        }
        val finalContext = validateClaim(request, feeInPlanks, preliminary.context.identity)
        check(featureToggleStore.demeterMutationsEnabled) {
            "Demeter actions are temporarily unavailable"
        }
        extrinsicService.submitExtrinsic(finalContext.context.chain, finalContext.context.accountId) {
            demeterClaimRewards(
                requireNotNull(finalContext.base.currencyId),
                requireNotNull(finalContext.target.currencyId),
                requireNotNull(finalContext.reward.currencyId)
            )
        }.getOrThrow()
    }.onSuccess {
        refresh(chainId)
    }

    private suspend fun validateDeposit(
        request: DemeterMutationRequest,
        feeInPlanks: BigInteger,
        expectedIdentity: SoraMutationIdentity? = null
    ): ValidatedDemeterMutation {
        val validated = validateDemeterContext(
            request,
            DEMETER_DEPOSIT_CALL,
            expectedIdentity
        )
        val amount = requireNotNull(request.amount)
        val amountInPlanks = validated.target.exactPositivePlanks(amount, "Demeter deposit amount")
        val freshFarm = getAllFarms(request.chainId).singleOrNull {
            it.base == validated.base.currencyId &&
                it.pool == validated.target.currencyId &&
                it.reward == validated.reward.currencyId &&
                it.isFarm && !it.isRemoved
        } ?: error("The exact Demeter farm is not active on SORA")
        check(freshFarm.totalTokensInPool >= BigInteger.ZERO) {
            "Fresh Demeter farm state is invalid"
        }
        mutationAuthorizer.requireFreshBalances(
            context = validated.context,
            spends = listOf(
                SoraAssetSpend(
                    validated.target,
                    amountInPlanks,
                    "Pool-token balance is insufficient for this Demeter deposit"
                )
            ),
            feeInPlanks = feeInPlanks
        )
        return reauthorizeDemeterContext(
            request,
            DEMETER_DEPOSIT_CALL,
            validated,
            expectedIdentity
        ).copy(amountInPlanks = amountInPlanks)
    }

    private suspend fun validateWithdraw(
        request: DemeterMutationRequest,
        feeInPlanks: BigInteger,
        expectedIdentity: SoraMutationIdentity? = null
    ): ValidatedDemeterMutation {
        val validated = validateDemeterContext(
            request,
            DEMETER_WITHDRAW_CALL,
            expectedIdentity
        )
        val amountInPlanks = validated.target.exactPositivePlanks(
            requireNotNull(request.amount),
            "Demeter withdrawal amount"
        )
        val freshPosition = requireFreshPosition(request.chainId, validated)
        check(amountInPlanks <= freshPosition.amount) {
            "Demeter position balance is insufficient for this withdrawal"
        }
        mutationAuthorizer.requireFreshBalances(validated.context, emptyList(), feeInPlanks)
        return reauthorizeDemeterContext(
            request,
            DEMETER_WITHDRAW_CALL,
            validated,
            expectedIdentity
        ).copy(amountInPlanks = amountInPlanks)
    }

    private suspend fun validateClaim(
        request: DemeterMutationRequest,
        feeInPlanks: BigInteger,
        expectedIdentity: SoraMutationIdentity? = null
    ): ValidatedDemeterMutation {
        val validated = validateDemeterContext(request, DEMETER_CLAIM_CALL, expectedIdentity)
        val freshPosition = requireFreshPosition(request.chainId, validated)
        check(freshPosition.rewardAmount > BigInteger.ZERO) { "No fresh Demeter rewards are available" }
        mutationAuthorizer.requireFreshBalances(validated.context, emptyList(), feeInPlanks)
        return reauthorizeDemeterContext(
            request,
            DEMETER_CLAIM_CALL,
            validated,
            expectedIdentity
        )
    }

    private suspend fun validateDemeterContext(
        request: DemeterMutationRequest,
        requiredCall: SoraRuntimeCall,
        expectedIdentity: SoraMutationIdentity?
    ): ValidatedDemeterMutation {
        val context = mutationAuthorizer.authorize(
            feature = SoraMutationFeature.Demeter,
            chainId = request.chainId,
            requestedAssets = listOf(request.base, request.target, request.reward),
            requiredCalls = setOf(requiredCall),
            expectedIdentity = expectedIdentity
        )
        return ValidatedDemeterMutation(
            context = context,
            base = context.assets[0],
            target = context.assets[1],
            reward = context.assets[2]
        )
    }

    private suspend fun reauthorizeDemeterContext(
        request: DemeterMutationRequest,
        requiredCall: SoraRuntimeCall,
        context: ValidatedDemeterMutation,
        expectedIdentity: SoraMutationIdentity?
    ): ValidatedDemeterMutation {
        if (expectedIdentity == null) return context

        return validateDemeterContext(
            request = request,
            requiredCall = requiredCall,
            expectedIdentity = context.context.identity
        )
    }

    private suspend fun requireFreshPosition(chainId: ChainId, validated: ValidatedDemeterMutation): DemeterStorage =
        getDemeter(chainId, validated.context.accountAddress)
        ?.singleOrNull {
            it.farm &&
                it.base == validated.base.currencyId &&
                it.pool == validated.target.currencyId &&
                it.reward == validated.reward.currencyId
        } ?: error("The exact fresh Demeter position is unavailable")

    private suspend fun getSoraAssets(chainId: ChainId): List<Asset> {
        val soraChain = chainRegistry.getChain(chainId)
        val wallet = accountRepository.getSelectedMetaAccount()
        val accountId = wallet.accountId(soraChain)
        val soraAssets = soraChain.assets.mapNotNull { chainAsset ->
            accountId?.let {
                walletRepository.getAsset(
                    metaId = wallet.id,
                    accountId = accountId,
                    chainAsset = chainAsset,
                    minSupportedVersion = null
                )
            }
        }
        return soraAssets
    }

    private suspend fun getRewardTokens(chainId: ChainId): List<DemeterRewardTokenStorage> {
        val runtime = chainRegistry.getRuntimeOrNull(chainId) ?: return emptyList()
        val chain = chainRegistry.getChain(chainId)

        val storage = runtime.metadata.module("DemeterFarmingPlatform")
            .storage("TokenInfos")
        val type = storage.type.value ?: return emptyList()
        val storageKey = storage.storageKey(
            runtime
        )

        val socketService = chainRegistry.awaitConnection(chainId).socketService

        return bulkRetriever.retrieveAllValues(socketService, storageKey).mapNotNull { hex ->
            hex.value?.let { hexValue ->
                runCatching {
                    type.fromHex(runtime, hexValue)
                        ?.safeCast<Struct.Instance>()?.let { decoded ->
                            decoded.get<ByteArray>("teamAccount")?.let { chain.addressOf(it) }
                            DemeterRewardTokenStorage(
                                token = hex.key.assetIdFromKey(),
                                account = decoded.get<ByteArray>("teamAccount")?.let { chain.addressOf(it) }.orEmpty(),
                                farmsTotalMultiplier = decoded.get<BigInteger>("farmsTotalMultiplier")!!,
                                stakingTotalMultiplier = decoded.get<BigInteger>("stakingTotalMultiplier")!!,
                                tokenPerBlock = decoded.get<BigInteger>("tokenPerBlock")!!,
                                farmsAllocation = decoded.get<BigInteger>("farmsAllocation")!!,
                                stakingAllocation = decoded.get<BigInteger>("stakingAllocation")!!,
                                teamAllocation = decoded.get<BigInteger>("teamAllocation")!!,
                            )
                        }
                }.getOrNull()
            }
        }
    }

    internal open suspend fun getAllFarms(chainId: ChainId): List<DemeterBasicStorage> {
        val runtime = chainRegistry.getRuntimeOrNull(chainId) ?: return emptyList()
        val storage = runtime.metadata.module("DemeterFarmingPlatform")
            .storage("Pools")
        val type = storage.type.value ?: return emptyList()
        val storageKey = storage.storageKey(
            runtime,
        )
        val socketService = chainRegistry.awaitConnection(chainId).socketService
        val farms = bulkRetriever.retrieveAllValues(socketService, storageKey).mapNotNull { hex ->
            hex.value?.let { hexValue ->
                val decoded = type.fromHex(runtime, hexValue)
                decoded?.safeCast<List<*>>()
                    ?.filterIsInstance<Struct.Instance>()
                    ?.mapNotNull { struct ->
                        runCatching {
                            DemeterBasicStorage(
                                base = struct.mapToToken("baseAsset")!!,
                                pool = hex.key.assetIdFromKey(1),
                                reward = hex.key.assetIdFromKey(),
                                multiplier = struct.get<BigInteger>("multiplier")!!,
                                isCore = struct.get<Boolean>("isCore")!!,
                                isFarm = struct.get<Boolean>("isFarm")!!,
                                isRemoved = struct.get<Boolean>("isRemoved")!!,
                                depositFee = struct.get<BigInteger>("depositFee")!!,
                                totalTokensInPool = struct.get<BigInteger>("totalTokensInPool")!!,
                                rewards = struct.get<BigInteger>("rewards")!!,
                                rewardsToBeDistributed = struct.get<BigInteger>("rewardsToBeDistributed")!!,
                            )
                        }.getOrNull()
                    }
            }
        }.flatten().filter {
            it.isFarm && it.isRemoved.not()
        }
        return farms
    }

    private fun getEmission(
        basic: DemeterBasicStorage,
        reward: DemeterRewardTokenStorage?,
        precision: Int
    ): BigDecimal {
        val tokenMultiplier =
            (if (basic.isFarm) reward?.farmsTotalMultiplier else reward?.stakingTotalMultiplier)?.toBigDecimal(
                precision
            ) ?: BigDecimal.ZERO
        if (tokenMultiplier.isZero()) return BigDecimal.ZERO
        val multiplier = basic.multiplier.toBigDecimal(precision).div(tokenMultiplier)
        val allocation =
            mapBalance(
                (if (basic.isFarm) reward?.farmsAllocation else reward?.stakingAllocation)
                    ?: BigInteger.ZERO,
                precision
            )
        val tokenPerBlock = reward?.tokenPerBlock?.toBigDecimal(precision) ?: BigDecimal.ZERO
        return allocation.times(tokenPerBlock).times(multiplier)
    }

    @Suppress("ComplexCondition")
    internal open suspend fun getDemeter(chainId: ChainId, address: String): List<DemeterStorage>? {
        val runtime = chainRegistry.getRuntimeOrNull(chainId) ?: return emptyList()
        val storage = runtime.metadata.module("DemeterFarmingPlatform")
            .storage("UserInfos")
        val storageKey = storage.storageKey(
            runtime,
            address.toAccountId(),
        )
        return getStorageHex(chainId, storageKey)?.let { hex ->
            storage.type.value
                ?.fromHex(runtime, hex)
                ?.safeCast<List<*>>()
                ?.filterIsInstance<Struct.Instance>()
                ?.mapNotNull { instance ->
                    val baseToken = instance.mapToToken("baseAsset")
                    val poolToken = instance.mapToToken("poolAsset")
                    val rewardToken = instance.mapToToken("rewardAsset")
                    val isFarm = instance.get<Boolean>("isFarm")
                    val pooled = instance.get<BigInteger>("pooledTokens")
                    val rewards = instance.get<BigInteger>("rewards")
                    if (isFarm != null && baseToken != null && poolToken != null &&
                        rewardToken != null && pooled != null && rewards != null
                    ) {
                        DemeterStorage(
                            base = baseToken,
                            pool = poolToken,
                            reward = rewardToken,
                            farm = isFarm,
                            amount = pooled,
                            rewardAmount = rewards,
                        )
                    } else {
                        null
                    }
                }
        }
    }

    private suspend fun getStorageHex(chainId: ChainId, storageKey: String): String? =
        chainRegistry.awaitConnection(chainId).socketService.executeAsync(
            request = GetStorageRequest(listOf(storageKey)),
            mapper = pojo<String>(),
        ).result

    fun Struct.Instance.mapToToken(field: String) = this.get<Struct.Instance>(field)?.getTokenId()?.toHexString(true)

    fun Struct.Instance.getTokenId() = get<List<*>>("code")
        ?.map { (it as BigInteger).toByte() }
        ?.toByteArray()

    fun String.assetIdFromKey() = this.takeLast(ASSET_SIZE).addHexPrefix()
    fun String.assetIdFromKey(pos: Int): String = this.substring(0, this.length - ASSET_SIZE * pos).assetIdFromKey()

    companion object {
        private const val BLOCKS_PER_YEAR = 5_256_000
        private const val ASSET_SIZE = 64
    }
}
