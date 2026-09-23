package jp.co.soramitsu.app.root.presentation.main

import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.app.R
import org.junit.Test

class PolkaswapNavigationActionTest {
    @Test
    fun centerPlaceholderIsHiddenFromAccessibilityAndEveryButtonTapUsesTheExistingMenu() {
        val navigation = mockk<BottomNavigationView>(relaxed = true)
        val placeholder = mockk<View>(relaxed = true)
        val button = mockk<View>(relaxed = true)
        val click = slot<View.OnClickListener>()
        every { navigation.findViewById<View>(R.id.polkaswapGraph) } returns placeholder
        every { button.setOnClickListener(capture(click)) } returns Unit

        configurePolkaswapNavigationAction(navigation, button)
        click.captured.onClick(button)
        click.captured.onClick(button)

        verify { placeholder.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        verify { placeholder.isFocusable = false }
        verify(exactly = 2) { navigation.selectedItemId = R.id.polkaswapGraph }
    }

    @Test
    fun measuredNavigationReservesItsHeightAndTheRaisedArtworkForContent() {
        val navigation = mockk<BottomNavigationView>(relaxed = true)
        val button = mockk<View>(relaxed = true)
        val content = mockk<View>(relaxed = true)
        val layout = slot<View.OnLayoutChangeListener>()
        every { navigation.addOnLayoutChangeListener(capture(layout)) } returns Unit
        every { navigation.height } returns 192
        every { button.measuredHeight } returns 84
        every { button.translationY } returns 24f
        every { content.paddingLeft } returns 3
        every { content.paddingTop } returns 4
        every { content.paddingRight } returns 5

        configurePolkaswapNavigationAction(navigation, button, content)
        layout.captured.onLayoutChange(navigation, 0, 0, 480, 192, 0, 0, 480, 84)

        verify { content.setPadding(3, 4, 5, 210) }
    }

}
