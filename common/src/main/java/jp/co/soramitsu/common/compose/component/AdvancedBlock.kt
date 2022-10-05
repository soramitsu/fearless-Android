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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.clickableWithNoIndication

@Composable
fun AdvancedBlock(Content: @Composable ColumnScope.() -> Unit) {
    val isHiddenState = remember { mutableStateOf(false) }
    val icon = if (isHiddenState.value) {
        R.drawable.ic_chevron_down_white
    } else {
        R.drawable.ic_chevron_up_white
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickableWithNoIndication { isHiddenState.value = isHiddenState.value.not() }
        ) {
            H5(text = stringResource(id = R.string.common_advanced), modifier = Modifier.weight(1f))
            Image(res = icon, modifier = Modifier.align(Alignment.CenterVertically))
        }
        if (isHiddenState.value.not()) {
            MarginVertical(margin = 27.dp)
            Content()
        }
    }
}
