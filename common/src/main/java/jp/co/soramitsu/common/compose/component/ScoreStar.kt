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
fun ScoreStar(score: Int) {
    val (starRes, color) = when (score) {
        in 0..33 -> R.drawable.ic_score_star_empty to warningOrange
        in 33..66 -> R.drawable.ic_score_star_half to warningYellow
        in 66..100 -> R.drawable.ic_score_star_full to greenText
        else -> R.drawable.ic_score_star_empty to white30
    }

    when {
        score >= 0 -> {
            Row {
                Image(res = starRes, tint = color, modifier = Modifier.size(12.dp))
                MarginHorizontal(margin = 3.dp)
                B2(text = score.toString(), color = color)
            }
        }
        score == -1 -> {
            // loading
            Image(res = starRes, tint = color, modifier = Modifier
                .size(12.dp)
                .shimmer())
        }
        score == -2 -> {
            //error
            Row {
                Image(res = starRes, tint = color, modifier = Modifier.size(12.dp))
                MarginHorizontal(margin = 3.dp)
                B2(text = "N/A", color = color)
            }
        }
    }
}

@Preview
@Composable
private fun ScoreStarPreview(){
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