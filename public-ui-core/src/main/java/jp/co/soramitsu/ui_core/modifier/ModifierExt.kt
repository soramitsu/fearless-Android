package jp.co.soramitsu.ui_core.modifier

import androidx.compose.ui.Modifier

inline fun Modifier.applyIf(condition: Boolean, block: Modifier.() -> Modifier): Modifier {
    return if (condition) block() else this
}
