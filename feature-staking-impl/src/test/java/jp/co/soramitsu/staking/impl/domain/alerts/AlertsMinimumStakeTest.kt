package jp.co.soramitsu.staking.impl.domain.alerts

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * On a Hub's first load or an era change the ledger and the era can arrive before the exposures are cached. If the
 * `MinimumActiveStake` read fails then, the minimum is unknown: the staking screen gets its alerts without a
 * minimum-stake alert instead of closing.
 */
class AlertsMinimumStakeTest {
    @Test
    fun `an unknown minimum raises no alert and no crash`() = runTest {
        val chain = mock<Chain> {
            on { id } doReturn AssetHubStaking.POLKADOT
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        val dot = mock<CoreAsset> {
            on { id } doReturn "dot"
            on { chainId } doReturn AssetHubStaking.POLKADOT
            on { precision } doReturn 10
            on { staking } doReturn CoreAsset.StakingType.RELAYCHAIN
        }
        val account = ByteArray(32) { 6 }
        val asset = Asset.createEmpty(dot, 7, account, minSupportedVersion = null)
        val repository = mock<StakingRelayChainScenarioRepository> {
            on { legacyElectedExposuresInActiveEra(AssetHubStaking.POLKADOT) } doReturn flowOf(emptyMap())
            on { observeActiveEraIndex(AssetHubStaking.POLKADOT) } doReturn flowOf(100.toBigInteger())
        }
        whenever(repository.minimumNominatorBond(any())).thenReturn(BigInteger.ONE)
        whenever(repository.minimumActiveStake(AssetHubStaking.POLKADOT)).thenReturn(null)
        whenever(repository.isLegacyErasStakersSchema(AssetHubStaking.POLKADOT)).thenReturn(false)
        val shared = mock<StakingSharedState> {
            on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(chain, dot))
        }
        val wallet = mock<MetaAccount> { on { id } doReturn 7L }
        val accounts = mock<AccountRepository>()
        whenever(accounts.getSelectedMetaAccount()).thenReturn(wallet)
        val wallets = mock<WalletRepository> {
            on { assetFlow(any(), any(), any(), anyOrNull()) } doReturn flowOf(asset)
        }
        val alerts = AlertsInteractor(repository, mock<StakingConstantsRepository>(), shared, wallets, accounts)

        val produced = alerts.getAlertsFlow(StakingState.Stash.None(chain, account, account, account)).first()

        assertTrue(produced.toString(), produced.none { it is Alert.BondMoreTokens })
    }
}
