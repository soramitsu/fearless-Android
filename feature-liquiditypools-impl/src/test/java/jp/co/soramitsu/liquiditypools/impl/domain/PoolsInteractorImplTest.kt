package jp.co.soramitsu.liquiditypools.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.liquiditypools.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.liquiditypools.data.PoolDataDto
import jp.co.soramitsu.liquiditypools.data.PoolsRepository
import jp.co.soramitsu.liquiditypools.domain.LiquidityMutationAction
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class PoolsInteractorImplTest {

    private val chain = mockk<Chain>(relaxed = true) {
        every { id } returns soraMainChainId
        every { ecosystem } returns Ecosystem.Substrate
        every { addressPrefix } returns 69
        every { externalApi } returns null
    }
    private val metaAccount = mockk<MetaAccount>(relaxed = true) {
        every { substrateAccountId } returns ACCOUNT
    }
    private val repository = mockk<PoolsRepository>(relaxed = true) {
        every { poolsChainId } returns soraMainChainId
    }
    private val accountRepository = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns metaAccount
        coEvery { getChain(soraMainChainId) } returns chain
        coEvery { getSelectedAccount(soraMainChainId) } returns Account(ADDRESS, null, "0x07", CryptoType.SR25519, 0)
    }
    private val explorer = mockk<BlockExplorerManager> {
        coEvery { syncSbApy() } returns Unit
        coEvery { getApy("reserve") } returns 12.5
        coEvery { getApy("missing") } returns null
    }
    private val chainsRepository = mockk<ChainsRepository> {
        coEvery { getChain(soraMainChainId) } returns chain
    }
    private val interactor = PoolsInteractorImpl(repository, accountRepository, explorer, chainsRepository, Dispatchers.Unconfined)

    private val pool = CommonPoolData(
        BasicPoolData(xor, valToken, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, "reserve"),
        null
    )

    @Test
    fun `pool subscriptions follow the selected SORA account`() = runBlocking {
        every { repository.subscribePools(ADDRESS) } returns flowOf(listOf(pool))
        every { repository.subscribePools("other") } returns flowOf(emptyList())
        coEvery { repository.subscribePool(ADDRESS, XOR_ID, VAL_ID) } returns flowOf(pool)

        assertEquals(soraMainChainId, interactor.poolsChainId)
        assertEquals(listOf(pool), interactor.subscribePoolsCacheCurrentAccount().first())
        assertEquals(emptyList<CommonPoolData>(), interactor.subscribePoolsCacheOfAccount("other").first())
        assertEquals(pool, interactor.getPoolData(XOR_ID, VAL_ID).first())
    }

    @Test
    fun `reads and fees are delegated for the pools chain`() = runBlocking {
        val dto = PoolDataDto(XOR_ID, VAL_ID, BigInteger.ONE, BigInteger.TWO, BigInteger.TEN, BigInteger.ONE, "reserve")
        coEvery { repository.getBasicPools(soraMainChainId) } returns listOf(pool.basic)
        coEvery { repository.getUserPoolData(soraMainChainId, ADDRESS, XOR_ID, any()) } returns dto
        coEvery {
            repository.calcAddLiquidityNetworkFee(soraMainChainId, ADDRESS, xor, valToken, BigDecimal.ONE, BigDecimal.TEN, true, false, 0.5)
        } returns BigDecimal("0.07")
        coEvery { repository.calcRemoveLiquidityNetworkFee(soraMainChainId, xor, valToken) } returns BigDecimal("0.03")
        coEvery { repository.mutationCapabilityReason(soraMainChainId, LiquidityMutationAction.Remove) } returns "off"

        assertEquals(listOf(pool.basic), interactor.getBasicPools())
        assertEquals(dto, interactor.getUserPoolData(soraMainChainId, ADDRESS, XOR_ID, byteArrayOf(1)))
        assertEquals(
            BigDecimal("0.07"),
            interactor.calcAddLiquidityNetworkFee(soraMainChainId, ADDRESS, xor, valToken, BigDecimal.ONE, BigDecimal.TEN, true, false, 0.5)
        )
        assertEquals(BigDecimal("0.03"), interactor.calcRemoveLiquidityNetworkFee(xor, valToken))
        assertEquals("off", interactor.mutationCapabilityReason(LiquidityMutationAction.Remove))
        assertEquals(12.5, interactor.getSbApy("reserve")!!, 0.0)
    }

    @Test
    fun `pair is enabled when the dex of the base token has a path to the target`() = runBlocking {
        coEvery { repository.getPoolBaseTokenDexId(soraMainChainId, XOR_ID) } returns 3
        coEvery { repository.isPairAvailable(soraMainChainId, XOR_ID, VAL_ID, 3) } returns true

        assertTrue(interactor.isPairEnabled(XOR_ID, VAL_ID))
    }

    @Test
    fun `mutations return the submitted hash, surface failures and treat no result as empty`() = runBlocking {
        // MockK cannot answer a nullable kotlin.Result, so the two mutations come from a delegating stub.
        val addCalls = mutableListOf<String>()
        val removeResults = ArrayDeque(listOf(Result.success("0xremove"), Result.failure(IllegalStateException("rejected")), null))
        val submitting = object : PoolsRepository by repository {
            override suspend fun observeAddLiquidity(
                chainId: String,
                address: String,
                tokenBase: jp.co.soramitsu.core.models.Asset,
                tokenTarget: jp.co.soramitsu.core.models.Asset,
                amountBase: BigDecimal,
                amountTarget: BigDecimal,
                pairEnabled: Boolean,
                pairPresented: Boolean,
                slippageTolerance: Double
            ): Result<String>? {
                addCalls += "$chainId $address ${tokenBase.symbol}/${tokenTarget.symbol} $amountBase/$amountTarget $pairEnabled $pairPresented $slippageTolerance"
                return Result.success("0xadd")
            }

            override suspend fun observeRemoveLiquidity(
                chainId: String,
                tokenBase: jp.co.soramitsu.core.models.Asset,
                tokenTarget: jp.co.soramitsu.core.models.Asset,
                markerAssetDesired: BigDecimal,
                firstAmountMin: BigDecimal,
                secondAmountMin: BigDecimal
            ): Result<String>? = removeResults.removeFirst()
        }
        val interactor = PoolsInteractorImpl(submitting, accountRepository, explorer, chainsRepository, Dispatchers.Unconfined)

        assertEquals("0xadd", interactor.observeAddLiquidity(soraMainChainId, xor, valToken, BigDecimal.ONE, BigDecimal.TEN, true, false, 0.5))
        assertEquals(listOf("$soraMainChainId $ADDRESS XOR/VAL 1/10 true false 0.5"), addCalls)
        assertEquals("0xremove", remove(interactor))
        assertEquals("rejected", runCatching { remove(interactor) }.exceptionOrNull()?.message)
        assertEquals("", remove(interactor))
    }

    @Test
    fun `adding liquidity without a substrate account fails before the repository`() = runBlocking {
        every { metaAccount.substrateAccountId } returns null

        val failure = runCatching {
            interactor.observeAddLiquidity(soraMainChainId, xor, valToken, BigDecimal.ONE, BigDecimal.TEN, true, true, 0.5)
        }.exceptionOrNull()

        assertEquals("There is no substrate account in current metaAccount", failure?.message)
        coVerify(exactly = 0) { repository.observeAddLiquidity(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sync refreshes basic pools, account pools and strategic bonus yields`() = runBlocking {
        interactor.syncPools()
        interactor.updateAccountPools()

        coVerify(exactly = 1) { repository.updateBasicPools(soraMainChainId) }
        coVerify(exactly = 2) { repository.updateAccountPools(soraMainChainId, ADDRESS) }
        coVerify(exactly = 1) { explorer.syncSbApy() }
        assertNull(interactor.getSbApy("missing"))
    }

    private suspend fun remove(interactor: PoolsInteractorImpl) = interactor.observeRemoveLiquidity(
        soraMainChainId, xor, valToken, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO
    )

    private companion object {
        val ACCOUNT = ByteArray(32) { 7 }
        val ADDRESS = ACCOUNT.toAddress(69.toShort())
    }
}
