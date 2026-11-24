package jp.co.soramitsu.app.root.presentation.main

import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSearchCustomValidatorsBinding
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CatalogueRetryUiInstrumentedTest {
    @Test fun failurePlaceholderShowsTheCauseAndRemainsAvailableForNativeRetryTaps() {
        CatalogueRetryUiFixture().use { f ->
            f.render(SearchBlockProducersState.Failed("Provider unavailable"))
            f.inspect {
                assertEquals("Provider unavailable\nRetry", it.searchCustomValidatorsPlaceholder.text.toString())
                assertRetryAvailable(it)
            }
            f.tapPlaceholder()
            f.render(SearchBlockProducersState.Failed("Provider unavailable"))
            f.tapPlaceholder()
            assertEquals(listOf("retry", "retry"), f.retries.toList())
        }
    }

    @Test fun activeContextRefreshShowsOnlyTheRetryPromptAndAcceptsANativeTap() {
        CatalogueRetryUiFixture().use { f ->
            f.render(SearchBlockProducersState.RefreshRequired)
            f.inspect {
                assertEquals("Retry", it.searchCustomValidatorsPlaceholder.text.toString())
                assertRetryAvailable(it)
            }
            f.tapPlaceholder()
            assertEquals(listOf("retry"), f.retries.toList())
        }
    }

    @Test fun retryTransitionsToLoadingAndRemovesThePreviousClickListener() {
        for (previous in retryStates()) {
            CatalogueRetryUiFixture().use { f ->
                f.render(previous) {
                    f.retries += "retry"
                    f.renderOnMain(SearchBlockProducersState.Loading)
                }
                f.tapPlaceholder()
                f.inspect {
                    assertEquals(View.VISIBLE, it.searchCustomValidatorProgress.visibility)
                    assertEquals(View.INVISIBLE, it.searchCustomValidatorsList.visibility)
                    assertEquals(View.GONE, it.searchCustomValidatorListHeader.visibility)
                    assertEquals(View.GONE, it.searchCustomValidatorsPlaceholder.visibility)
                    assertRetryRemoved(it)
                }
                assertEquals(listOf("retry"), f.retries.toList())
            }
        }
    }

    @Test fun noInputReplacesBothRetryStatesWithAnInertSearchPrompt() {
        for (previous in retryStates()) {
            CatalogueRetryUiFixture().use { f ->
                f.render(previous)
                f.render(SearchBlockProducersState.NoInput)
                f.inspect {
                    assertEquals(it.root.context.getString(R.string.common_search_start_title), it.searchCustomValidatorsPlaceholder.text.toString())
                    assertEquals(View.VISIBLE, it.searchCustomValidatorsPlaceholder.visibility)
                    assertRetryRemoved(it)
                }
                f.tapPlaceholder()
                assertTrue(f.retries.isEmpty())
            }
        }
    }

    @Test fun noResultsReplacesBothRetryStatesWithAnInertEmptyMessage() {
        for (previous in retryStates()) {
            CatalogueRetryUiFixture().use { f ->
                f.render(previous)
                f.render(SearchBlockProducersState.NoResults)
                f.inspect {
                    assertEquals(it.root.context.getString(R.string.staking_validator_search_empty_title), it.searchCustomValidatorsPlaceholder.text.toString())
                    assertEquals(View.VISIBLE, it.searchCustomValidatorsPlaceholder.visibility)
                    assertRetryRemoved(it)
                }
                f.tapPlaceholder()
                assertTrue(f.retries.isEmpty())
            }
        }
    }

    @Test fun successfulRecoveryPreservesRealRowsSelectionHeaderAndQueryWhileClearingRetry() {
        for (previous in retryStates()) {
            CatalogueRetryUiFixture().use { f ->
                f.inspect { it.searchCustomValidatorsField.setText("al") }
                f.render(previous)
                f.render(SearchBlockProducersState.Success(f.rows, "Results: 2"))
                f.inspect {
                    assertEquals(View.VISIBLE, it.searchCustomValidatorsList.visibility)
                    assertEquals(View.VISIBLE, it.searchCustomValidatorListHeader.visibility)
                    assertEquals(View.INVISIBLE, it.searchCustomValidatorProgress.visibility)
                    assertEquals(View.GONE, it.searchCustomValidatorsPlaceholder.visibility)
                    assertRetryRemoved(it)
                    assertEquals("Results: 2", it.searchCustomValidatorAccounts.text.toString())
                    assertEquals("al", it.searchCustomValidatorsField.text.toString())
                    assertEquals(f.rows, f.adapter.currentList)
                    val first = requireNotNull(it.searchCustomValidatorsList.findViewHolderForAdapterPosition(0)).itemView
                    val second = requireNotNull(it.searchCustomValidatorsList.findViewHolderForAdapterPosition(1)).itemView
                    assertEquals("Alice", first.findViewById<TextView>(R.id.name).text.toString())
                    assertEquals("12.5%", first.findViewById<TextView>(R.id.scoring).text.toString())
                    assertEquals(View.VISIBLE, first.findViewById<View>(R.id.selectedIndicator).visibility)
                    assertEquals("ALPINE", second.findViewById<TextView>(R.id.name).text.toString())
                    assertEquals(View.GONE, second.findViewById<View>(R.id.selectedIndicator).visibility)
                    second.performClick()
                    first.findViewById<View>(R.id.info).performClick()
                }
                assertEquals(listOf("alpine-address"), f.selected.toList())
                assertEquals(listOf("alice-address"), f.inspected.toList())
                assertTrue(f.retries.isEmpty())
            }
        }
    }

    @Test fun renderingANewRetryHandlerReplacesTheOldHandlerWithoutAccumulatingCallbacks() {
        CatalogueRetryUiFixture().use { f ->
            f.render(SearchBlockProducersState.Failed("Old failure")) { f.retries += "obsolete" }
            f.render(SearchBlockProducersState.RefreshRequired) { f.retries += "current" }
            f.tapPlaceholder()
            f.tapPlaceholder()
            assertEquals(listOf("current", "current"), f.retries.toList())
        }
    }

    @Test fun retryCanReturnAfterAnInertStateWithoutRestoringAnOldHandler() {
        CatalogueRetryUiFixture().use { f ->
            f.render(SearchBlockProducersState.Failed("Old failure")) { f.retries += "obsolete" }
            f.render(SearchBlockProducersState.NoResults)
            f.tapPlaceholder()
            assertTrue(f.retries.isEmpty())
            f.render(SearchBlockProducersState.Failed("Current failure")) { f.retries += "current" }
            f.tapPlaceholder()
            assertEquals(listOf("current"), f.retries.toList())
        }
    }

    @Test fun retryUsesTheRealInflatedViewsJapaneseResourcesAndStillDispatchesTheAction() {
        CatalogueRetryUiFixture(Locale.JAPANESE).use { f ->
            f.render(SearchBlockProducersState.RefreshRequired)
            f.inspect { assertEquals("リトライ", it.searchCustomValidatorsPlaceholder.text.toString()) }
            f.tapPlaceholder()
            f.render(SearchBlockProducersState.Failed("Provider unavailable"))
            f.inspect { assertEquals("Provider unavailable\nリトライ", it.searchCustomValidatorsPlaceholder.text.toString()) }
            f.tapPlaceholder()
            assertEquals(listOf("retry", "retry"), f.retries.toList())
        }
    }

    private fun retryStates() =
        listOf(SearchBlockProducersState.Failed("Provider unavailable"), SearchBlockProducersState.RefreshRequired)

    private fun assertRetryAvailable(binding: FragmentSearchCustomValidatorsBinding) {
        val placeholder = binding.searchCustomValidatorsPlaceholder
        assertEquals(View.VISIBLE, placeholder.visibility)
        assertTrue(placeholder.isClickable)
        assertTrue(placeholder.isFocusable)
        assertTrue(placeholder.hasOnClickListeners())
        assertTrue(placeholder.createAccessibilityNodeInfo().isClickable)
        assertEquals(View.INVISIBLE, binding.searchCustomValidatorProgress.visibility)
        assertEquals(View.INVISIBLE, binding.searchCustomValidatorsList.visibility)
        assertEquals(View.GONE, binding.searchCustomValidatorListHeader.visibility)
    }

    private fun assertRetryRemoved(binding: FragmentSearchCustomValidatorsBinding) {
        val placeholder = binding.searchCustomValidatorsPlaceholder
        assertFalse(placeholder.isClickable)
        assertFalse(placeholder.isFocusable)
        assertFalse(placeholder.hasOnClickListeners())
        assertFalse(placeholder.performClick())
    }
}
