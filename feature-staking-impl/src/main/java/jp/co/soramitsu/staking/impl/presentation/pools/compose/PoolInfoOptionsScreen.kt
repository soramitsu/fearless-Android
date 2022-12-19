package jp.co.soramitsu.staking.impl.presentation.pools.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetDialog
import jp.co.soramitsu.common.compose.component.H3Bold
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextButton
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customButtonColors
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.feature_staking_impl.R

data class PoolInfoOptionsViewState(
    val options: List<Option>
) {
    enum class Option(@StringRes val titleRes: Int, val textColor: Color) {
        Edit(R.string.common_edit, white),
        Destroy(R.string.common_destroy, white),
        Block(R.string.common_block, colorAccentDark)
    }
}

@Composable
fun PoolInfoOptionsScreen(state: PoolInfoOptionsViewState, onSelected: (PoolInfoOptionsViewState.Option) -> Unit) {
    BottomSheetDialog(
        verticalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.CenterVertically),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        H3Bold(text = stringResource(id = R.string.pool_options_title))
        MarginVertical(margin = 4.dp)
        state.options.forEach {
            val onClick = remember { { onSelected(it) } }
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                text = stringResource(id = it.titleRes),
                colors = customButtonColors(grayButtonBackground, it.textColor),
                onClick = onClick
            )
        }
        MarginVertical(margin = 24.dp)
    }
}

@Composable
@Preview
private fun PoolInfoOptionsScreenPreview() {
    FearlessTheme {
        PoolInfoOptionsScreen(PoolInfoOptionsViewState(PoolInfoOptionsViewState.Option.values().toList()), {})
    }
}
