package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.warningYellow
import jp.co.soramitsu.common.compose.theme.white30

@Composable
fun ScoreStar(score: Int, modifier: Modifier = Modifier) {
    val starRes = when (score) {
        in 0..33 -> R.drawable.ic_score_star_empty
        in 33..66 -> R.drawable.ic_score_star_half
        in 66..100 -> R.drawable.ic_score_star_full
        else -> R.drawable.ic_score_star_empty
    }
    val color = when (score) {
        in 0..25 -> warningOrange
        in 25..75 -> warningYellow
        in 75..100 -> greenText
        else -> white30
    }

    when {
        score >= 0 -> {
            Row(modifier = modifier) {
                Image(res = starRes, tint = color, modifier = Modifier.size(12.dp))
                MarginHorizontal(margin = 3.dp)
                B2(text = score.toString(), color = color)
            }
        }

        score == -1 -> {
            // loading
            Image(
                res = starRes, tint = color, modifier = modifier
                    .size(12.dp)
                    .shimmer()
            )
        }

        score == -2 -> {
            //error
            Row(modifier = modifier) {
                Image(res = starRes, tint = color, modifier = Modifier.size(12.dp))
                MarginHorizontal(margin = 3.dp)
                B2(text = "N/A", color = color)
            }
        }
    }
}

@Preview
@Composable
private fun ScoreStarPreview() {
    FearlessAppTheme {
        Column {
            ScoreStar(score = -1)
            ScoreStar(score = -2)
            ScoreStar(score = 0)
            ScoreStar(score = 50)
            ScoreStar(score = 100)
        }
    }
}