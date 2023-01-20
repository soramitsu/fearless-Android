package jp.co.soramitsu.common.compose.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.clickableWithNoIndication

@Composable
fun ExapandableText(
    title: String,
    modifier: Modifier = Modifier,
    initialState: Boolean = false,
    isFullWidthClickable: Boolean = true,
    onClick: () -> Unit = emptyClick,
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpandedState by remember { mutableStateOf(initialState) }
    val iconRotate = if (isExpandedState) 180f else 0f
    val animatedIconRotate by animateFloatAsState(targetValue = iconRotate)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableWithNoIndication {
                    if (isFullWidthClickable) {
                        isExpandedState = isExpandedState.not()
                    }
                    onClick()
                }
        ) {
            H5(text = title, modifier = Modifier.weight(1f))
            Image(
                res = R.drawable.ic_arrow_down,
                modifier = Modifier
                    .rotate(animatedIconRotate)
                    .align(Alignment.CenterVertically)
                    .clickableWithNoIndication {
                        isExpandedState = isExpandedState.not()
                    }
            )
        }
        if (isExpandedState) {
            MarginVertical(margin = 27.dp)
            content()
        }
    }
}
