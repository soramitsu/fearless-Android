package jp.co.soramitsu.wallet.impl.di

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.RegistryConnectionFixture
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * The cross-chain balance reader refreshes the origin network's balances before a bridge quote. It reads like
 * discovery: a network background sync does not keep is connected for that read only and disconnected after,
 * instead of staying connected until the process ends.
 */
class ReviewedBridgeBalanceReaderConnectionTest {

    private val account = ByteArray(32) { 3 }
    private val wallet = mockk<MetaAccount>(relaxed = true)
    private val interactor = mockk<WalletInteractor> { coEvery { getSelectedMetaAccount() } returns wallet }

    @Test
    fun `origin outside background sync is connected for the read and disconnected after`() = runBlocking {
        RegistryConnectionFixture().use { f ->
            val connectedDuringRead = mutableListOf<String>()
            val reader = reader(f, f.unrankedChain) { connectedDuringRead += f.configured }

            val snapshot = reader.refreshAndRead(f.unrankedChain, asset("xor"), asset("val"), account)

            assertEquals(BigInteger.TEN, snapshot.transferAssetBalance)
            assertEquals(BigInteger.valueOf(4), snapshot.feeAssetBalance)
            assertEquals(listOf("a", "b"), connectedDuringRead)
            assertEquals(listOf("b"), f.disconnected)
        }
    }

    @Test
    fun `origin background sync keeps is read on its connection and left connected`() = runBlocking {
        RegistryConnectionFixture().use { f ->
            val reader = reader(f, f.rankedChain) {}

            reader.refreshAndRead(f.rankedChain, asset("xor"), asset("val"), account)

            assertEquals(listOf("a"), f.configured)
            assertEquals(emptyList<String>(), f.disconnected)
        }
    }

    private fun reader(f: RegistryConnectionFixture, origin: Chain, duringRead: () -> Unit) =
        WalletFeatureModule().provideReviewedBridgeBalanceReader(
            balanceLoaderProvider = object : BalanceLoader.Provider {
                override fun invoke(chain: Chain): BalanceLoader = mockk {
                    coEvery { loadBalance(setOf(wallet)) } coAnswers {
                        duringRead()
                        listOf(update(origin, "xor", free = 10), update(origin, "val", free = 6, frozen = 2))
                    }
                }
            },
            walletInteractor = interactor,
            chainRegistry = f.registry
        )

    private fun update(origin: Chain, assetId: String, free: Long, frozen: Long = 0) = AssetBalanceUpdateItem(
        metaId = 1L,
        chainId = origin.id,
        accountId = account,
        id = assetId,
        freeInPlanks = BigInteger.valueOf(free),
        miscFrozenInPlanks = BigInteger.valueOf(frozen)
    )

    private fun asset(assetId: String) = mockk<CoreAsset> { every { id } returns assetId }
}
