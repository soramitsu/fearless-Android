package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.backgroundBlurColor
import jp.co.soramitsu.common.compose.theme.white

@Composable
fun ToolbarBottomSheet(
    title: String?,
    @DrawableRes navigationIconResId: Int = R.drawable.ic_arrow_left_24,
    onNavigationClicked: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.material.IconButton(
                onClick = {
                    onNavigationClicked()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(backgroundBlurColor)
                    .size(32.dp)
            ) {
                Icon(
                    painter = painterResource(id = navigationIconResId),
                    tint = white,
                    contentDescription = null
                )
            }
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
