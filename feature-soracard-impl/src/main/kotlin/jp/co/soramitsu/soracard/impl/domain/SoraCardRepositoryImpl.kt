package jp.co.soramitsu.soracard.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.scale.Schema
import jp.co.soramitsu.shared_utils.scale.sizedByteArray
import jp.co.soramitsu.shared_utils.scale.uint128
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.soracard.api.domain.SoraCardRepository
import jp.co.soramitsu.soracard.impl.data.SoraCardApi
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

class SoraCardRepositoryImpl @Inject constructor(
    private val soraCardApi: SoraCardApi,
    @Named(REMOTE_STORAGE_SOURCE)
    private val remoteStorageSource: StorageDataSource
) : SoraCardRepository {
    private companion object {
        private const val XOR_PRICE_REQUEST_DELAY_MILLIS = 30_000
    }

    private var cachedXorEuroPrice: Pair<BigDecimal?, Long> = Pair(null, 0)

    override suspend fun getXorEuroPrice(): BigDecimal? {
        val (xorEurPrice, cachedTime) = cachedXorEuroPrice

        val cacheExpired = cachedTime + XOR_PRICE_REQUEST_DELAY_MILLIS < System.currentTimeMillis()

        return if (xorEurPrice != null && cacheExpired.not()) {
            xorEurPrice
        } else {
            val soraPrice = soraCardApi.getXorEuroPrice()
            val newValue = soraPrice?.price?.toBigDecimalOrNull()
            cachedXorEuroPrice = newValue to System.currentTimeMillis()

            newValue
        }
    }

    override suspend fun getStakedFarmedAmountOfAsset(address: String, asset: Asset): BigInteger {
        val tokenId = asset.currencyId
        val amount = getDemeter(address, asset.chainId)
            ?.filter {
                it.farm.not() && it.base == tokenId && it.pool == tokenId
            }
            ?.sumOf { it.amount }
        return amount.orZero()
    }

    private suspend fun getDemeter(address: String, chainId: ChainId): List<DemeterStorage>? {
        return remoteStorageSource.query(
            chainId = chainId,
            keyBuilder = {
                it.metadata.module("DemeterFarmingPlatform").storage("UserInfos").storageKey(it, address.toAccountId())
            },
            binding = ::bindDemeter
        )
    }

    private fun bindDemeter(
        scale: String?,
        runtime: RuntimeSnapshot
    ): List<DemeterStorage>? {
        scale ?: return emptyList()
        val returnType = runtime.metadata.storageReturnType("DemeterFarmingPlatform", "UserInfos")

        return (returnType.fromHexOrIncompatible(scale, runtime) as? List<*>)
            ?.filterIsInstance<Struct.Instance>()
            ?.mapNotNull { instance ->
                val baseToken = instance.get<Struct.Instance>("baseAsset")
                    ?.get<List<*>>("code")?.map {
                        (it as BigInteger).toByte()
                    }?.toByteArray()?.toHexString(true)
                val poolToken = instance.get<Struct.Instance>("poolAsset")
                    ?.get<List<*>>("code")?.map {
                        (it as BigInteger).toByte()
                    }?.toByteArray()?.toHexString(true)
                val rewardToken = instance.get<Struct.Instance>("rewardAsset")
                    ?.get<List<*>>("code")?.map {
                        (it as BigInteger).toByte()
                    }?.toByteArray()?.toHexString(true)
                val isFarm = instance.get<Boolean>("isFarm")
                val pooled = instance.get<BigInteger>("pooledTokens")
                if (isFarm != null && baseToken != null && poolToken != null && rewardToken != null && pooled != null) {
                    DemeterStorage(
                        base = baseToken,
                        pool = poolToken,
                        reward = rewardToken,
                        farm = isFarm,
                        amount = pooled,
                    )
                } else {
                    null
                }
            }
    }

    private class DemeterStorage(
        val base: String,
        val pool: String,
        val reward: String,
        val farm: Boolean,
        val amount: BigInteger,
    )

    override suspend fun getXorPooledAmount(address: String, asset: Asset): BigDecimal {
        val baseTokenId = asset.currencyId ?: error("XOR token not found")

        val tokenIds = getUserPoolsTokenIds(address, asset.chainId)

        val xorPoolPairTokenIds = tokenIds[baseTokenId].orEmpty()

        return xorPoolPairTokenIds.mapNotNull { tokenId ->
            val xorReservesInPlanks = getXorReserves(asset.chainId, baseTokenId, tokenId) ?: return@mapNotNull null

            getPoolReserveAccount(asset.chainId, baseTokenId, tokenId)?.let { reserveAccountId ->

                getTotalIssuances(asset.chainId, reserveAccountId)?.let { totalIssuancesInPlanks ->
                    val providersBalanceInPlanks = getPoolProviders(asset.chainId, reserveAccountId, address).orZero()

                    val reserves = asset.amountFromPlanks(xorReservesInPlanks)
                    val poolProvidersBalance = asset.amountFromPlanks(providersBalanceInPlanks)
                    val poolTotalIssuance = asset.amountFromPlanks(totalIssuancesInPlanks)
                    val basePooled = reserves.multiply(poolProvidersBalance).divide(poolTotalIssuance, asset.precision, RoundingMode.HALF_EVEN)
                    basePooled
                }
            }
        }.sumByBigDecimal { it }
    }

    private suspend fun getUserPoolsTokenIds(address: String, chainId: ChainId) = remoteStorageSource.queryByPrefix(
        chainId = chainId,
        prefixKeyBuilder = {
            it.metadata.module("PoolXYK").storage("AccountPools").storageKey(it, address.toAccountId())
        },
        keyExtractor = ::soraTokenIdFromPoolsStorageKey,
        binding = ::bindAccountPools
    )

    private fun soraTokenIdFromPoolsStorageKey(key: String) = key.takeLast(64).requireHexPrefix()

    private fun bindAccountPools(
        scale: String?,
        runtime: RuntimeSnapshot,
        key: String
    ): List<ByteArray> {
        scale ?: return emptyList()
        val returnType = runtime.metadata.storageReturnType("PoolXYK", "AccountPools")

        val tokens: List<ByteArray> = (returnType.fromHexOrIncompatible(scale, runtime) as? List<*>)
            ?.filterIsInstance<Struct.Instance>()
            ?.mapNotNull { instance ->
                instance.get<List<*>>("code")?.map {
                    (it as BigInteger).toByte()
                }?.toByteArray()
            }
            .orEmpty()

        return tokens
    }

    private suspend fun getPoolProviders(chainId: ChainId, reserveAccountId: ByteArray, address: String) = remoteStorageSource.query(
        chainId = chainId,
        keyBuilder = {
            it.metadata.module("PoolXYK").storage("PoolProviders").storageKey(
                it,
                reserveAccountId,
                address.toAccountId()
            )
        },
        binding = { struct, runtime ->
            struct?.let {
                val encodableStruct = PoolProviders.read(struct.fromHex())
                encodableStruct[encodableStruct.schema.poolProviders]
            }
        }
    )

    private suspend fun getTotalIssuances(chainId: ChainId, reserveAccountId: ByteArray) = remoteStorageSource.query(
        chainId = chainId,
        keyBuilder = {
            it.metadata.module("PoolXYK").storage("TotalIssuances").storageKey(it, reserveAccountId)
        },
        binding = { struct, runtime ->
            struct?.let {
                val encodableStruct = TotalIssuance.read(struct.fromHex())
                encodableStruct[encodableStruct.schema.totalIssuance]
            }
        }
    )

    private suspend fun getPoolReserveAccount(chainId: ChainId, baseTokenId: String, tokenId: ByteArray) = remoteStorageSource.query(
        chainId = chainId,
        keyBuilder = {
            it.metadata.module("PoolXYK").storage("Properties").storageKey(
                it,
                getCommonPrimitivesAssetId32("code", baseTokenId.fromHex()),
                getCommonPrimitivesAssetId32("code", tokenId)
            )
        },
        binding = { struct, _ ->
            struct?.let {
                val encodableStruct = PoolPropertiesResponse.read(struct.fromHex())
                encodableStruct[encodableStruct.schema.first]
            }
        }
    )

    private suspend fun getXorReserves(chainId: ChainId, baseTokenId: String, tokenId: ByteArray) = remoteStorageSource.query(
        chainId = chainId,
        keyBuilder = {
            it.metadata.module("PoolXYK").storage("Reserves").storageKey(
                it,
                getCommonPrimitivesAssetId32("code", baseTokenId.fromHex()),
                getCommonPrimitivesAssetId32("code", tokenId)
            )
        },
        binding = { struct, _ ->
            struct?.let {
                val encodableStruct = ReservesResponse.read(struct.fromHex())
                encodableStruct[encodableStruct.schema.first]
            }
        }
    )

    private fun getCommonPrimitivesAssetId32(name: String, tokenId: ByteArray) = Struct.Instance(
        mapOf(
            name to tokenId.toList().map { it.toInt().toBigInteger() }
        )
    )

    object ReservesResponse : Schema<ReservesResponse>() {
        val first by uint128()
        val second by uint128()
    }

    object PoolPropertiesResponse : Schema<PoolPropertiesResponse>() {
        val first by sizedByteArray(32)
        val second by sizedByteArray(32)
    }

    object TotalIssuance : Schema<TotalIssuance>() {
        val totalIssuance by uint128()
    }

    object PoolProviders : Schema<PoolProviders>() {
        val poolProviders by uint128()
    }
}
