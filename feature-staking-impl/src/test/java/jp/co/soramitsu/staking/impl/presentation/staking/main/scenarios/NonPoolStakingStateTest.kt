package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import java.math.BigInteger
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.Round
import jp.co.soramitsu.staking.impl.domain.model.NominatorStatus
import jp.co.soramitsu.staking.impl.domain.model.StashNoneStatus
import jp.co.soramitsu.staking.impl.domain.model.ValidatorStatus
import jp.co.soramitsu.staking.impl.presentation.staking.main.ManageStakeAction
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.MAX_STAKING_TIMER_MILLIS
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NonPoolStakingStateTest {

    @Test
    fun `relay manage actions preserve every legacy role and payout capability rule`() {
        val allActions = ManageStakeAction.entries.toSet()

        assertEquals(allActions, relayManageActions(RelayStakeKind.NOMINATOR, payoutsSupported = true))
        assertEquals(
            allActions - ManageStakeAction.PAYOUTS,
            relayManageActions(RelayStakeKind.NOMINATOR, payoutsSupported = false)
        )
        assertEquals(
            allActions - ManageStakeAction.VALIDATORS,
            relayManageActions(RelayStakeKind.VALIDATOR, payoutsSupported = true)
        )
        assertEquals(
            allActions - setOf(ManageStakeAction.VALIDATORS, ManageStakeAction.PAYOUTS),
            relayManageActions(RelayStakeKind.VALIDATOR, payoutsSupported = false)
        )
        assertEquals(allActions, relayManageActions(RelayStakeKind.STASH_NONE, payoutsSupported = true))
        assertEquals(
            allActions - ManageStakeAction.PAYOUTS,
            relayManageActions(RelayStakeKind.STASH_NONE, payoutsSupported = false)
        )
    }

    @Test
    fun `nominator statuses retain era waiting timer and inactive reason independence`() {
        val active = mapNominatorStakeStatus(NominatorStatus.Active, "Era 42")
        val waiting = mapNominatorStakeStatus(NominatorStatus.Waiting(Long.MAX_VALUE), "ignored")
        val lowStake = mapNominatorStakeStatus(
            NominatorStatus.Inactive(NominatorStatus.Inactive.Reason.MIN_STAKE),
            "Era 7"
        )
        val noValidator = mapNominatorStakeStatus(
            NominatorStatus.Inactive(NominatorStatus.Inactive.Reason.NO_ACTIVE_VALIDATOR),
            "Era 8"
        )

        assertTrue(active is StakeStatus.Active)
        assertEquals("Era 42", active.extraMessage)
        assertTrue(waiting is StakeStatus.Waiting)
        assertEquals(MAX_STAKING_TIMER_MILLIS, (waiting as StakeStatus.Waiting).timeLeft)
        assertTrue(lowStake is StakeStatus.Inactive)
        assertEquals("Era 7", lowStake.extraMessage)
        assertTrue(noValidator is StakeStatus.Inactive)
        assertEquals("Era 8", noValidator.extraMessage)
    }

    @Test
    fun `validator and stash statuses map every supported state`() {
        val validatorActive = mapValidatorStakeStatus(ValidatorStatus.ACTIVE, "Era 1")
        val validatorInactive = mapValidatorStakeStatus(ValidatorStatus.INACTIVE, "Era 2")
        val stashInactive = mapStashNoneStakeStatus(StashNoneStatus.INACTIVE, "Era 3")

        assertTrue(validatorActive is StakeStatus.Active)
        assertEquals("Era 1", validatorActive.extraMessage)
        assertTrue(validatorInactive is StakeStatus.Inactive)
        assertEquals("Era 2", validatorInactive.extraMessage)
        assertTrue(stashInactive is StakeStatus.Inactive)
        assertEquals("Era 3", stashInactive.extraMessage)
    }

    @Test
    fun `ready to unlock dominates every adversarial collator status`() {
        val statuses = listOf(
            CandidateInfoStatus.ACTIVE,
            CandidateInfoStatus.EMPTY,
            CandidateInfoStatus.IDLE,
            CandidateInfoStatus.LEAVING(Long.MIN_VALUE),
            null
        )

        statuses.forEach { status ->
            assertSame(
                StakeStatus.ReadyToUnlockCollator,
                mapCollatorStakeStatus(status, Long.MIN_VALUE, Long.MAX_VALUE, isReadyToUnlock = true)
            )
        }
    }

    @Test
    fun `collator statuses clamp adversarial timers and recover from absent timing data`() {
        val active = mapCollatorStakeStatus(
            CandidateInfoStatus.ACTIVE,
            Long.MAX_VALUE,
            leavingTimeLeft = null,
            isReadyToUnlock = false
        )
        val leaving = mapCollatorStakeStatus(
            CandidateInfoStatus.LEAVING(null),
            roundTimeLeft = 1,
            leavingTimeLeft = Long.MIN_VALUE,
            isReadyToUnlock = false
        )
        val missingLeavingTime = mapCollatorStakeStatus(
            CandidateInfoStatus.LEAVING(Long.MAX_VALUE),
            roundTimeLeft = 1,
            leavingTimeLeft = null,
            isReadyToUnlock = false
        )

        assertTrue(active is StakeStatus.ActiveCollator)
        assertEquals(MAX_STAKING_TIMER_MILLIS, (active as StakeStatus.ActiveCollator).timeLeft)
        assertTrue(leaving is StakeStatus.LeavingCollator)
        assertEquals(0L, (leaving as StakeStatus.LeavingCollator).timeLeft)
        assertTrue(missingLeavingTime is StakeStatus.LeavingCollator)
        assertEquals(0L, (missingLeavingTime as StakeStatus.LeavingCollator).timeLeft)

        val missingRoundTime = mapCollatorStakeStatus(
            CandidateInfoStatus.ACTIVE,
            roundTimeLeft = null,
            leavingTimeLeft = null,
            isReadyToUnlock = false
        ) as StakeStatus.ActiveCollator
        assertEquals(0L, missingRoundTime.timeLeft)
        assertTrue(missingRoundTime.hideZeroTimer)
    }

    @Test
    fun `empty and idle collators never become clickable active states`() {
        listOf(CandidateInfoStatus.EMPTY, CandidateInfoStatus.IDLE).forEach { status ->
            val result = mapCollatorStakeStatus(status, 99, 88, isReadyToUnlock = false)

            assertTrue(result is StakeStatus.IdleCollator)
            assertFalse(result.statusClickable)
        }
    }

    @Test
    fun `missing candidate info remains a visible non-clickable delegation state`() {
        val delegations = listOf("known", "missing", "\u0000adversarial")

        val attached = attachCandidateInfoPreservingDelegations(
            delegations = delegations,
            candidateInfos = emptyMap(),
            collatorIdHex = { it }
        )

        assertEquals(delegations, attached.map { it.first })
        assertTrue(attached.all { it.second == null })
        val unavailable = mapCollatorStakeStatus(
            status = null,
            roundTimeLeft = null,
            leavingTimeLeft = null,
            isReadyToUnlock = false
        )
        assertSame(StakeStatus.UnavailableCollator, unavailable)
        assertFalse(unavailable.statusClickable)
    }

    @Test
    fun `round timer rejects invalid dimensions and clamps current block to the round`() {
        val normalRound = Round(
            current = BigInteger.valueOf(42),
            first = BigInteger.valueOf(100),
            length = BigInteger.TEN
        )

        assertEquals(
            3_600_000L,
            calculateRoundTimeLeftMillis(normalRound, BigInteger.valueOf(105), hoursInRound = 2)
        )
        assertEquals(
            0L,
            calculateRoundTimeLeftMillis(normalRound, BigInteger.valueOf(111), hoursInRound = 2)
        )
        assertEquals(
            7_200_000L,
            calculateRoundTimeLeftMillis(normalRound, BigInteger.ZERO, hoursInRound = 2)
        )
        assertEquals(null, calculateRoundTimeLeftMillis(normalRound.copy(length = BigInteger.ZERO), BigInteger.ZERO, 2))
        assertEquals(null, calculateRoundTimeLeftMillis(normalRound.copy(length = BigInteger.ONE.negate()), BigInteger.ZERO, 2))
        assertEquals(null, calculateRoundTimeLeftMillis(normalRound, BigInteger.ZERO, 0))
        assertEquals(null, calculateRoundTimeLeftMillis(normalRound, BigInteger.ZERO, -1))
    }

    @Test
    fun `round timer uses big integer arithmetic and bounds huge durations`() {
        val hugeRound = Round(
            current = BigInteger.ONE.shiftLeft(300),
            first = BigInteger.ONE.shiftLeft(400),
            length = BigInteger.ONE.shiftLeft(350)
        )

        assertEquals(
            MAX_STAKING_TIMER_MILLIS,
            calculateRoundTimeLeftMillis(hugeRound, hugeRound.first, Int.MAX_VALUE)
        )
        assertEquals(
            0L,
            calculateRoundTimeLeftMillis(
                hugeRound,
                hugeRound.first + hugeRound.length + BigInteger.ONE.shiftLeft(500),
                Int.MAX_VALUE
            )
        )
    }

    @Test
    fun `leaving timer handles past huge and invalid round data without long overflow`() {
        assertEquals(
            21_600_000L,
            calculateLeavingTimeLeftMillis(
                currentRoundNumber = BigInteger.valueOf(100),
                leavingRound = 101,
                hoursInRound = 2,
                leaveDelay = 2
            )
        )
        assertEquals(
            0L,
            calculateLeavingTimeLeftMillis(
                currentRoundNumber = BigInteger.ONE.shiftLeft(200),
                leavingRound = Long.MAX_VALUE,
                hoursInRound = Int.MAX_VALUE,
                leaveDelay = Int.MAX_VALUE
            )
        )
        assertEquals(
            MAX_STAKING_TIMER_MILLIS,
            calculateLeavingTimeLeftMillis(
                currentRoundNumber = BigInteger.ZERO,
                leavingRound = Long.MAX_VALUE,
                hoursInRound = Int.MAX_VALUE,
                leaveDelay = Int.MAX_VALUE
            )
        )
        assertEquals(null, calculateLeavingTimeLeftMillis(BigInteger.ZERO, null, 1, 1))
        assertEquals(null, calculateLeavingTimeLeftMillis(BigInteger.ZERO, -1, 1, 1))
        assertEquals(null, calculateLeavingTimeLeftMillis(BigInteger.ZERO, 1, 0, 1))
        assertEquals(null, calculateLeavingTimeLeftMillis(BigInteger.ZERO, 1, -1, 1))
        assertEquals(null, calculateLeavingTimeLeftMillis(BigInteger.ZERO, 1, 1, -1))
        assertEquals(null, calculateLeavingTimeLeftMillis(BigInteger.ONE.negate(), 1, 1, 1))
    }
}
