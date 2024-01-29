package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.black3

@Composable
fun CollapsibleView(
    modifier: Modifier = Modifier,
    title: String,
    @DrawableRes collapsedIconResId: Int = R.drawable.ic_plus_white_24,
    @DrawableRes expandedIconResId: Int = R.drawable.ic_minus_24,
    initCollapsed: Boolean = true,
    onActionClick: (Boolean) -> Unit = {},
    expandableContent: @Composable ColumnScope.() -> Unit
) {
    val isCollapsed = remember { mutableStateOf(initCollapsed) }
    val actionIconRes = if (isCollapsed.value) collapsedIconResId else expandedIconResId

    val onClick = {
        val newCollapsedValue = isCollapsed.value.not()
        isCollapsed.value = newCollapsedValue
        onActionClick(newCollapsedValue)
    }

    Column(
        modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            B1(text = title)
            Spacer(modifier = Modifier.weight(1f))
            Image(
                modifier = Modifier
                    .align(Alignment.CenterVertically),
                res = actionIconRes
            )
        }

        Divider(color = black3, thickness = 1.dp)

        if (isCollapsed.value.not()) {
            MarginVertical(margin = 8.dp)
            expandableContent()
        }
    }
}

@Preview
@Composable
fun CollapsibleViewPreview() {
    CollapsibleView(
        title = "Review required permissions",
        initCollapsed = false
    ) {
        H1(text = "Some header there")
    }
}
