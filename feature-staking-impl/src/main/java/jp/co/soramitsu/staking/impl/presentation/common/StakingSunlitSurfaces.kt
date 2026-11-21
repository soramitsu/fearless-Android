package jp.co.soramitsu.staking.impl.presentation.common

import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import jp.co.soramitsu.common.compose.theme.blurColorLight
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.fwLightSurface

/** Staking's Compose cards: raised and key-lit in the sun-lit design, the translucent legacy card otherwise. */
internal val stakingCardColor: Color
    @Composable get() = if (FwRestyle.isEnabled(FwSurface.Staking)) MaterialTheme.customColors.backgroundBlurColor else blurColorLight

/** The same card for staking's XML views. */
internal fun View.stakingCard(legacy: () -> Drawable) {
    if (FwRestyle.isEnabled(FwSurface.Staking)) fwLightSurface() else background = legacy()
}
