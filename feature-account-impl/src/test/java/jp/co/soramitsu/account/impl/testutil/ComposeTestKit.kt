package jp.co.soramitsu.account.impl.testutil

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.robolectric.shadows.ShadowSystemClock

/** The app's string for [id], as the screens render it. */
fun string(@StringRes id: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

/**
 * Taps the node after moving the clock past the 1.5 s double-tap guard of `clickableSingle`/`onSingleClick`;
 * Robolectric's clock starts near 100 ms, so an unguarded first tap would be dropped.
 */
fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction {
    ShadowSystemClock.advanceBy(Duration.ofSeconds(5))
    return performClick()
}

/** Matches nodes that show no text, editable text or content description (icon-only targets). */
fun hasNoText(): SemanticsMatcher = SemanticsMatcher("has no text") { node ->
    node.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
        node.config.getOrNull(SemanticsProperties.EditableText) == null &&
        node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
}

/** Clickable icon-only targets in tree order, for example a toolbar's back arrow followed by field icons. */
fun iconClickables(): SemanticsMatcher = hasClickAction() and hasNoText()
