package jp.co.soramitsu.common.compose.theme.fw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FwMotionPolicyTest {
    private val full = FwMotionPolicy(reduceMotion = false)

    @Test fun ordinaryMotionPlaysEveryEventAtItsSpecDuration() {
        for (kind in FwMotionKind.entries) assertEquals(kind.name, FwMotionMode.Full, full.mode(kind))
        assertEquals(90, full.durationMillis(FwMotionKind.Press))
        assertEquals(450, full.durationMillis(FwMotionKind.BalanceRoll))
        assertEquals(1600, full.durationMillis(FwMotionKind.SyncSweep))
        assertEquals(300, full.durationMillis(FwMotionKind.Shake))
        assertTrue(full.moves(FwMotionKind.Press))
        assertFalse("A tint flash is not movement", full.moves(FwMotionKind.PriceFlash))
    }

    @Test fun reducedMotionCalmAndReducedEffectsKeepInformationWithoutMovement() {
        for (policy in listOf(
            FwMotionPolicy(reduceMotion = true),
            FwMotionPolicy(reduceMotion = false, calm = true),
            FwMotionPolicy(reduceMotion = false, reducedEffects = true)
        )) {
            for (kind in FwMotionKind.entries) {
                val mode = policy.mode(kind)
                assertFalse("$kind must not move under $policy", policy.moves(kind))
                assertTrue(policy.durationMillis(kind) <= FwMotionDurations.REDUCED_CROSSFADE)
                if (kind == FwMotionKind.SyncSweep || kind == FwMotionKind.TxBreathe) assertEquals("loops stop", FwMotionMode.Still, mode)
            }
            assertEquals(FwMotionMode.Still, policy.mode(FwMotionKind.Shake))
            assertEquals(FwMotionMode.Crossfade, policy.mode(FwMotionKind.BalanceRoll))
            assertEquals(FwMotionMode.Crossfade, policy.mode(FwMotionKind.Copied))
            assertEquals(0, policy.staggerDelayMillis(3))
        }
    }

    @Test fun secureSurfacesAllowPressFeedbackOnly() {
        val secure = FwMotionPolicy(reduceMotion = false, secure = true)
        for (kind in FwMotionKind.entries) {
            if (kind == FwMotionKind.Press) assertEquals(FwMotionMode.Full, secure.mode(kind))
            else assertEquals(kind.name, FwMotionMode.Still, secure.mode(kind))
        }
        assertEquals(0, secure.durationMillis(FwMotionKind.BalanceRoll))
    }

    @Test fun staggerIsThirtyMillisecondsPerRowForAtMostEightRows() {
        assertEquals(0, full.staggerDelayMillis(0))
        assertEquals(30, full.staggerDelayMillis(1))
        assertEquals(210, full.staggerDelayMillis(7))
        assertEquals(210, full.staggerDelayMillis(40))
    }

    @Test fun effectsMapToThePolicyIncludingAnimatorScaleZero() {
        val still = resolveFwEffects(34, lowRam = false, powerSave = false, systemContrast = 0f, animatorsEnabled = false)
        assertTrue(still.motionPolicy().reduceMotion)
        val calm = FwEffects(FwTier.Full, highContrast = false, reduceMotion = false, calm = true)
        assertEquals(FwMotionMode.Crossfade, calm.motionPolicy().mode(FwMotionKind.TabWell))
        val reduced = FwEffects(FwTier.Reduced, highContrast = false, reduceMotion = false)
        assertTrue(reduced.motionPolicy().reducedEffects)
        val lite = FwEffects(FwTier.Lite, highContrast = false, reduceMotion = false)
        assertEquals(FwMotionMode.Full, lite.motionPolicy().mode(FwMotionKind.ListStagger))
        assertEquals(FwMotionMode.Still, lite.motionPolicy(secure = true).mode(FwMotionKind.ListStagger))
    }

    @Test fun staggerWindowCoversOnlyTheFirstAppearance() {
        val stagger = FwStagger(startedAt = 1_000L, policy = full)
        assertEquals(60, stagger.delayFor(2, now = 1_100L))
        assertEquals(null, stagger.delayFor(2, now = 1_000L + FwStagger.FIRST_APPEARANCE_WINDOW_MS + 1))
        assertEquals(null, stagger.delayFor(8, now = 1_100L))
        assertEquals(null, FwStagger(1_000L, FwMotionPolicy(reduceMotion = true)).delayFor(1, now = 1_050L))
    }
}
