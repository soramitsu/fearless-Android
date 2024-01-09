package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.customColors

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
