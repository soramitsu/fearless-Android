package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingComposeTest
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectValidatorsScreenTest : StakingComposeTest() {

    private class Recorder : SelectValidatorsScreenInterface {
        val events = mutableListOf<String>()
        override fun onNavigationClick() { events += "back" }
        override fun onSelected(item: SelectableListItemState<String>) { events += "select:${item.id}" }
        override fun onInfoClick(item: SelectableListItemState<String>) { events += "info:${item.id}" }
        override fun onChooseClick() { events += "choose" }
        override fun onOptionsClick() { events += "options" }
        override fun onSearchQueryInput(query: String) { events += "query:$query" }
    }

    private val alice = SelectableListItemState(
        id = "a",
        title = "Alice validator",
        subtitle = "Staked 10 DOT",
        caption = AnnotatedString("commission 5%"),
        isSelected = false,
        additionalStatuses = listOf(SelectableListItemState.SelectableListItemAdditionalStatus.OVERSUBSCRIBED)
    )
    private val bob = SelectableListItemState(id = "b", title = "Bob validator", isSelected = false)

    private fun loaded(selected: List<SelectableListItemState<String>>) = LoadingState.Loaded(
        ValidatorsListViewState(
            segments = listOf(
                ListSegmentState(
                    title = "Elected",
                    iconRes = R.drawable.ic_elected_validator,
                    sortingValue = "APY",
                    description = "Selected by the network",
                    items = listOf(alice)
                ),
                ListSegmentState(items = listOf(bob))
            ),
            selectedItems = selected
        )
    )

    private fun renderScreen(state: SelectValidatorsScreenViewState, legacy: Boolean, recorder: Recorder) =
        render(legacy) { SelectValidatorsScreen(state, recorder) }

    private fun listWithSelectionReachesCallbacks(legacy: Boolean) {
        val recorder = Recorder()
        renderScreen(SelectValidatorsScreenViewState("Suggested", isCustom = false, listState = loaded(listOf(alice))), legacy, recorder)

        compose.onNodeWithText("Suggested").assertExists()
        compose.onNodeWithText("Elected").assertExists()
        compose.onNodeWithText("APY").assertExists()
        compose.onNodeWithText("Selected by the network").assertExists()
        compose.onNodeWithText("Staked 10 DOT").assertExists()
        compose.onNodeWithText("commission 5%").assertExists()
        compose.onNodeWithText("Bob validator").performClick()
        compose.onNodeWithText(str(R.string.pool_staking_choosepool_button_title)).assertIsEnabled().performClick()
        compose.waitForIdle()

        assertTrue(recorder.events.toString(), "select:b" in recorder.events)
        assertTrue(recorder.events.toString(), "choose" in recorder.events)
    }

    @Test
    fun restyledListShowsSegmentsAndReportsSelectionAndChoice() = listWithSelectionReachesCallbacks(legacy = false)

    @Test
    fun legacyListShowsSegmentsAndReportsSelectionAndChoice() = listWithSelectionReachesCallbacks(legacy = true)

    @Test
    fun chooseIsDisabledWithoutSelection() {
        val recorder = Recorder()
        renderScreen(SelectValidatorsScreenViewState("Custom", isCustom = true, searchQuery = "al", listState = loaded(emptyList())), false, recorder)

        compose.onNodeWithText(str(R.string.pool_staking_choosepool_button_title)).assertIsNotEnabled().performClick()
        compose.onNodeWithText("al").assertExists()
        compose.waitForIdle()
        assertFalse("choose" in recorder.events)
    }

    @Test
    fun emptyCustomListExplainsFilters() {
        val empty = LoadingState.Loaded(ValidatorsListViewState(emptyList(), emptyList()))
        renderScreen(SelectValidatorsScreenViewState("Custom", isCustom = true, listState = empty), true, Recorder())
        compose.onNodeWithText(str(R.string.custom_validators_empty_message)).assertExists()
    }

    @Test
    fun emptySuggestedListSaysNoValidators() {
        val empty = LoadingState.Loaded(ValidatorsListViewState(listOf(ListSegmentState(items = emptyList())), emptyList()))
        renderScreen(SelectValidatorsScreenViewState("Suggested", isCustom = false, listState = empty), false, Recorder())
        compose.onNodeWithText(str(R.string.validators_list_empty_message)).assertExists()
        assertTrue(empty.data.isEmpty)
    }

    @Test
    fun loadingHidesTheList() {
        renderScreen(SelectValidatorsScreenViewState("Suggested", isCustom = false, listState = LoadingState.Loading()), false, Recorder())
        compose.onNodeWithText("Suggested").assertExists()
        compose.onNodeWithText(str(R.string.pool_staking_choosepool_button_title)).assertDoesNotExist()
    }

    @Test
    fun multiSelectListMarksSelectedItems() {
        val selected = mutableListOf<String>()
        val state = MultiSelectListViewState(items = listOf(alice, bob), selectedItems = listOf(bob))
        render { ValidatorsList(listState = state, onSelected = { selected += it.id }, onInfoClick = {}) }

        compose.onNodeWithText("Alice validator").performClick()
        compose.waitForIdle()
        assertEquals(listOf("a"), selected)
        assertFalse(state.isEmpty)
        assertTrue(MultiSelectListViewState.empty<String>().isEmpty)
    }
}
