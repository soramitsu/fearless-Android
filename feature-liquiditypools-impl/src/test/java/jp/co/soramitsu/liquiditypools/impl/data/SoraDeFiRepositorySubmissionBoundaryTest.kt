package jp.co.soramitsu.liquiditypools.impl.data

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.PoolDao
import jp.co.soramitsu.liquiditypools.data.PoolDataDto
import jp.co.soramitsu.liquiditypools.data.PoolsRepository
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

class SoraDeFiRepositorySubmissionBoundaryTest {

    @Test
    fun `add liquidity rejects a selected account change after fee estimation`() = runBlocking {
        val harness = Harness()
        harness.rejectFinalReauthorization(harness.base, harness.target)

        val result = harness.poolsRepository().observeAddLiquidity(
            chainId = soraMainChainId,
            address = ACCOUNT_ADDRESS,
            tokenBase = harness.base,
            tokenTarget = harness.target,
            amountBase = BigDecimal.ONE,
            amountTarget = BigDecimal.ONE,
            pairEnabled = true,
            pairPresented = true,
            slippageTolerance = 1.0
        )

        harness.assertRejectedAtFinalBoundary(result)
    }

    @Test
    fun `remove liquidity rejects a selected account change after fee estimation`() = runBlocking {
        val harness = Harness()
        harness.rejectFinalReauthorization(harness.base, harness.target)

        val result = harness.poolsRepository().observeRemoveLiquidity(
            chainId = soraMainChainId,
            tokenBase = harness.base,
            tokenTarget = harness.target,
            markerAssetDesired = BigDecimal.ONE,
            firstAmountMin = BigDecimal.ONE,
            secondAmountMin = BigDecimal.ONE
        )

        harness.assertRejectedAtFinalBoundary(result)
    }

    @Test
    fun `Demeter deposit rejects a selected account change after fee estimation`() = runBlocking {
        val harness = Harness()
        harness.rejectFinalReauthorization(harness.base, harness.target, harness.reward)

        val result = harness.demeterRepository().deposit(
            chainId = soraMainChainId,
            pool = harness.farm,
            amount = BigDecimal.ONE
        )

        harness.assertRejectedAtFinalBoundary(result)
    }

    @Test
    fun `Demeter withdraw rejects a selected account change after fee estimation`() = runBlocking {
        val harness = Harness()
        harness.rejectFinalReauthorization(harness.base, harness.target, harness.reward)

        val result = harness.demeterRepository().withdraw(
            chainId = soraMainChainId,
            pool = harness.position,
            amount = BigDecimal.ONE
        )

        harness.assertRejectedAtFinalBoundary(result)
    }

    @Test
    fun `Demeter claim rejects a selected account change after fee estimation`() = runBlocking {
        val harness = Harness()
        harness.rejectFinalReauthorization(harness.base, harness.target, harness.reward)

        val result = harness.demeterRepository().claimRewards(
            chainId = soraMainChainId,
            pool = harness.position
        )

        harness.assertRejectedAtFinalBoundary(result)
    }

    private class Harness {
        val base = coreAsset("base", BASE_CURRENCY_ID, "BASE")
        val target = coreAsset("target", TARGET_CURRENCY_ID, "TARGET")
        val reward = coreAsset("reward", REWARD_CURRENCY_ID, "REWARD")
        private val xor = coreAsset("xor", XOR_CURRENCY_ID, "XOR", isUtility = true)

        private val chain = mock<Chain> {
            on { id }.thenReturn(soraMainChainId)
        }
        private val extrinsicService: ExtrinsicService = Mockito.mock(
            ExtrinsicService::class.java
        ) { invocation ->
            when (invocation.method.name) {
                "estimateFee" -> {
                    estimateAttempts++
                    BigInteger.TEN
                }

                "submitExtrinsic" -> {
                    submitAttempts++
                    Mockito.RETURNS_DEFAULTS.answer(invocation)
                }

                else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
            }
        }
        private val chainRegistry = mock<ChainRegistry>()
        private val accountRepository = mock<AccountRepository>()
        private val poolDao = mock<PoolDao>()
        private val database = mock<AppDatabase>()
        private val featureToggleStore = mock<ProductFeatureToggleStore>()
        private val mutationAuthorizer = mock<SoraDeFiMutationAuthorizer>()
        private val bulkRetriever = mock<BulkRetriever>()
        private val walletRepository = mock<WalletRepository>()
        private val poolsRepository = mock<PoolsRepository>()

        private var authorizeAttempts = 0
        private var estimateAttempts = 0
        private var submitAttempts = 0

        val farm = DemeterFarmingBasicPool(
            tokenBase = walletAsset(base),
            tokenTarget = walletAsset(target),
            tokenReward = walletAsset(reward),
            apr = 1.0,
            tvl = BigDecimal.ONE,
            fee = 0.0
        )
        val position = DemeterFarmingPool(
            tokenBase = walletAsset(base),
            tokenTarget = walletAsset(target),
            tokenReward = walletAsset(reward),
            apr = 1.0,
            amount = BigDecimal.TEN,
            amountReward = BigDecimal.ONE
        )

        init {
            whenever(featureToggleStore.polkaswapMutationsEnabled).thenReturn(true)
            whenever(featureToggleStore.demeterMutationsEnabled).thenReturn(true)
        }

        suspend fun rejectFinalReauthorization(vararg assets: CoreAsset) {
            val context = SoraMutationContext(
                chain = chain,
                accountId = ACCOUNT_ID,
                accountAddress = ACCOUNT_ADDRESS,
                identity = SoraMutationIdentity(META_ID, ACCOUNT_IDENTITY),
                assets = assets.toList(),
                feeAsset = xor
            )
            whenever(
                mutationAuthorizer.authorize(
                    feature = any(),
                    chainId = any(),
                    requestedAssets = any(),
                    requiredCalls = any(),
                    expectedIdentity = anyOrNull()
                )
            ).thenAnswer {
                authorizeAttempts++
                if (authorizeAttempts == FINAL_AUTHORIZATION_ATTEMPT) {
                    throw IllegalStateException(LATE_IDENTITY_FAILURE)
                }
                context
            }
            whenever(
                mutationAuthorizer.requireFreshBalances(any(), any(), any())
            ).thenReturn(Unit)
        }

        fun poolsRepository(): PoolsRepositoryImpl = object : PoolsRepositoryImpl(
            extrinsicService = extrinsicService,
            chainRegistry = chainRegistry,
            accountRepository = accountRepository,
            poolDao = poolDao,
            db = database,
            featureToggleStore = featureToggleStore,
            mutationAuthorizer = mutationAuthorizer
        ) {
            override suspend fun getPoolBaseTokenDexId(chainId: ChainId, tokenId: String?) = 0

            override suspend fun getUserPoolData(
                chainId: ChainId,
                address: String,
                baseTokenId: String,
                targetTokenId: ByteArray
            ) = PoolDataDto(
                baseAssetId = BASE_CURRENCY_ID,
                assetId = TARGET_CURRENCY_ID,
                reservesFirst = LARGE_BALANCE,
                reservesSecond = LARGE_BALANCE,
                totalIssuance = LARGE_BALANCE,
                poolProvidersBalance = LARGE_BALANCE,
                reservesAccount = ACCOUNT_ADDRESS
            )
        }

        fun demeterRepository(): DemeterFarmingRepositoryImpl = object : DemeterFarmingRepositoryImpl(
            chainRegistry = chainRegistry,
            bulkRetriever = bulkRetriever,
            accountRepository = accountRepository,
            walletRepository = walletRepository,
            poolsRepository = poolsRepository,
            extrinsicService = extrinsicService,
            featureToggleStore = featureToggleStore,
            mutationAuthorizer = mutationAuthorizer
        ) {
            override suspend fun getAllFarms(chainId: ChainId) = listOf(
                DemeterBasicStorage(
                    base = BASE_CURRENCY_ID,
                    pool = TARGET_CURRENCY_ID,
                    reward = REWARD_CURRENCY_ID,
                    multiplier = BigInteger.ONE,
                    isCore = true,
                    isFarm = true,
                    isRemoved = false,
                    depositFee = BigInteger.ZERO,
                    totalTokensInPool = LARGE_BALANCE,
                    rewards = LARGE_BALANCE,
                    rewardsToBeDistributed = LARGE_BALANCE
                )
            )

            override suspend fun getDemeter(chainId: ChainId, address: String) = listOf(
                DemeterStorage(
                    base = BASE_CURRENCY_ID,
                    pool = TARGET_CURRENCY_ID,
                    reward = REWARD_CURRENCY_ID,
                    farm = true,
                    amount = LARGE_BALANCE,
                    rewardAmount = LARGE_BALANCE
                )
            )
        }

        fun assertRejectedAtFinalBoundary(result: Result<String>?) {
            val requiredResult = requireNotNull(result)
            assertTrue(requiredResult.isFailure)
            assertEquals(LATE_IDENTITY_FAILURE, requiredResult.exceptionOrNull()?.message)
            assertEquals(FINAL_AUTHORIZATION_ATTEMPT, authorizeAttempts)
            assertEquals(1, estimateAttempts)
            assertEquals(0, submitAttempts)
        }

        private fun walletAsset(asset: CoreAsset) = WalletAsset.createEmpty(
            chainAsset = asset,
            metaId = META_ID,
            accountId = ACCOUNT_ID,
            minSupportedVersion = null
        )

        private fun coreAsset(
            id: String,
            currencyId: String,
            symbol: String,
            isUtility: Boolean = false
        ) = mock<CoreAsset> {
            on { this.id }.thenReturn(id)
            on { chainId }.thenReturn(soraMainChainId)
            on { this.currencyId }.thenReturn(currencyId)
            on { precision }.thenReturn(PRECISION)
            on { this.symbol }.thenReturn(symbol)
            on { this.isUtility }.thenReturn(isUtility)
        }
    }

    private companion object {
        val ACCOUNT_ID = ByteArray(32) { 7 }
        val LARGE_BALANCE: BigInteger = BigInteger.TEN.pow(30)
        const val META_ID = 7L
        const val PRECISION = 18
        const val FINAL_AUTHORIZATION_ATTEMPT = 3
        const val ACCOUNT_ADDRESS = "selected-sora-account"
        const val ACCOUNT_IDENTITY = "0x0707"
        const val LATE_IDENTITY_FAILURE = "The selected SORA account changed at the final boundary"
        const val BASE_CURRENCY_ID =
            "0x0100000000000000000000000000000000000000000000000000000000000000"
        const val XOR_CURRENCY_ID =
            "0x0200000000000000000000000000000000000000000000000000000000000000"
        const val TARGET_CURRENCY_ID =
            "0x0300000000000000000000000000000000000000000000000000000000000000"
        const val REWARD_CURRENCY_ID =
            "0x0400000000000000000000000000000000000000000000000000000000000000"
    }
}
