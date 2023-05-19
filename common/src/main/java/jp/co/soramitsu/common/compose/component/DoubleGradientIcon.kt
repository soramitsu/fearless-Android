package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.colorAccentDark

@Composable
fun DoubleGradientIcon(
    leftImage: GradientIconState,
    rightImage: GradientIconState,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        GradientIcon(
            icon = leftImage,
            color = colorAccentDark,
            background = backgroundBlack,
            modifier = Modifier
                .offset(x = 25.dp)
                .zIndex(1f),
            contentPadding = PaddingValues(10.dp)
        )
        GradientIcon(
            icon = rightImage,
            color = colorAccentDark,
            background = backgroundBlack,
            modifier = Modifier
                .offset(x = (-25).dp)
                .zIndex(0f),
            contentPadding = PaddingValues(10.dp)
        )
    }
}

@Preview
@Composable
fun DoubleGradientIconPreview() {
    DoubleGradientIcon(
        leftImage = GradientIconState.Local(R.drawable.ic_vector),
        rightImage = GradientIconState.Local(R.drawable.ic_vector)
    )
}
