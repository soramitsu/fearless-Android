package jp.co.soramitsu.common.compose.theme.tokens

import androidx.compose.ui.text.font.FontFamily
import jp.co.soramitsu.common.compose.theme.Inter
import jp.co.soramitsu.common.compose.theme.Sora
import jp.co.soramitsu.common.compose.theme.fw.FwFont

internal enum class FontFamilies(val value: FontFamily) {
    HEADLINE(Sora),
    TEXT(Inter),
    MONO(FwFont.Mono)
}
