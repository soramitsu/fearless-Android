package jp.co.soramitsu.staking.impl.domain.alerts

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
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
 * On chains with paged exposures (Polkadot, Kusama, Westend) "Change your validators" says "None of your validators
 * have been elected by the network". It compared account ids as ByteArray references, which never match, so the alert
 * never appeared; it must appear exactly when none of the nominated validators is elected.
 */
class AlertsChangeValidatorsTest {

    private val chainId = "relay"
    private val stash = ByteArray(32) { 6 }
    private val myValidator = ByteArray(32) { 1 }
    private val otherValidator = ByteArray(32) { 2 }
    private val activeEra = 100.toBigInteger()

    @Test
    fun `no elected validator among the nominations asks to change validators`() = runTest {
        val alerts = alerts(elected = listOf(otherValidator), submittedInEra = activeEra - BigInteger.ONE)

        assertTrue(alerts.toString(), alerts.any { it is Alert.ChangeValidators })
    }

    @Test
    fun `an elected nominated validator raises no change alert`() = runTest {
        val alerts = alerts(elected = listOf(otherValidator, myValidator), submittedInEra = activeEra - BigInteger.ONE)

        assertFalse(alerts.toString(), alerts.any { it is Alert.ChangeValidators })
    }

    @Test
    fun `nominations waiting for the next era raise no change alert`() = runTest {
        val alerts = alerts(elected = listOf(otherValidator), submittedInEra = activeEra)

        assertFalse(alerts.toString(), alerts.any { it is Alert.ChangeValidators })
        assertTrue(alerts.toString(), alerts.any { it is Alert.WaitingForNextEra })
    }

    private suspend fun alerts(elected: List<ByteArray>, submittedInEra: BigInteger): List<Alert> {
        val chain = mock<Chain> {
            on { id } doReturn chainId
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        val dot = mock<CoreAsset> {
            on { id } doReturn "dot"
            on { chainId } doReturn chainId
            on { precision } doReturn 10
            on { staking } doReturn CoreAsset.StakingType.RELAYCHAIN
        }
        val asset = Asset.createEmpty(dot, 7, stash, minSupportedVersion = null)
        // Fresh copies: the node's answer and the cached state never share array instances.
        val stateNominations = Nominations(listOf(myValidator.copyOf()), submittedInEra, suppressed = false)
        val remoteNominations = Nominations(listOf(myValidator.copyOf()), submittedInEra, suppressed = false)
        val exposures = elected.associate { validator ->
            validator.toHexString() to LegacyExposure(BigInteger.TEN, BigInteger.ONE, listOf(IndividualExposure(ByteArray(32) { 9 }, BigInteger.ONE)))
        }
        val repository = mock<StakingRelayChainScenarioRepository> {
            on { legacyElectedExposuresInActiveEra(chainId) } doReturn flowOf(exposures)
            on { observeActiveEraIndex(chainId) } doReturn flowOf(activeEra)
        }
        whenever(repository.minimumNominatorBond(any())).thenReturn(BigInteger.ONE)
        whenever(repository.minimumActiveStake(chainId)).thenReturn(null)
        whenever(repository.isLegacyErasStakersSchema(chainId)).thenReturn(false)
        whenever(repository.getRemoteAccountNominations(any(), any())).thenReturn(remoteNominations)
        val shared = mock<StakingSharedState> {
            on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(chain, dot))
        }
        val wallet = mock<MetaAccount> { on { id } doReturn 7L }
        val accounts = mock<AccountRepository>()
        whenever(accounts.getSelectedMetaAccount()).thenReturn(wallet)
        val wallets = mock<WalletRepository> {
            on { assetFlow(any(), any(), any(), anyOrNull()) } doReturn flowOf(asset)
        }
        val interactor = AlertsInteractor(repository, mock<StakingConstantsRepository>(), shared, wallets, accounts)

        return interactor.getAlertsFlow(StakingState.Stash.Nominator(chain, stash, stash, stash, stateNominations)).first()
    }
}
