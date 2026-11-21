package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.fw.FwElevation
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwShape
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.common.compose.theme.fw.fwSurface
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle

data class MnemonicWordModel(
    val numberToShow: String,
    val word: String
)

fun mapMnemonicToMnemonicWords(mnemonic: List<String>): List<MnemonicWordModel> {
    return mnemonic.mapIndexed { index: Int, word: String ->
        MnemonicWordModel(
            (index + 1).toString(),
            word
        )
    }
}

fun List<MnemonicWordModel>.toMnemonicString(): String {
    return joinToString(separator = "")
}

@Composable
fun MnemonicWords(
    mnemonicWords: List<MnemonicWordModel>,
    modifier: Modifier = Modifier
) {
    if (FwRestyle.isEnabled(FwSurface.Mnemonic)) {
        SunlitMnemonicWords(mnemonicWords, modifier)
        return
    }
    val mnemonicWordsCount by remember(mnemonicWords) {
        derivedStateOf { mnemonicWords.size }
    }

    Row(modifier = modifier) {
        Spacer(modifier = Modifier.weight(1f))
        val firstPart by remember(mnemonicWords, mnemonicWordsCount) {
            derivedStateOf {
                mnemonicWords.subList(0, (mnemonicWordsCount + 1) / 2)
            }
        }
        MnemonicWordsColumn(mnemonicWords = firstPart)
        Spacer(modifier = Modifier.weight(1f))
        val secondPart by remember(mnemonicWords, mnemonicWordsCount) {
            derivedStateOf {
                mnemonicWords.subList((mnemonicWordsCount + 1) / 2, mnemonicWordsCount)
            }
        }
        MnemonicWordsColumn(mnemonicWords = secondPart)
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * The words in a single well pressed into the surface, numbered and still: this page never animates.
 * Large text folds the two columns into one, so a word never wraps or leaves the well.
 */
@Composable
private fun SunlitMnemonicWords(mnemonicWords: List<MnemonicWordModel>, modifier: Modifier) {
    val c = LocalFwColors.current
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    val perColumn = ((mnemonicWords.size + columns - 1) / columns).coerceAtLeast(1)
    // In sp, so that a two-digit number stays on one line and clear of its word at every text size.
    val numberWidth = with(LocalDensity.current) { 32.sp.toDp() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fwSurface(LocalFwEffects.current, c, FwShape.Card, FwElevation.Inset)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        mnemonicWords.chunked(perColumn).forEach { part ->
            Column(Modifier.weight(1f)) {
                part.forEach { word ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text(
                            word.numberToShow,
                            modifier = Modifier.width(numberWidth),
                            style = fwTextStyle(FwTextRole.PhraseWord),
                            color = c.textTertiary,
                            softWrap = false
                        )
                        Text(word.word, style = fwTextStyle(FwTextRole.PhraseWord), color = c.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MnemonicWordsColumn(
    modifier: Modifier = Modifier,
    mnemonicWords: List<MnemonicWordModel>
) {
    val maxSymbolsInColumn by remember(mnemonicWords) {
        derivedStateOf {
            mnemonicWords
                .maxByOrNull { it.numberToShow.length }
                ?.numberToShow.orEmpty()
                .length
        }
    }
    MeasureUnconstrainedViewWidth(
        modifier = modifier,
        viewToMeasure = {
            MnemonicWordNumber(
                number = "0".repeat(maxSymbolsInColumn)
            )
        }
    ) { numberTextWidth ->
        Column {
            mnemonicWords.forEach { mnemonicWord ->
                MnemonicWord(
                    mnemonicWord = mnemonicWord,
                    numberTextWidth = numberTextWidth
                )
            }
        }
    }
}

@Composable
private fun MnemonicWord(
    mnemonicWord: MnemonicWordModel,
    numberTextWidth: Dp,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        MnemonicWordNumber(
            modifier = Modifier.width(numberTextWidth),
            number = mnemonicWord.numberToShow
        )
        MarginHorizontal(margin = 12.dp)
        B0(text = mnemonicWord.word)
    }
}

@Composable
private fun MnemonicWordNumber(
    number: String,
    modifier: Modifier = Modifier
) {
    B0(
        modifier = modifier,
        text = number,
        color = MaterialTheme.customColors.colorGreyText
    )
}

@Composable
private fun MeasureUnconstrainedViewWidth(
    modifier: Modifier = Modifier,
    viewToMeasure: @Composable () -> Unit,
    content: @Composable (measuredWidth: Dp) -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val measuredWidth = subcompose("viewToMeasure", viewToMeasure)[0]
            .measure(Constraints()).width.toDp()

        val contentPlaceable = subcompose("content") {
            content(measuredWidth)
        }[0].measure(constraints)
        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
        }
    }
}

@Preview
@Composable
fun MnemonicWordsPreview(
    modifier: Modifier = Modifier
) {
    val mnemonicWords = listOf(
        MnemonicWordModel(numberToShow = "1", word = "song"),
        MnemonicWordModel(numberToShow = "2", word = "toss"),
        MnemonicWordModel(numberToShow = "3", word = "odor"),
        MnemonicWordModel(numberToShow = "4", word = "click"),
        MnemonicWordModel(numberToShow = "5", word = "blouse"),
        MnemonicWordModel(numberToShow = "6", word = "lesson"),
        MnemonicWordModel(numberToShow = "7", word = "runway"),
        MnemonicWordModel(numberToShow = "8", word = "popular"),
        MnemonicWordModel(numberToShow = "9", word = "owner"),
        MnemonicWordModel(numberToShow = "10", word = "caught"),
        MnemonicWordModel(numberToShow = "11", word = "wrist"),
        MnemonicWordModel(numberToShow = "12", word = "poverty")
    )
    MnemonicWords(mnemonicWords = mnemonicWords)
}
