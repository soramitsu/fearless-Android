package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.white64

data class TitleValueViewState(
    val title: String,
    val value: String? = null,
    val additionalValue: String? = null
)

@Composable
fun TitleToValue(state: TitleValueViewState) {
    ConstraintLayout {
        val (title, value, additional) = createRefs()

        B2(
            text = state.title,
            color = white64,
            modifier = Modifier.constrainAs(title) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
            }
        )
        state.value?.let {
            B0(text = it, modifier = Modifier.constrainAs(value) {
                top.linkTo(title.bottom, 4.dp)
                start.linkTo(title.start)
            })
        } ?: ShimmerB0(modifier = Modifier.constrainAs(value) {
            top.linkTo(title.bottom, 4.dp)
            start.linkTo(title.start)
            end.linkTo(title.end)
        })

        state.additionalValue?.let { additionalValue ->
            B2(
                text = additionalValue,
                color = white64,
                modifier = Modifier.constrainAs(additional) {
                    top.linkTo(value.bottom, 2.dp)
                    start.linkTo(title.start)
                })
        }
    }
}

@Composable
@Preview
fun TitleValuePreview() {
    FearlessTheme {
        Column {
            TitleToValue(
                TitleValueViewState("Total staked", "2.5M KSM", "\$380.94M")
            )
            MarginVertical(margin = 32.dp)
            TitleToValue(TitleValueViewState("Total staked", null, null))
        }
    }
}
