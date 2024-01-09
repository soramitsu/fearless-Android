package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.green
import jp.co.soramitsu.common.compose.theme.white64

data class TitleValueViewState(
    val title: String,
    val value: String? = null,
    val additionalValue: String? = null,
    val valueColor: Color = Color.Unspecified,
    val clickState: ClickState? = null
) {
    sealed class ClickState(@DrawableRes val icon: Int, val identifier: Int) {
        class Title(@DrawableRes icon: Int, identifier: Int) : ClickState(icon, identifier)
        class Value(@DrawableRes icon: Int, identifier: Int) : ClickState(icon, identifier)
    }
}

@Composable
fun TitleToValue(state: TitleValueViewState, modifier: Modifier = Modifier, testTag: String) {
    TitleToValue(state = state, titleColor = white64, modifier = modifier, testTag = testTag)
}

@Composable
fun ChangeToValue(state: TitleValueViewState?, modifier: Modifier = Modifier, testTag: String) {
    state?.let { TitleToValue(state = it, titleColor = green, modifier = modifier, testTag = testTag) } ?: TitleToValueShimmer(modifier)
}

@Composable
private fun TitleToValue(modifier: Modifier = Modifier, state: TitleValueViewState, titleColor: Color, testTag: String) {
    ConstraintLayout(modifier = modifier) {
        val (title, value, additional) = createRefs()

        B2(
            text = state.title,
            color = titleColor,
            modifier = Modifier
                .constrainAs(title) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                }
                .testTag("${testTag}Title")
        )

        state.value?.let {
            B0(
                text = it,
                modifier = Modifier
                    .constrainAs(value) {
                        top.linkTo(title.bottom, 4.dp)
                        start.linkTo(title.start)
                    }
                    .testTag("${testTag}Value")
            )
        } ?: ShimmerB0(
            modifier = Modifier
                .constrainAs(value) {
                    top.linkTo(title.bottom, 8.dp)
                    start.linkTo(title.start)
                    end.linkTo(title.end)
                    width = Dimension.fillToConstraints
                }
                .testTag("${testTag}ValueShimmer")
        )

        state.additionalValue?.let { additionalValue ->
            B2(
                text = additionalValue,
                color = white64,
                modifier = Modifier
                    .constrainAs(additional) {
                        top.linkTo(value.bottom, 2.dp)
                        start.linkTo(title.start)
                    }
                    .testTag("${testTag}AdditionalValue")
            )
        }
    }
}

@Composable
private fun TitleToValueShimmer(modifier: Modifier) {
    Column(modifier) {
        MarginVertical(margin = 4.dp)
        ShimmerB2(modifier = Modifier.width(100.dp))
        MarginVertical(margin = 10.dp)
        ShimmerB0(modifier = Modifier.width(90.dp))
    }
}

@Composable
@Preview
private fun TitleValuePreview() {
    FearlessTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .background(Color.Black)
        ) {
            TitleToValue(
                testTag = "",
                state = TitleValueViewState("Total staked", "2.5M KSM", "\$380.94M")
            )
            MarginVertical(margin = 32.dp)
            TitleToValue(testTag = "", state = TitleValueViewState("Total staked", null, null))
            MarginVertical(margin = 32.dp)
            Row {
                ChangeToValue(testTag = "", state = TitleValueViewState("1,43% monthly", "0.164 KSM", "$24.92"))
                MarginHorizontal(margin = 16.dp)
                ChangeToValue(testTag = "", state = null)
            }
            MarginVertical(margin = 32.dp)
            ChangeToValue(testTag = "", state = null)
        }
    }
}
