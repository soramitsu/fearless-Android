package jp.co.soramitsu.common.compose.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ScreenLayout {
    Grid, List
}

@Composable
fun ScreenLayout.Render(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    when(this) {

        ScreenLayout.List -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content.invoke()
        }

        ScreenLayout.Grid -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content.invoke()
        }

    }
}