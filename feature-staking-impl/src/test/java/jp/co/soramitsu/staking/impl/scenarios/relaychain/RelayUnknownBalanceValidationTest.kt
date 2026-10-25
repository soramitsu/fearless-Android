package jp.co.soramitsu.staking.impl.scenarios.relaychain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.unbond.CrossExistentialValidation
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Answers
import org.mockito.kotlin.*

/** Exercises the real balance-only scenario methods without touching a network or database. */
class RelayUnknownBalanceValidationTest {
    private val scenario = mock<StakingRelayChainScenarioInteractor>(defaultAnswer = Answers.CALLS_REAL_METHODS)
    private val asset = mock<Asset>()
    private val stateChain = mock<Chain> {
        on { ecosystem } doReturn Ecosystem.Substrate
        on { addressPrefix } doReturn 42
    }
    private val state = StakingState.NonStash(stateChain, ByteArray(32) { 1 })
    private val unbond = UnbondValidationPayload(state, BigDecimal.ZERO, BigDecimal.ONE, asset)

    @Test fun missingBondedAmountRemainsUnknownAndCannotAuthorizeUnbond() = runTest {
        whenever(asset.bonded).thenReturn(null)
        assertNull(scenario.getUnstakeAvailableAmount(asset, null))
        assertFalse(scenario.checkEnoughToUnbondValidation(unbond))
        assertFalse(scenario.checkCrossExistentialValidation(unbond))
    }

    @Test fun explicitZeroAndPositiveBondedBalancesKeepTheirMeaning() = runTest {
        whenever(asset.bonded).thenReturn(BigDecimal.ZERO)
        assertEquals(BigDecimal.ZERO, scenario.getUnstakeAvailableAmount(asset, null))
        assertFalse(scenario.checkEnoughToUnbondValidation(unbond))
        whenever(asset.bonded).thenReturn(BigDecimal.TEN)
        assertEquals(BigDecimal.TEN, scenario.getUnstakeAvailableAmount(asset, null))
        assertTrue(scenario.checkEnoughToUnbondValidation(unbond))
    }

    @Test fun missingUnbondingCannotAuthorizeRebondAndKnownAmountRecovers() = runTest {
        val payload = RebondValidationPayload(asset, BigDecimal.ZERO, BigDecimal.ONE)
        whenever(asset.unbonding).thenReturn(null)
        assertNull(scenario.getRebondAvailableAmount(asset, BigDecimal.ONE))
        assertFalse(scenario.checkEnoughToRebondValidation(payload))
        whenever(asset.unbonding).thenReturn(BigDecimal.ONE)
        assertTrue(scenario.checkEnoughToRebondValidation(payload))
    }

    @Test fun unknownBondedAmountIsAnErrorAndCannotBecomeAnOverridableWarning() = runTest {
        whenever(asset.bonded).thenReturn(null)
        val result = CrossExistentialValidation(scenario).validate(unbond)
        assertTrue(result is ValidationStatus.NotValid)
        result as ValidationStatus.NotValid
        assertEquals(DefaultFailureLevel.ERROR, result.level)
        assertSame(UnbondValidationFailure.NotEnoughBonded, result.reason)
    }
    @Test fun actualRelayFlowPublishesUnknownBetweenKnownBondedBalances() = runTest {
        val config = mock<jp.co.soramitsu.core.models.Asset> {
            on { chainId } doReturn "missing-chain"
            on { id } doReturn "native"
            on { precision } doReturn 2
            on { symbol } doReturn "UNIT"
        }
        val known = Asset.createEmpty(config, 7, byteArrayOf(1), minSupportedVersion = null)
            .copy(bondedInPlanksState = AssetAmountState.Known(BigInteger.valueOf(100)))
        val unknown = known.copy(bondedInPlanksState = AssetAmountState.Unavailable("invalid-cache-number"))
        val interactor = mock<StakingInteractor> {
            on { currentAssetFlow() } doReturn flowOf(known, unknown, known)
        }
        val selectedAccount = mock<MetaAccount>()
        val accounts = mock<AccountRepository> { onBlocking { getSelectedMetaAccount() } doReturn selectedAccount }
        val shared = mock<StakingSharedState> {
            on { selectionItem } doReturn flowOf(StakingAssetSelection.RelayChainStaking("missing-chain", "native"))
        }
        // No current configured asset means the optional ledger refresh returns without a network call.
        val chain = mock<Chain> { on { assetsById } doReturn emptyMap() }
        val registry = mock<ChainRegistry> { onBlocking { getChain(any()) } doReturn chain }
        val actual = StakingRelayChainScenarioInteractor(interactor, accounts, mock(), mock(), mock(), mock(), mock(), shared, mock(), mock(), mock(), registry, mock(), mock())
        val values = actual.getStakingBalanceFlow(null).toList()
        assertEquals(3, values.size)
        assertEquals(BigDecimal("1.00"), values[0].staked.amount)
        assertNull(values[1].staked.amount)
        assertEquals("—", values[1].staked.token)
        assertNull(values[1].staked.fiat)
        assertEquals(BigDecimal("1.00"), values[2].staked.amount)
    }

}
