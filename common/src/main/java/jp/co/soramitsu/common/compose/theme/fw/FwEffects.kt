package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Paint tiers never change layout, copy, hit targets or wallet behavior. */
enum class FwTier { Full, Lite, Reduced }
enum class FwTierChoice { Automatic, Lite, Reduced }

@Immutable
data class FwEffects(
    val tier: FwTier,
    val highContrast: Boolean,
    val reduceMotion: Boolean,
    val calm: Boolean = false
) {
    val paintReduced: Boolean get() = tier == FwTier.Reduced || highContrast || calm
    val glass: Boolean get() = !paintReduced
    val aurora: Boolean get() = !paintReduced
    val glow: Boolean get() = !paintReduced
    val neuShadows: Boolean get() = !paintReduced

    // Phase 0 uses opaque chrome. A capable device alone must not enable an unqualified blur.
    val liveBlur: Boolean get() = false
}

@Immutable
data class FwAppearance(
    val choice: FwTierChoice = FwTierChoice.Automatic,
    val highContrast: Boolean = false
)

/** Platform-independent precedence, also used by the live Android environment. */
fun resolveFwEffects(
    sdkInt: Int,
    lowRam: Boolean,
    powerSave: Boolean,
    systemContrast: Float,
    animatorsEnabled: Boolean,
    appearance: FwAppearance = FwAppearance()
): FwEffects {
    val highContrast = appearance.highContrast || (sdkInt >= 34 && systemContrast >= 0.5f)
    val lite = sdkInt < 31 || lowRam || powerSave || appearance.choice == FwTierChoice.Lite
    val tier = when {
        highContrast || appearance.choice == FwTierChoice.Reduced -> FwTier.Reduced
        lite -> FwTier.Lite
        else -> FwTier.Full
    }
    return FwEffects(tier, highContrast, reduceMotion = !animatorsEnabled)
}

val LocalFwEffects = staticCompositionLocalOf { FwEffects(FwTier.Lite, highContrast = false, reduceMotion = false) }
val LocalFwColors = staticCompositionLocalOf { FwStandard }

/** Security and confirmation surfaces retain their parent contrast and motion preferences. */
@Composable
fun FwCalm(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFwEffects provides LocalFwEffects.current.copy(calm = true), content = content)
}
