package jp.co.soramitsu.staking.impl.scenarios.relaychain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ternoaChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * An Asset Hub unbonding period depends on the account: a nominator whom the runtime does not slash unbonds in
 * NominatorFastUnbondDuration eras, anyone else in BondingDuration. The period shown follows the selected wallet
 * and an unreadable period is an error, never a period of zero hours.
 */
class AssetHubLockupPeriodTest {
    private val fastAccount = ByteArray(32) { 1 }
    private val fullAccount = ByteArray(32) { 2 }

    private fun wallet(accountId: ByteArray): MetaAccount = mock {
        on { substrateAccountId } doReturn accountId
        on { chainAccounts } doReturn emptyMap()
    }

    private inner class Fixture(val chainId: String) {
        val native = mock<CoreAsset> {
            on { id } doReturn "native"
            on { this.chainId } doReturn chainId
            on { isUtility } doReturn true
            on { staking } doReturn CoreAsset.StakingType.RELAYCHAIN
        }
        val chain = mock<Chain> {
            on { id } doReturn chainId
            on { ecosystem } doReturn Ecosystem.Substrate
            on { assets } doReturn listOf(native)
        }
        val wallets = MutableStateFlow(wallet(fastAccount))
        val accounts = mock<AccountRepository> {
            on { selectedMetaAccountFlow() } doReturn wallets
        }
        val repository = mock<StakingRelayChainScenarioRepository>()
        val constants = mock<StakingConstantsRepository>()
        val shared = mock<StakingSharedState> {
            on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(chain, native))
        }
        val interactor = StakingRelayChainScenarioInteractor(
            stakingInteractor = mock<StakingInteractor>(),
            accountRepository = accounts,
            walletRepository = mock(),
            stakingConstantsRepository = constants,
            stakingRelayChainScenarioRepository = repository,
            stakingRewardsRepository = mock(),
            factory = mock(),
            stakingSharedState = shared,
            identityRepository = mock(),
            payoutRepository = mock(),
            walletConstants = mock(),
            chainRegistry = mock(),
            stakingTotalRewardDao = mock(),
            assetCache = mock()
        )

        suspend fun setUp() {
            // A 24-hour era: 2400 blocks per session, 6 sessions, 6 s blocks.
            whenever(repository.sessionLength(chainId)).thenReturn(2400.toBigInteger())
            whenever(repository.eraLength(chainId)).thenReturn(6.toBigInteger())
            whenever(repository.blockCreationTime(chainId)).thenReturn(6000.toBigInteger())
            whenever(repository.legacyElectedExposuresInActiveEra(chainId)).thenReturn(flowOf(emptyMap()))
            whenever(repository.minimumActiveStake(chainId)).thenReturn(BigInteger.ONE)
            whenever(repository.minimumNominatorBond(any())).thenReturn(BigInteger.ONE)
            whenever(repository.assetHubUnbondingEras(chainId, fastAccount)).thenReturn(2.toBigInteger())
            whenever(repository.assetHubUnbondingEras(chainId, fullAccount)).thenReturn(28.toBigInteger())
            whenever(constants.maxRewardedNominatorPerValidator(chainId)).thenReturn(null)
            whenever(shared.chain()).thenReturn(chain)
        }
    }

    @Test
    fun `wallet change refreshes the Hub unbonding period`() = runTest {
        val f = Fixture(AssetHubStaking.POLKADOT).apply { setUp() }
        val periods = mutableListOf<Int>()
        val collection = launch {
            f.interactor.observeNetworkInfoState().take(2).toList().mapTo(periods) { (it as NetworkInfo.RelayChain).lockupPeriodInHours }
        }
        yield()
        f.wallets.value = wallet(fullAccount)
        collection.join()
        assertEquals(listOf(48, 672), periods)
    }

    @Test
    fun `unreadable Hub period is an error rather than no period`() = runTest {
        val f = Fixture(AssetHubStaking.KUSAMA).apply { setUp() }
        whenever(f.repository.assetHubUnbondingEras(AssetHubStaking.KUSAMA, fastAccount)).thenThrow(IllegalStateException("no runtime"))
        assertTrue(runCatching { f.interactor.observeNetworkInfoState().first() }.isFailure)
        assertTrue(runCatching { f.interactor.getLockupPeriodInHours() }.isFailure)
    }

    @Test
    fun `wallet without a Hub account cannot inherit another wallet's period`() = runTest {
        val f = Fixture(AssetHubStaking.POLKADOT).apply { setUp() }
        f.wallets.value = mock { on { chainAccounts } doReturn emptyMap() }
        assertTrue(runCatching { f.interactor.observeNetworkInfoState().first() }.isFailure)
    }

    @Test
    fun `direct lookup uses the selected wallet's account`() = runTest {
        val f = Fixture(AssetHubStaking.POLKADOT).apply { setUp() }
        whenever(f.accounts.getSelectedMetaAccount()).thenReturn(wallet(fullAccount))
        assertEquals(672, f.interactor.getLockupPeriodInHours())
    }

    @Test
    fun `other networks keep the bonding duration and a missing one still reads as zero`() = runTest {
        val f = Fixture(ternoaChainId).apply { setUp() }
        whenever(f.constants.lockupPeriodInEras(ternoaChainId)).thenReturn(7.toBigInteger())
        assertEquals(168, (f.interactor.observeNetworkInfoState().first() as NetworkInfo.RelayChain).lockupPeriodInHours)
        whenever(f.constants.lockupPeriodInEras(ternoaChainId)).thenThrow(IllegalStateException("no constant"))
        assertEquals(0, (f.interactor.observeNetworkInfoState().first() as NetworkInfo.RelayChain).lockupPeriodInHours)
    }

    private companion object {
        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }
    }
}
