package jp.co.soramitsu.common.view

import android.content.Context
import android.os.Parcelable
import android.util.SparseArray
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.github.razir.progressbutton.isProgressActive
import jp.co.soramitsu.common.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectionAndProgressViewsRobolectricTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Soramitsu_Fearless)

    @Test
    fun shouldToggleSegmentSelectionNotifyListenersAndRestoreIt() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(R.attr.labelOne, "All")
            .addAttribute(R.attr.labelTwo, "Favorites").addAttribute(android.R.attr.singleLine, "false").build()
        val view = SegmentedButtonView(context, attrs).apply { id = 102030 }
        assertEquals("All", view.findViewById<TextView>(R.id.optionOne).text.toString())
        assertEquals("Favorites", view.findViewById<TextView>(R.id.optionTwo).text.toString())
        val selected = mutableListOf<Int>()
        view.setOnSelectionChangeListener { selected += it }
        view.toggle()
        assertEquals(1, view.getSelectedIndex())
        val background = view.findViewById<android.view.View>(R.id.selectedBackground)
        assertEquals(R.id.optionTwo, (background.layoutParams as ConstraintLayout.LayoutParams).startToStart)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)
        view.toggle()
        assertEquals(0, view.getSelectedIndex())
        view.restoreHierarchyState(saved)
        assertEquals(1, view.getSelectedIndex())
        assertEquals(listOf(1, 0, 1), selected)
        view.setOnSelectionChangeListener(null)
        view.toggle()
        assertEquals(listOf(1, 0, 1), selected)
        assertEquals(0, view.getSelectedIndex())
        view.restoreHierarchyState(SparseArray())
        assertEquals(0, view.getSelectedIndex())
    }

    @Test
    fun shouldRequireLifecyclePreparationAndRestoreTextAfterProgress() {
        val button = PrimaryButton(context).apply { text = "Continue" }
        assertThrows(IllegalArgumentException::class.java) { button.setState(ButtonState.PROGRESS) }
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        button.prepareForProgress(owner)
        button.setState(ButtonState.NORMAL)
        assertTrue(button.isEnabled)
        button.setProgress(true)
        assertFalse(button.isEnabled)
        assertTrue(button.isProgressActive())
        button.setProgress(true)
        assertTrue(button.isProgressActive())
        button.setState(ButtonState.DISABLED)
        assertFalse(button.isProgressActive())
        assertFalse(button.isEnabled)
        assertEquals("Continue", button.text.toString())
        button.setProgress(false)
        assertTrue(button.isEnabled)
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }
}
