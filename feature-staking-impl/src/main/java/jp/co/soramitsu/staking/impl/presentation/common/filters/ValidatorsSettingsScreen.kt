package jp.co.soramitsu.staking.impl.presentation.common.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Switch
import androidx.compose.material.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetDialog
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Sorting

data class ValidatorsSettingsViewState(
    val filters: List<FilterItemViewState>,
    val sortings: List<SortingItemViewState>
)

data class FilterItemViewState(
    val title: String,
    val isSelected: Boolean,
    val filter: Filters
)

data class SortingItemViewState(
    val title: String,
    val isSelected: Boolean,
    val sorting: Sorting
)

@Composable
fun ValidatorsSettingsScreen(
    state: ValidatorsSettingsViewState,
    onCloseClick: () -> Unit,
    onFilterSelectClick: (FilterItemViewState) -> Unit,
    onSortingSelectClick: (SortingItemViewState) -> Unit
) {
    BottomSheetDialog {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                H3(text = stringResource(id = R.string.wallet_filters_title))
                Spacer(modifier = Modifier.weight(1f))
                Image(res = R.drawable.ic_close, modifier = Modifier.clickable(onClick = onCloseClick))
            }
            MarginVertical(margin = 13.dp)
            if (state.filters.isNotEmpty()) {
                H3(text = stringResource(id = R.string.wallet_filters_header))
                state.filters.forEach {
                    FilterItem(it, onFilterSelectClick)
                }
            }
            if (state.sortings.isNotEmpty()) {
                H3(text = stringResource(id = R.string.common_filter_sort_header))
                state.sortings.forEach {
                    SortingItem(it, onSortingSelectClick)
                }
            }
        }
    }
}

val switchColors = object : SwitchColors {
    @Composable
    override fun thumbColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(white)
    }

    @Composable
    override fun trackColor(enabled: Boolean, checked: Boolean): State<Color> {
        return rememberUpdatedState(transparent)
    }
}

@Composable
fun FilterItem(state: FilterItemViewState, onSelectClick: (FilterItemViewState) -> Unit) {
    Row(
        Modifier
            .padding(vertical = 12.dp)
            .clickableWithNoIndication { onSelectClick(state) }) {
        H5(text = state.title, color = black2)
        Spacer(modifier = Modifier.weight(1f))
        val trackColor = if (state.isSelected) colorAccent else black3
        Switch(
            colors = switchColors,
            checked = state.isSelected,
            onCheckedChange = { onSelectClick(state) },
            modifier = Modifier
                .background(color = trackColor, shape = RoundedCornerShape(20.dp))
                .padding(3.dp)
                .height(20.dp)
                .width(35.dp)
        )
    }
}

@Composable
fun SortingItem(state: SortingItemViewState, onSelectClick: (SortingItemViewState) -> Unit) {
    Row(
        Modifier
            .padding(vertical = 12.dp)
            .clickableWithNoIndication { onSelectClick(state) }) {
        H5(text = state.title, color = black2)
        Spacer(modifier = Modifier.weight(1f))
        if (state.isSelected) {
            Image(res = R.drawable.ic_selected, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
@Preview
private fun ValidatorsSettingsScreenPreview() {
    val state = remember {
        mutableStateOf(
            ValidatorsSettingsViewState(
                listOf(
                    FilterItemViewState("Having onchain identity", true, Filters.HavingOnChainIdentity),
                    FilterItemViewState("Not slashed", false, Filters.HavingOnChainIdentity),
                    FilterItemViewState("Not oversubscribed", true, Filters.HavingOnChainIdentity)
                ),
                listOf(
                    SortingItemViewState("APY", true, Sorting.EstimatedRewards),
                    SortingItemViewState("Total stake", false, Sorting.EstimatedRewards),
                    SortingItemViewState("Own stake", false, Sorting.EstimatedRewards)
                )
            )
        )
    }

    ValidatorsSettingsScreen(state.value, {}, {
        val index = state.value.filters.indexOf(it)
        val newFilters = state.value.filters.toMutableList()
        newFilters.removeAt(index)
        newFilters.add(index, it.copy(isSelected = it.isSelected.not()))
        state.value = state.value.copy(filters = newFilters)
    }, {
        val index = state.value.sortings.indexOf(it)
        val newSortings = state.value.sortings.toMutableList()
        newSortings.removeAt(index)
        newSortings.add(index, it.copy(isSelected = it.isSelected.not()))
        state.value = state.value.copy(sortings = newSortings)
    })
}
