package jp.co.soramitsu.app.root.presentation.main

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.HiltShimHostActivity
import jp.co.soramitsu.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** At large text sizes the tab bar grows and its labels may wrap to two centred lines instead of being cut off. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class PolkaswapNavigationLargeTextTest {

    @Test
    fun largeTextRaisesTheBarAndLetsLabelsWrap() {
        RuntimeEnvironment.setFontScale(1.5f)
        val activity = Robolectric.buildActivity(HiltShimHostActivity::class.java).setup().get()
        val themed = ContextThemeWrapper(activity, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar)
        val navigation = BottomNavigationView(themed).apply { inflateMenu(R.menu.bottom_navigations_fab) }
        activity.setContentView(navigation)

        configurePolkaswapNavigationAction(navigation, View(activity))

        val density = navigation.resources.displayMetrics.density
        assertEquals(((56 + 72 * (1.5f - 1)) * density).toInt(), navigation.minimumHeight)
        val labels = (0 until navigation.menu.size()).flatMap { index ->
            val item = navigation.findViewById<View>(navigation.menu.getItem(index).itemId)
            listOf(
                com.google.android.material.R.id.navigation_bar_item_small_label_view,
                com.google.android.material.R.id.navigation_bar_item_large_label_view
            ).mapNotNull { item.findViewById<TextView>(it) }
        }
        assertTrue(labels.isNotEmpty())
        labels.forEach { label ->
            assertEquals(2, label.maxLines)
            assertEquals(null, label.ellipsize)
            assertEquals(Gravity.CENTER, label.gravity and Gravity.CENTER)
        }
    }
}
