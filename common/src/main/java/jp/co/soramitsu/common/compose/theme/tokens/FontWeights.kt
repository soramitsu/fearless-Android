package jp.co.soramitsu.common.compose.theme.tokens

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.font.FontWeight.Companion.Normal

internal enum class FontWeights(val value: FontWeight) {
    ZERO(Normal),
    ONE(Bold),
}
