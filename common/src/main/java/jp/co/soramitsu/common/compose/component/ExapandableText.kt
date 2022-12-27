package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.clickableWithNoIndication

enum class ExpandClickableMode {
    Arrow,
    FullWidth
}

@Composable
fun ExapandableText(
    title: String,
    modifier: Modifier = Modifier,
    initialState: Boolean = false,
    expandClickableMode: ExpandClickableMode = ExpandClickableMode.FullWidth,
    content: @Composable ColumnScope.() -> Unit
) {
    val isExpandedState = remember { mutableStateOf(initialState) }
    val icon = if (isExpandedState.value) {
        R.drawable.ic_chevron_down_white
    } else {
        R.drawable.ic_chevron_up_white
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableWithNoIndication {
                    if (expandClickableMode == ExpandClickableMode.FullWidth) {
                        isExpandedState.value = isExpandedState.value.not()
                    }
                }
        ) {
            H5(text = title, modifier = Modifier.weight(1f))
            Image(
                res = icon,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clickableWithNoIndication {
                        if (expandClickableMode == ExpandClickableMode.Arrow) {
                            isExpandedState.value = isExpandedState.value.not()
                        }
                    }
            )
        }
        if (isExpandedState.value) {
            MarginVertical(margin = 27.dp)
            content()
        }
    }
}
