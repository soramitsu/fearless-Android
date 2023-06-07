package jp.co.soramitsu.common.compose.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.accentRadioButtonColors
import jp.co.soramitsu.common.utils.clickableSingle

data class TextSelectableItemState(
    val isSelected: Boolean,
    @StringRes val textResId: Int
)

@Composable
fun TextSelectableItem(
    state: TextSelectableItemState,
    modifier: Modifier = Modifier,
    onSelectedCallback: () -> Unit
) {
    Row(
        modifier = modifier
            .clickableSingle { onSelectedCallback() }
            .padding(vertical = 8.dp)
    ) {
        FearlessRadioButton(
            selected = state.isSelected,
            onClick = null,
            modifier = Modifier.align(Alignment.CenterVertically),
            colors = accentRadioButtonColors
        )

        B1(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            text = stringResource(state.textResId)
        )
    }
}
