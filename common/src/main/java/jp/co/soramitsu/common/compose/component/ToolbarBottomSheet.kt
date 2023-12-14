package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme

@Composable
fun ToolbarBottomSheet(
    modifier: Modifier = Modifier,
    title: String?,
    @DrawableRes navigationIconResId: Int = R.drawable.ic_arrow_left_24,
    onNavigationClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart
        ) {
            NavigationIconButton(
                navigationIconResId = navigationIconResId,
                onNavigationClick = onNavigationClick
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            title?.let {
                H4(text = title)
            }
        }
    }
}

@Preview
@Composable
private fun ToolbarBottomSheetPreview() {
    FearlessAppTheme {
        Column(
            modifier = Modifier
                .background(Color.Black)
                .padding(16.dp)
        ) {
            ToolbarBottomSheet(
                title = "title",
                onNavigationClick = {}
            )
        }
    }
}