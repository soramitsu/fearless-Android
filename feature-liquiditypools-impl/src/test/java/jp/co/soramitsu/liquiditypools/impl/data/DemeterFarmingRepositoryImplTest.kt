package jp.co.soramitsu.liquiditypools.impl.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.liquiditypools.data.PoolsRepository
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.PSWAP_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.UNKNOWN_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.pswap
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.data.SoraRuntime.code
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class DemeterFarmingRepositoryImplTest {

    private val node = FakeSoraNode(listOf(xor, valToken, pswap))
    private val builder = mockk<ExtrinsicBuilder>(relaxed = true)
    private var submitted = 0
    private val extrinsicService = mockk<ExtrinsicService> {
        coEvery { estimateFee(any(), any<ByteArray>(), any(), any(), any(), any()) } coAnswers {
            arg<suspend ExtrinsicBuilder.() -> Unit>(5).invoke(builder)
            BigInteger.TEN
        }
        coEvery { submitExtrinsic(any(), any(), any(), any(), any(), any()) } coAnswers {
            submitted++
            arg<suspend ExtrinsicBuilder.() -> Unit>(5).invoke(builder)
            Result.success(TX_HASH)
        }
    }
    private val bulkRetriever = mockk<BulkRetriever>()
    private val bulkValues = mutableMapOf<String, Map<String, String?>>()
    private val metaAccount = mockk<MetaAccount>(relaxed = true) {
        every { id } returns META_ID
        every { substrateAccountId } returns USER_ACCOUNT
    }
    private val accountRepository = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns metaAccount
        coEvery { getSelectedAccount(soraMainChainId) } returns
            Account(USER_ADDRESS, "SORA", USER_ACCOUNT.toHexString(), CryptoType.SR25519, 0)
    }
    private val walletRepository = mockk<WalletRepository> {
        val prices = mapOf(xor to BigDecimal.ONE, valToken to BigDecimal("3"), pswap to BigDecimal.ONE)
        prices.forEach { (asset, price) ->
            coEvery { getAsset(META_ID, USER_ACCOUNT, asset, null) } returns walletAsset(asset, fiatRate = price)
        }
    }
    private val poolsRepository = mockk<PoolsRepository> {
        coEvery { getBasicPool(soraMainChainId, XOR_ID, VAL_ID) } returns BasicPoolData(
            baseToken = xor,
            targetToken = valToken,
            baseReserves = BigDecimal("100"),
            targetReserves = BigDecimal("200"),
            totalIssuance = BigDecimal("100"),
            reserveAccount = "reserve"
        )
    }
    private val toggles = mockk<ProductFeatureToggleStore> {
        every { demeterMutationsEnabled } returns true
    }
    private val authorizer = mockk<SoraDeFiMutationAuthorizer>()

    private val repository = DemeterFarmingRepositoryImpl(
        chainRegistry = node.chainRegistry,
        bulkRetriever = bulkRetriever,
        accountRepository = accountRepository,
        walletRepository = walletRepository,
        poolsRepository = poolsRepository,
        extrinsicService = extrinsicService,
        featureToggleStore = toggles,
        mutationAuthorizer = authorizer
    )

    init {
        coEvery { bulkRetriever.retrieveAllKeys(node.socket, any(), any()) } answers {
            bulkValues[secondArg()].orEmpty().keys.toList()
        }
        coEvery { bulkRetriever.queryKeys(node.socket, any(), any()) } answers {
            bulkValues.values.fold(emptyMap<String, String?>()) { acc, values -> acc + values }
                .filterKeys { it in secondArg<List<String>>() }
        }
        farms(farm(), farm(isRemoved = true), farm(isFarm = false))
        rewardTokens()
        positions(position())
    }

    @Test
    fun `capability reason names the runtime call of each Demeter action`() = runBlocking {
        val calls = mutableListOf<Set<SoraRuntimeCall>>()
        coEvery { authorizer.capabilityReason(SoraMutationFeature.Demeter, soraMainChainId, capture(calls)) } returns null

        DemeterMutationAction.entries.forEach { assertNull(repository.mutationCapabilityReason(soraMainChainId, it)) }

        assertEquals(
            listOf("deposit", "withdraw", "get_rewards").map { setOf(SoraRuntimeCall(PALLET, it)) },
            calls
        )
    }

    @Test
    fun `available farms combine pool storage, reward emission and fiat prices`() = runBlocking {
        val pools = repository.getAvailablePools(soraMainChainId)

        val farm = pools.single()
        assertEquals(xor, farm.tokenBase.token.configuration)
        assertEquals(valToken, farm.tokenTarget.token.configuration)
        assertEquals(pswap, farm.tokenReward.token.configuration)
        // TVL = target reserves / issuance (2) x 10 LP x 2 sides x $3; APR = 0.3 PSWAP per block x blocks per year / TVL.
        assertTrue(BigDecimal("120").compareTo(farm.tvl) == 0)
        assertEquals(1_314_000.0, farm.apr, 1e-6)
        assertEquals(4.0, farm.fee, 1e-9)
    }

    @Test
    fun `available farms are cached until refresh`() = runBlocking {
        repository.getAvailablePools(soraMainChainId)
        repository.getAvailablePools(soraMainChainId)
        coVerify(exactly = 1) { bulkRetriever.retrieveAllKeys(node.socket, SoraRuntime.prefix(PALLET, "Pools"), any()) }

        repository.refresh(soraMainChainId)
        repository.getAvailablePools(soraMainChainId)

        coVerify(exactly = 2) { bulkRetriever.retrieveAllKeys(node.socket, SoraRuntime.prefix(PALLET, "Pools"), any()) }
    }

    @Test
    fun `farms without a priced pool or a known reward token are handled`() = runBlocking {
        coEvery { poolsRepository.getBasicPool(soraMainChainId, XOR_ID, VAL_ID) } returns null
        farms(farm(), farm(reward = UNKNOWN_ID))

        val farm = repository.getAvailablePools(soraMainChainId).single()

        assertTrue(BigDecimal.ZERO.compareTo(farm.tvl) == 0)
        assertEquals(0.0, farm.apr, 0.0)
    }

    @Test
    fun `farms of unknown assets are skipped and an unweighted reward token emits nothing`() = runBlocking {
        farms(farm(), farm(base = UNKNOWN_ID), farm(pool = UNKNOWN_ID))
        rewardTokens(farmsMultiplier = BigInteger.ZERO)

        val farm = repository.getAvailablePools(soraMainChainId).single()

        assertEquals(valToken, farm.tokenTarget.token.configuration)
        assertEquals(0.0, farm.apr, 0.0)
        assertTrue(BigDecimal("120").compareTo(farm.tvl) == 0)
    }

    @Test
    fun `staking farms are valued by their pooled tokens`() = runBlocking {
        val staking = object : DemeterFarmingRepositoryImpl(
            node.chainRegistry, bulkRetriever, accountRepository, walletRepository, poolsRepository,
            extrinsicService, toggles, authorizer
        ) {
            override suspend fun getAllFarms(chainId: String) = listOf(
                DemeterBasicStorage(
                    base = XOR_ID, pool = VAL_ID, reward = PSWAP_ID, multiplier = BigInteger.ONE, isCore = true,
                    isFarm = false, isRemoved = false, depositFee = BigInteger.ZERO,
                    totalTokensInPool = BigInteger.TEN.pow(19), rewards = BigInteger.ZERO,
                    rewardsToBeDistributed = BigInteger.ZERO
                )
            )
        }

        val farm = staking.getAvailablePools(soraMainChainId).single()

        // 10 pooled VAL at $3; the staking multiplier (4) and allocation (0.2) give 0.05 PSWAP per block.
        assertTrue(BigDecimal("30").compareTo(farm.tvl) == 0)
        assertEquals(876_000.0, farm.apr, 1e-6)
    }

    @Test
    fun `farmed positions keep only active farm deposits and are cached per account`() = runBlocking {
        positions(
            position(),
            position(pooled = BigInteger.ZERO),
            position(isFarm = false),
            position(pool = UNKNOWN_ID)
        )

        val positions = requireNotNull(repository.getFarmedPools(soraMainChainId))
        repository.getFarmedPools(soraMainChainId)

        val position = positions.single()
        assertEquals(valToken, position.tokenTarget.token.configuration)
        assertTrue(BigDecimal("5").compareTo(position.amount) == 0)
        assertTrue(BigDecimal("2").compareTo(position.amountReward) == 0)
        assertEquals(1_314_000.0, position.apr, 1e-6)
        assertEquals(1, node.requestsOf("state_getStorage").size)

        repository.refresh(soraMainChainId)
        repository.getFarmedPools(soraMainChainId)
        assertEquals(2, node.requestsOf("state_getStorage").size)
    }

    @Test
    fun `farmed positions are unknown without account storage and empty without runtime`() = runBlocking {
        node.storage.clear()
        assertNull(repository.getFarmedPools(soraMainChainId))

        coEvery { node.chainRegistry.getRuntimeOrNull(soraMainChainId) } returns null
        repository.refresh(soraMainChainId)

        assertEquals(emptyList<DemeterFarmingPool>(), repository.getFarmedPools(soraMainChainId))
        assertEquals(emptyList<DemeterFarmingBasicPool>(), repository.getAvailablePools(soraMainChainId))
    }

    @Test
    fun `deposit submits the exact farm and clears cached farms`() = runBlocking {
        authorize(xor, valToken, pswap)
        repository.getAvailablePools(soraMainChainId)

        val result = repository.deposit(soraMainChainId, basicPool(), BigDecimal("1.5"))

        assertEquals(TX_HASH, result.getOrThrow())
        // The fee is estimated on exactly the call that is then submitted.
        val calls = mutableListOf<Map<String, Any?>>()
        verify(exactly = 2) { builder.call(PALLET, "deposit", capture(calls)) }
        assertEquals(calls[0], calls[1])
        val args = calls.last()
        assertEquals(code(XOR_ID), args["base_asset"])
        assertEquals(code(VAL_ID), args["pool_asset"])
        assertEquals(code(PSWAP_ID), args["reward_asset"])
        assertEquals(true, args["is_farm"])
        assertEquals(BigInteger("1500000000000000000"), args["pooled_tokens"])
        // Reward tokens are only read for the available-farm cache, which the submitted deposit invalidated.
        repository.getAvailablePools(soraMainChainId)
        coVerify(exactly = 2) { bulkRetriever.retrieveAllKeys(node.socket, SoraRuntime.prefix(PALLET, "TokenInfos"), any()) }
    }

    @Test
    fun `deposit fails closed without an active farm or with Demeter switched off`() = runBlocking {
        authorize(xor, pswap, valToken)
        assertEquals("The exact Demeter farm is not active on SORA", deposit().exceptionOrNull()?.message)

        authorize(xor, valToken, pswap)
        every { toggles.demeterMutationsEnabled } returns false
        assertEquals("Demeter actions are temporarily unavailable", deposit().exceptionOrNull()?.message)
        assertEquals(0, submitted)
    }

    @Test
    fun `withdraw is limited by the fresh farmed position`() = runBlocking {
        authorize(xor, valToken, pswap)

        assertEquals(TX_HASH, repository.withdraw(soraMainChainId, farmedPool(), BigDecimal("5")).getOrThrow())
        val calls = mutableListOf<Map<String, Any?>>()
        verify(exactly = 2) { builder.call(PALLET, "withdraw", capture(calls)) }
        assertEquals(
            listOf(BigInteger("5000000000000000000"), BigInteger("5000000000000000000")),
            calls.map {
            it["pooled_tokens"]
        }
        )

        val tooMuch = repository.withdraw(soraMainChainId, farmedPool(), BigDecimal("6"))
        assertEquals("Demeter position balance is insufficient for this withdrawal", tooMuch.exceptionOrNull()?.message)

        authorize(xor, pswap, valToken)
        val missing = repository.withdraw(soraMainChainId, farmedPool(), BigDecimal.ONE)
        assertEquals("The exact fresh Demeter position is unavailable", missing.exceptionOrNull()?.message)
        assertEquals(1, submitted)
    }

    @Test
    fun `claim needs fresh rewards and submits get_rewards`() = runBlocking {
        authorize(xor, valToken, pswap)

        assertEquals(TX_HASH, repository.claimRewards(soraMainChainId, farmedPool()).getOrThrow())
        verify { builder.call(PALLET, "get_rewards", any()) }

        positions(position(rewards = BigInteger.ZERO))
        val empty = repository.claimRewards(soraMainChainId, farmedPool())
        assertEquals("No fresh Demeter rewards are available", empty.exceptionOrNull()?.message)

        positions(position())
        every { toggles.demeterMutationsEnabled } returns false
        val disabled = repository.claimRewards(soraMainChainId, farmedPool())
        assertEquals("Demeter actions are temporarily unavailable", disabled.exceptionOrNull()?.message)
        assertEquals(1, submitted)
    }

    private suspend fun deposit() = repository.deposit(soraMainChainId, basicPool(), BigDecimal.ONE)

    private fun authorize(vararg assets: CoreAsset) {
        val context = SoraMutationContext(
            chain = node.chain,
            accountId = USER_ACCOUNT,
            accountAddress = USER_ADDRESS,
            identity = SoraMutationIdentity(META_ID, USER_ACCOUNT.toHexString()),
            assets = assets.toList(),
            feeAsset = xor
        )
        coEvery { authorizer.authorize(any(), any(), any(), any(), any()) } returns context
        coEvery { authorizer.requireFreshBalances(any(), any(), any()) } returns Unit
    }

    private fun basicPool() = DemeterFarmingBasicPool(
        tokenBase = walletAsset(xor),
        tokenTarget = walletAsset(valToken),
        tokenReward = walletAsset(pswap),
        apr = 1.0,
        tvl = BigDecimal.TEN,
        fee = 0.0
    )

    private fun farmedPool() = DemeterFarmingPool(
        tokenBase = walletAsset(xor),
        tokenTarget = walletAsset(valToken),
        tokenReward = walletAsset(pswap),
        apr = 1.0,
        amount = BigDecimal("5"),
        amountReward = BigDecimal("2")
    )

    private fun farm(
        reward: String = PSWAP_ID,
        isFarm: Boolean = true,
        isRemoved: Boolean = false,
        base: String = XOR_ID,
        pool: String = VAL_ID
    ) = pool to reward to SoraRuntime.struct(
        SoraRuntime.vecElement(PALLET, "Pools"),
        mapOf(
            "baseAsset" to code(base),
            "multiplier" to BigInteger.ONE,
            "depositFee" to BigInteger("40000000000000000"),
            "isCore" to true,
            "isFarm" to isFarm,
            "isRemoved" to isRemoved,
            "totalTokensInPool" to BigInteger.TEN.pow(19),
            "rewards" to BigInteger.valueOf(5),
            "rewardsToBeDistributed" to BigInteger.valueOf(6)
        )
    )

    private fun farms(vararg farms: Pair<Pair<String, String>, Any>) {
        bulkValues[SoraRuntime.prefix(PALLET, "Pools")] = farms.groupBy({ it.first }, { it.second })
            .map { (key, structs) ->
                SoraRuntime.key(PALLET, "Pools", code(key.first), code(key.second)) to
                    SoraRuntime.encodeValue(PALLET, "Pools", structs)
            }
            .toMap()
    }

    private fun rewardTokens(farmsMultiplier: BigInteger = BigInteger.TWO) {
        val type = SoraRuntime.valueType(PALLET, "TokenInfos")
        val info = SoraRuntime.struct(
            type,
            mapOf(
                "farmsTotalMultiplier" to farmsMultiplier,
                "stakingTotalMultiplier" to BigInteger.valueOf(4),
                "tokenPerBlock" to BigInteger.TEN.pow(18),
                "farmsAllocation" to BigInteger("600000000000000000"),
                "stakingAllocation" to BigInteger("200000000000000000"),
                "teamAllocation" to BigInteger("200000000000000000"),
                "teamAccount" to ByteArray(32) { 3 }
            )
        )
        bulkValues[SoraRuntime.prefix(PALLET, "TokenInfos")] = mapOf(
            SoraRuntime.key(PALLET, "TokenInfos", code(PSWAP_ID)) to SoraRuntime.encode(type, info),
            SoraRuntime.key(PALLET, "TokenInfos", code(VAL_ID)) to null
        )
    }

    private fun position(
        pool: String = VAL_ID,
        isFarm: Boolean = true,
        pooled: BigInteger = BigInteger("5000000000000000000"),
        rewards: BigInteger = BigInteger("2000000000000000000")
    ) = SoraRuntime.struct(
        SoraRuntime.vecElement(PALLET, "UserInfos"),
        mapOf(
            "baseAsset" to code(XOR_ID),
            "poolAsset" to code(pool),
            "rewardAsset" to code(PSWAP_ID),
            "isFarm" to isFarm,
            "pooledTokens" to pooled,
            "rewards" to rewards
        )
    )

    private fun positions(vararg positions: Any) {
        node.storage[SoraRuntime.key(PALLET, "UserInfos", USER_ACCOUNT)] =
            SoraRuntime.encodeValue(PALLET, "UserInfos", positions.toList())
    }

    private companion object {
        const val PALLET = "DemeterFarmingPlatform"
        const val META_ID = 7L
        const val TX_HASH = "0xd3e7"
        val USER_ACCOUNT = ByteArray(32) { 7 }
        val USER_ADDRESS = SoraRuntime.address(USER_ACCOUNT)
    }
}
