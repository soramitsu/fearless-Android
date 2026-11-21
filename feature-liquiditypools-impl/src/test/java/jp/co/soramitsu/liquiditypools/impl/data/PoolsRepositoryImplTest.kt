package jp.co.soramitsu.liquiditypools.impl.data

import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.PoolDao
import jp.co.soramitsu.coredb.model.BasicPoolLocal
import jp.co.soramitsu.coredb.model.UserPoolJoinedLocalNullable
import jp.co.soramitsu.coredb.model.UserPoolLocal
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.liquiditypools.data.PoolDataDto
import jp.co.soramitsu.liquiditypools.domain.LiquidityMutationAction
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.PSWAP_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.UNKNOWN_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.coreAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.pswap
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.data.SoraRuntime.code
import jp.co.soramitsu.liquiditypools.impl.data.SoraRuntime.encodeValue
import jp.co.soramitsu.liquiditypools.impl.data.SoraRuntime.key
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class PoolsRepositoryImplTest {

    private val node = FakeSoraNode(listOf(xor, valToken, pswap))
    private val builder = mockk<ExtrinsicBuilder>(relaxed = true)
    private val submittedBuilder = mockk<ExtrinsicBuilder>(relaxed = true)
    private val estimateBatchFlags = mutableListOf<Boolean>()
    private val submitBatchFlags = mutableListOf<Boolean>()
    private val extrinsicService = mockk<ExtrinsicService> {
        coEvery { estimateFee(any(), any<ByteArray>(), any(), any(), any(), any()) } coAnswers {
            estimateBatchFlags += thirdArg<Boolean>()
            arg<suspend ExtrinsicBuilder.() -> Unit>(5).invoke(builder)
            FEE_PLANKS
        }
        coEvery { submitExtrinsic(any(), any(), any(), any(), any(), any()) } coAnswers {
            submitBatchFlags += thirdArg<Boolean>()
            arg<suspend ExtrinsicBuilder.() -> Unit>(5).invoke(submittedBuilder)
            Result.success(TX_HASH)
        }
    }
    private val metaAccount = mockk<MetaAccount>(relaxed = true) {
        every { substrateAccountId } returns USER_ACCOUNT
    }
    private val accountRepository = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns metaAccount
    }
    private val poolDao = mockk<PoolDao>(relaxed = true)
    private val database = mockk<AppDatabase>()
    private val toggles = mockk<ProductFeatureToggleStore> {
        every { polkaswapMutationsEnabled } returns true
    }
    private val authorizer = mockk<SoraDeFiMutationAuthorizer>()

    private val repository = PoolsRepositoryImpl(
        extrinsicService = extrinsicService,
        chainRegistry = node.chainRegistry,
        accountRepository = accountRepository,
        poolDao = poolDao,
        db = database,
        featureToggleStore = toggles,
        mutationAuthorizer = authorizer
    )

    private var roomMocked = false

    @After
    fun tearDown() {
        if (roomMocked) unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `pair availability asks the liquidity proxy for the dex path`() = runBlocking {
        node.pathAvailable = false

        val available = repository.isPairAvailable(soraMainChainId, XOR_ID, VAL_ID, 1)

        assertEquals(false, available)
        val request = node.requestsOf("liquidityProxy_isPathAvailable").single()
        assertEquals(listOf(1, XOR_ID, VAL_ID), request.params)
    }

    @Test
    fun `capability reason asks the authorizer for the liquidity call of each action`() = runBlocking {
        val calls = mutableListOf<Set<SoraRuntimeCall>>()
        coEvery { authorizer.capabilityReason(SoraMutationFeature.Liquidity, soraMainChainId, capture(calls)) } returnsMany
            listOf(null, "Withdraw call missing")

        assertNull(repository.mutationCapabilityReason(soraMainChainId, LiquidityMutationAction.Add))
        assertEquals(
            "Withdraw call missing",
            repository.mutationCapabilityReason(soraMainChainId, LiquidityMutationAction.Remove)
        )
        assertEquals(
            listOf(setOf(SoraRuntimeCall("PoolXYK", "deposit_liquidity")), setOf(SoraRuntimeCall("PoolXYK", "withdraw_liquidity"))),
            calls
        )
    }

    @Test
    fun `cached basic pool is mapped onto the chain assets`() = runBlocking {
        coEvery { poolDao.getBasicPool(XOR_ID, VAL_ID) } returns local(XOR_ID, VAL_ID)
        coEvery { poolDao.getBasicPool(UNKNOWN_ID, VAL_ID) } returns local(UNKNOWN_ID, VAL_ID)
        coEvery { poolDao.getBasicPool(XOR_ID, UNKNOWN_ID) } returns local(XOR_ID, UNKNOWN_ID)
        coEvery { poolDao.getBasicPool(XOR_ID, PSWAP_ID) } returns null

        val pool = requireNotNull(repository.getBasicPool(soraMainChainId, XOR_ID, VAL_ID))

        assertEquals(xor, pool.baseToken)
        assertEquals(valToken, pool.targetToken)
        assertAmount("1000", pool.baseReserves)
        assertAmount("2000", pool.targetReserves)
        assertAmount("300", pool.totalIssuance)
        assertEquals("cached-reserve", pool.reserveAccount)
        assertNull(repository.getBasicPool(soraMainChainId, UNKNOWN_ID, VAL_ID))
        assertNull(repository.getBasicPool(soraMainChainId, XOR_ID, UNKNOWN_ID)!!.targetToken)
        assertNull(repository.getBasicPool(soraMainChainId, XOR_ID, PSWAP_ID))
    }

    @Test
    fun `basic pools read reserves, reserve account and issuance from storage`() = runBlocking {
        poolStorage(XOR_ID, VAL_ID)
        node.keys[key(POOL_XYK, "Reserves", code(XOR_ID))] = listOf(
            key(POOL_XYK, "Reserves", code(XOR_ID), code(VAL_ID)),
            key(POOL_XYK, "Reserves", code(XOR_ID), code(PSWAP_ID))
        )
        // XOR-PSWAP has reserves but no registered properties.
        node.storage[key(POOL_XYK, "Reserves", code(XOR_ID), code(PSWAP_ID))] =
            encodeValue(POOL_XYK, "Reserves", listOf(BASE_RESERVES, TARGET_RESERVES))

        val pools = repository.getBasicPools(soraMainChainId)

        assertEquals(2, pools.size)
        val xorVal = pools.single { it.targetToken == valToken }
        assertEquals(xor, xorVal.baseToken)
        assertAmount("1000", xorVal.baseReserves)
        assertAmount("2000", xorVal.targetReserves)
        assertAmount("300", xorVal.totalIssuance)
        assertEquals(SoraRuntime.address(RESERVE_ACCOUNT), xorVal.reserveAccount)
        val xorPswap = pools.single { it.targetToken == pswap }
        assertAmount("0", xorPswap.totalIssuance)
        assertEquals("", xorPswap.reserveAccount)
    }

    @Test
    fun `user pool data combines reserves, issuance and the account's provider balance`() = runBlocking {
        poolStorage(XOR_ID, VAL_ID)

        val data = repository.getUserPoolData(soraMainChainId, USER_ADDRESS, XOR_ID, VAL_ID.fromHex())

        assertEquals(
            PoolDataDto(
                baseAssetId = XOR_ID,
                assetId = VAL_ID,
                reservesFirst = BASE_RESERVES,
                reservesSecond = TARGET_RESERVES,
                totalIssuance = TOTAL_ISSUANCE,
                poolProvidersBalance = PROVIDED,
                reservesAccount = SoraRuntime.address(RESERVE_ACCOUNT)
            ),
            data
        )
    }

    @Test
    fun `user pool data is unavailable without reserves, properties or runtime`() = runBlocking {
        poolStorage(XOR_ID, VAL_ID)
        node.storage.remove(key(POOL_XYK, "Reserves", code(XOR_ID), code(VAL_ID)))
        assertNull(repository.getUserPoolData(soraMainChainId, USER_ADDRESS, XOR_ID, VAL_ID.fromHex()))

        poolStorage(XOR_ID, VAL_ID)
        node.storage.remove(key(POOL_XYK, "Properties", code(XOR_ID), code(VAL_ID)))
        assertNull(repository.getUserPoolData(soraMainChainId, USER_ADDRESS, XOR_ID, VAL_ID.fromHex()))

        coEvery { node.chainRegistry.getRuntimeOrNull(soraMainChainId) } returns null
        assertNull(repository.getUserPoolData(soraMainChainId, USER_ADDRESS, XOR_ID, VAL_ID.fromHex()))
    }

    @Test
    fun `provider balance defaults to zero when the account has no position`() = runBlocking {
        poolStorage(XOR_ID, VAL_ID)
        node.storage.remove(key(POOL_XYK, "PoolProviders", RESERVE_ACCOUNT, USER_ACCOUNT))

        val data = repository.getUserPoolData(soraMainChainId, USER_ADDRESS, XOR_ID, VAL_ID.fromHex())

        assertEquals(BigInteger.ZERO, data?.poolProvidersBalance)
    }

    @Test
    fun `base token dex id comes from the DEX manager registry`() = runBlocking {
        dexInfos()

        assertEquals(0, repository.getPoolBaseTokenDexId(soraMainChainId, XOR_ID))
        assertEquals(1, repository.getPoolBaseTokenDexId(soraMainChainId, PSWAP_ID))
        assertTrue(runCatching { repository.getPoolBaseTokenDexId(soraMainChainId, VAL_ID) }.isFailure)
    }

    @Test
    fun `add liquidity fee estimates a deposit with slippage minimums`() = runBlocking {
        dexInfos()

        val fee = repository.calcAddLiquidityNetworkFee(
            chainId = soraMainChainId,
            address = USER_ADDRESS,
            tokenBase = xor,
            tokenTarget = valToken,
            tokenBaseAmount = BigDecimal("2"),
            tokenTargetAmount = BigDecimal("4"),
            pairEnabled = true,
            pairPresented = true,
            slippageTolerance = 1.0
        )

        assertAmount("0.01", requireNotNull(fee))
        val args = slot<Map<String, Any?>>()
        verify { builder.call("PoolXYK", "deposit_liquidity", capture(args)) }
        verify(exactly = 0) { builder.call("PoolXYK", "initialize_pool", any()) }
        assertEquals(BigInteger.ZERO, args.captured["dex_id"])
        assertEquals(code(XOR_ID), args.captured["input_asset_a"])
        assertEquals(code(VAL_ID), args.captured["input_asset_b"])
        assertEquals(planks("2"), args.captured["input_a_desired"])
        assertEquals(planks("4"), args.captured["input_b_desired"])
        assertEquals(planks("1.98"), args.captured["input_a_min"])
        assertEquals(planks("3.96"), args.captured["input_b_min"])
    }

    @Test
    fun `remove liquidity fee needs both currencies and a substrate account`() = runBlocking {
        dexInfos()

        assertNull(repository.calcRemoveLiquidityNetworkFee(soraMainChainId, coreAsset("x", "X", null), valToken))
        assertNull(repository.calcRemoveLiquidityNetworkFee(soraMainChainId, xor, coreAsset("x", "X", null)))
        every { metaAccount.substrateAccountId } returns null
        assertNull(repository.calcRemoveLiquidityNetworkFee(soraMainChainId, xor, valToken))
        every { metaAccount.substrateAccountId } returns USER_ACCOUNT

        val fee = repository.calcRemoveLiquidityNetworkFee(soraMainChainId, xor, valToken)

        assertAmount("0.01", requireNotNull(fee))
        val args = slot<Map<String, Any?>>()
        verify { builder.call("PoolXYK", "withdraw_liquidity", capture(args)) }
        assertEquals(code(XOR_ID), args.captured["output_asset_a"])
        assertEquals(code(VAL_ID), args.captured["output_asset_b"])
        assertEquals(planks("1"), args.captured["marker_asset_desired"])
    }

    @Test
    fun `storage change exposes block and changes and rejects malformed results`() {
        val change = with(repository) {
            subscription(mapOf("block" to "0xb10c", "changes" to listOf(listOf("0xkey", "0xvalue")))).storageChange()
        }
        assertEquals("0xb10c", change.block)
        assertEquals("0xvalue", change.getSingleChange())

        listOf<Any>(
            "not a map",
            mapOf("changes" to emptyList<Any>()),
            mapOf("block" to "0xb10c")
        ).forEach { malformed ->
            val failure = runCatching { with(repository) { subscription(malformed).storageChange() } }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        }
    }

    @Test
    fun `account pools refresh stores every pool position in one transaction`() = runBlocking {
        poolStorage(XOR_ID, VAL_ID)
        poolStorage(XOR_ID, UNKNOWN_ID)
        node.keys[key(POOL_XYK, "AccountPools", USER_ACCOUNT)] = listOf(
            key(POOL_XYK, "AccountPools", USER_ACCOUNT, code(XOR_ID)),
            key(POOL_XYK, "AccountPools", USER_ACCOUNT, code(UNKNOWN_ID))
        )
        node.storage[key(POOL_XYK, "AccountPools", USER_ACCOUNT, code(XOR_ID))] =
            encodeValue(POOL_XYK, "AccountPools", listOf(code(VAL_ID), code(PSWAP_ID), code(UNKNOWN_ID)))
        node.storage[key(POOL_XYK, "AccountPools", USER_ACCOUNT, code(UNKNOWN_ID))] =
            encodeValue(POOL_XYK, "AccountPools", listOf(code(VAL_ID)))
        mockkStatic("androidx.room.RoomDatabaseKt")
        roomMocked = true
        val transaction = slot<suspend () -> Unit>()
        coEvery { database.withTransaction(capture(transaction)) } coAnswers { transaction.captured.invoke() }
        val basic = slot<List<BasicPoolLocal>>()
        val user = slot<List<UserPoolLocal>>()
        coEvery { poolDao.insertBasicPools(capture(basic)) } returns Unit
        coEvery { poolDao.insertUserPools(capture(user)) } returns Unit

        repository.updateAccountPools(soraMainChainId, USER_ADDRESS)

        coVerify { poolDao.clearTable(USER_ADDRESS) }
        // XOR-PSWAP has no reserves on the node; the unknown base and target assets are not chain assets.
        val stored = basic.captured.single()
        assertEquals(XOR_ID, stored.tokenIdBase)
        assertEquals(VAL_ID, stored.tokenIdTarget)
        assertAmount("1000", stored.reserveBase)
        assertAmount("2000", stored.reserveTarget)
        assertAmount("300", stored.totalIssuance)
        assertEquals(SoraRuntime.address(RESERVE_ACCOUNT), stored.reservesAccount)
        val position = user.captured.single()
        assertEquals(USER_ADDRESS, position.accountAddress)
        assertEquals(VAL_ID, position.userTokenIdTarget)
        assertAmount("30", position.poolProvidersBalance)
    }

    @Test
    fun `basic pools refresh replaces stale cached pools`() = runBlocking {
        dexInfos(secondBase = UNKNOWN_ID)
        poolStorage(XOR_ID, VAL_ID)
        node.keys[key(POOL_XYK, "Reserves", code(XOR_ID))] = listOf(
            key(POOL_XYK, "Reserves", code(XOR_ID), code(VAL_ID)),
            key(POOL_XYK, "Reserves", code(XOR_ID), code(PSWAP_ID))
        )
        val stale = local(XOR_ID, PSWAP_ID)
        coEvery { poolDao.getBasicPools() } returns listOf(stale, local(XOR_ID, VAL_ID))
        val inserted = slot<List<BasicPoolLocal>>()
        coEvery { poolDao.insertBasicPools(capture(inserted)) } returns Unit

        repository.updateBasicPools(soraMainChainId)

        coVerify { poolDao.deleteBasicPools(listOf(stale)) }
        val pool = inserted.captured.single()
        assertEquals(VAL_ID, pool.tokenIdTarget)
        assertAmount("1000", pool.reserveBase)
        assertAmount("300", pool.totalIssuance)
        assertEquals(SoraRuntime.address(RESERVE_ACCOUNT), pool.reservesAccount)
    }

    @Test
    fun `cached pools are mapped with the account share and unknown assets are dropped`() = runBlocking {
        val userPool = UserPoolLocal(XOR_ID, VAL_ID, USER_ADDRESS, BigDecimal("30"))
        every { poolDao.subscribeAllPools(USER_ADDRESS) } returns flowOf(
            listOf(
                UserPoolJoinedLocalNullable(userPool, local(XOR_ID, VAL_ID)),
                UserPoolJoinedLocalNullable(null, local(XOR_ID, PSWAP_ID)),
                UserPoolJoinedLocalNullable(null, local(XOR_ID, UNKNOWN_ID)),
                UserPoolJoinedLocalNullable(null, local(UNKNOWN_ID, VAL_ID))
            )
        )
        every { poolDao.subscribePool(USER_ADDRESS, XOR_ID, VAL_ID) } returns
            flowOf(UserPoolJoinedLocalNullable(userPool, local(XOR_ID, VAL_ID)))
        every { poolDao.subscribePool(USER_ADDRESS, XOR_ID, UNKNOWN_ID) } returns
            flowOf(UserPoolJoinedLocalNullable(null, local(XOR_ID, UNKNOWN_ID)))

        val pools = repository.subscribePools(USER_ADDRESS).first()
        val single = repository.subscribePool(USER_ADDRESS, XOR_ID, VAL_ID).first()
        val unknown = repository.subscribePool(USER_ADDRESS, XOR_ID, UNKNOWN_ID).toList()

        assertEquals(listOf(valToken, pswap), pools.map { it.basic.targetToken })
        assertNull(pools[1].user)
        val share = requireNotNull(pools[0].user)
        assertAmount("100", share.basePooled)
        assertAmount("200", share.targetPooled)
        assertEquals(10.0, share.poolShare, 0.0)
        assertAmount("30", share.poolProvidersBalance)
        assertEquals(pools[0], single)
        assertTrue(unknown.isEmpty())
    }

    @Test
    fun `add liquidity submits the revalidated deposit and returns its hash`() = runBlocking {
        dexInfos()
        authorize(listOf(xor, valToken))

        val result = observeAdd(pairPresented = true)

        assertEquals(TX_HASH, result?.getOrThrow())
        assertEquals(listOf(false), estimateBatchFlags)
        assertEquals(listOf(false), submitBatchFlags)
        val args = slot<Map<String, Any?>>()
        verify { submittedBuilder.call("PoolXYK", "deposit_liquidity", capture(args)) }
        assertEquals(planks("1"), args.captured["input_a_desired"])
        assertEquals(planks("0.995"), args.captured["input_a_min"])
        coVerify(exactly = 2) { authorizer.requireFreshBalances(any(), any(), any()) }
    }

    @Test
    fun `adding a new pair batches registration and initialization`() = runBlocking {
        dexInfos()
        authorize(listOf(xor, valToken))

        val result = observeAdd(pairPresented = false, pairEnabled = false)

        assertEquals(TX_HASH, result?.getOrThrow())
        assertEquals(listOf(true), submitBatchFlags)
        verify { submittedBuilder.call("TradingPair", "register", any()) }
        verify { submittedBuilder.call("PoolXYK", "initialize_pool", any()) }
        verify { submittedBuilder.call("PoolXYK", "deposit_liquidity", any()) }
    }

    @Test
    fun `add liquidity fails closed on bad slippage, another account, equal assets or a disabled switch`() =
        runBlocking {
        dexInfos()
        authorize(listOf(xor, valToken))
        assertFailure("Liquidity slippage must be at least zero and below 100 percent", observeAdd(slippage = 100.0))
        assertFailure("Liquidity account does not match the selected SORA account", observeAdd(address = "other"))

        authorize(listOf(xor, xor))
        assertFailure("Liquidity assets must be different", observeAdd())

        authorize(listOf(xor, valToken))
        every { toggles.polkaswapMutationsEnabled } returns false
        assertFailure("Polkaswap liquidity actions are temporarily disabled", observeAdd())
        assertTrue(submitBatchFlags.isEmpty())
    }

    @Test
    fun `remove liquidity checks the fresh position before submitting the withdrawal`() = runBlocking {
        dexInfos()
        poolStorage(XOR_ID, VAL_ID)
        authorize(listOf(xor, valToken))

        val result = observeRemove(marker = "10")

        assertEquals(TX_HASH, result?.getOrThrow())
        val args = slot<Map<String, Any?>>()
        verify { submittedBuilder.call("PoolXYK", "withdraw_liquidity", capture(args)) }
        assertEquals(planks("10"), args.captured["marker_asset_desired"])
        assertEquals(planks("1"), args.captured["output_a_min"])
    }

    @Test
    fun `remove liquidity fails closed on an oversized withdrawal, missing position or disabled switch`() =
        runBlocking {
        dexInfos()
        poolStorage(XOR_ID, VAL_ID)
        authorize(listOf(xor, valToken))

        assertFailure("Liquidity position balance is insufficient for this withdrawal", observeRemove(marker = "31"))
        assertFailure("Minimum liquidity output exceeds the fresh pool reserves", observeRemove(marker = "1", min = "5000"))
        every { toggles.polkaswapMutationsEnabled } returns false
        assertFailure("Polkaswap liquidity actions are temporarily disabled", observeRemove(marker = "1"))

        authorize(listOf(xor, xor))
        assertFailure("Liquidity assets must be different", observeRemove(marker = "1"))

        authorize(listOf(xor, valToken))
        node.storage.clear()
        assertFailure("Fresh SORA liquidity position is unavailable", observeRemove(marker = "1"))
    }

    private suspend fun observeAdd(
        pairPresented: Boolean = true,
        pairEnabled: Boolean = true,
        slippage: Double = 0.5,
        address: String = USER_ADDRESS
    ) = repository.observeAddLiquidity(
        chainId = soraMainChainId,
        address = address,
        tokenBase = xor,
        tokenTarget = valToken,
        amountBase = BigDecimal.ONE,
        amountTarget = BigDecimal("2"),
        pairEnabled = pairEnabled,
        pairPresented = pairPresented,
        slippageTolerance = slippage
    )

    private suspend fun observeRemove(marker: String, min: String = "1") = repository.observeRemoveLiquidity(
        chainId = soraMainChainId,
        tokenBase = xor,
        tokenTarget = valToken,
        markerAssetDesired = BigDecimal(marker),
        firstAmountMin = BigDecimal(min),
        secondAmountMin = BigDecimal.ONE
    )

    private fun authorize(assets: List<jp.co.soramitsu.core.models.Asset>) {
        val context = SoraMutationContext(
            chain = node.chain,
            accountId = USER_ACCOUNT,
            accountAddress = USER_ADDRESS,
            identity = SoraMutationIdentity(7, USER_ACCOUNT.toHexString()),
            assets = assets,
            feeAsset = xor
        )
        coEvery { authorizer.authorize(any(), any(), any(), any(), any()) } returns context
        coEvery { authorizer.requireFreshBalances(any(), any(), any()) } returns Unit
    }

    private fun assertFailure(message: String, result: Result<String>?) {
        assertEquals(message, result?.exceptionOrNull()?.message)
    }

    private fun poolStorage(base: String, target: String) {
        node.storage[key(POOL_XYK, "Reserves", code(base), code(target))] =
            encodeValue(POOL_XYK, "Reserves", listOf(BASE_RESERVES, TARGET_RESERVES))
        node.storage[key(POOL_XYK, "Properties", code(base), code(target))] =
            RESERVE_ACCOUNT.toHexString(true) + FEE_ACCOUNT.toHexString()
        node.storage[key(POOL_XYK, "TotalIssuances", RESERVE_ACCOUNT)] = encodeValue(POOL_XYK, "TotalIssuances", TOTAL_ISSUANCE)
        node.storage[key(POOL_XYK, "PoolProviders", RESERVE_ACCOUNT, USER_ACCOUNT)] =
            encodeValue(POOL_XYK, "PoolProviders", PROVIDED)
    }

    private fun dexInfos(secondBase: String = PSWAP_ID) {
        val infoType = SoraRuntime.valueType(DEX_MANAGER, "DEXInfos")
        val dex0 = key(DEX_MANAGER, "DEXInfos", BigInteger.ZERO)
        val dex1 = key(DEX_MANAGER, "DEXInfos", BigInteger.ONE)
        node.keys[SoraRuntime.prefix(DEX_MANAGER, "DEXInfos")] = listOf(dex0, dex1)
        node.storage[dex0] = SoraRuntime.encode(infoType, SoraRuntime.struct(infoType, mapOf("baseAssetId" to code(XOR_ID))))
        node.storage[dex1] = SoraRuntime.encode(infoType, SoraRuntime.struct(infoType, mapOf("baseAssetId" to code(secondBase))))
    }

    private fun subscription(result: Any) =
        SubscriptionChange("2.0", "state_storage", SubscriptionChange.Params(result, "subscription"))

    private fun local(base: String, target: String) = BasicPoolLocal(
        tokenIdBase = base,
        tokenIdTarget = target,
        reserveBase = BigDecimal("1000"),
        reserveTarget = BigDecimal("2000"),
        totalIssuance = BigDecimal("300"),
        reservesAccount = "cached-reserve"
    )

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertTrue("expected $expected but was $actual", BigDecimal(expected).compareTo(actual) == 0)
    }

    private fun planks(amount: String): BigInteger = BigDecimal(amount).movePointRight(18).toBigIntegerExact()

    private companion object {
        const val POOL_XYK = "PoolXYK"
        const val DEX_MANAGER = "DEXManager"
        const val TX_HASH = "0xfeed"
        val FEE_PLANKS: BigInteger = BigInteger.TEN.pow(16)
        val USER_ACCOUNT = ByteArray(32) { 7 }
        val RESERVE_ACCOUNT = ByteArray(32) { 9 }
        val FEE_ACCOUNT = ByteArray(32) { 11 }
        val USER_ADDRESS = SoraRuntime.address(USER_ACCOUNT)
        val BASE_RESERVES: BigInteger = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(1000))
        val TARGET_RESERVES: BigInteger = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(2000))
        val TOTAL_ISSUANCE: BigInteger = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(300))
        val PROVIDED: BigInteger = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(30))
    }
}
