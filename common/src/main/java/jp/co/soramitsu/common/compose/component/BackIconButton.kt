package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08

@Composable
fun NavigationIconButton(
    modifier: Modifier = Modifier,
    @DrawableRes navigationIconResId: Int = R.drawable.ic_arrow_left_24,
    onNavigationClick: () -> Unit = {}
) {
    androidx.compose.material.IconButton(
        onClick = onNavigationClick,
        modifier = modifier
            .clip(CircleShape)
            .background(white08)
            .size(32.dp)
    ) {
        Icon(
            painter = painterResource(id = navigationIconResId),
            tint = white,
            contentDescription = null
        )
    }
}

@Preview
@Composable
fun NavigationIconButtonPreview() {
    NavigationIconButton()
}
