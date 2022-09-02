package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.backgroundBlack

@Composable
fun BottomSheetScreen(modifier: Modifier = Modifier, Content: @Composable ColumnScope.() -> Unit) {
    Column {
        MarginVertical(margin = 12.dp)
        Column(
            modifier = modifier
                .background(backgroundBlack, RoundedCornerShape(topEnd = 24.dp, topStart = 24.dp))

        ) {
            MarginVertical(margin = 12.dp)
            Content()
        }
    }
}
