package jp.co.soramitsu.app.root.presentation.main

import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ViewGroup
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import java.io.File
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.view.BottomNavigationViewWithFAB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolkaswapNavigationLayoutInstrumentedTest {
    @Test
    fun narrowLayoutKeepsOneNamedCenterActionAtStandardAndDoubleTextSize() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        for (scale in listOf(1f, 2f)) {
            val intent = Intent(target, PolkaswapNavigationLayoutTestActivity::class.java)
            ActivityScenario.launch<PolkaswapNavigationLayoutTestActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    val configuration = Configuration(target.resources.configuration).apply { fontScale = scale }
                    val context = ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_Soramitsu_Fearless)
                    val inflater = LayoutInflater.from(context).cloneInContext(context)
                    inflater.factory2 = object : LayoutInflater.Factory2 {
                        override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? =
                            onCreateView(name, context, attrs)

                        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
                            if (name == "androidx.fragment.app.FragmentContainerView") FrameLayout(context, attrs) else null
                    }
                    // Inflate the production shell; omit wallet fragments and their account/network dependencies.
                    val root = inflater.inflate(R.layout.fragment_main, null)
                    val navigation = root.findViewById<BottomNavigationViewWithFAB>(R.id.bottom_navigation_view_with_fab)
                    val button = root.findViewById<ExtendedFloatingActionButton>(R.id.fabMain)
                    val content = root.findViewById<View>(R.id.bottomNavHost)
                    configurePolkaswapNavigationAction(navigation, button, content)
                    activity.setContentView(root)
                    val density = context.resources.displayMetrics.density
                    val width = (320 * density).toInt()
                    val height = (640 * density).toInt()
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)

                    assertTrue("The content viewport must end above the raised button", height - content.paddingBottom <= button.top + button.translationY)
                    assertEquals(255, android.graphics.Color.alpha((navigation.background as MaterialShapeDrawable).fillColor!!.defaultColor))
                    assertEquals("Polkaswap", button.contentDescription.toString())
                    val accessibilityNode = button.createAccessibilityNodeInfo()
                    assertEquals("Polkaswap", accessibilityNode.contentDescription.toString())
                    assertTrue(accessibilityNode.isClickable)
                    assertEquals(View.VISIBLE, button.visibility)
                    assertTrue(button.isClickable)
                    assertTrue(button.width / density >= 48)
                    assertTrue(button.height / density >= 48)
                    assertTrue(button.left >= 0 && button.right <= width)
                    assertTrue(button.top + button.translationY >= 0 && button.bottom + button.translationY <= height)
                    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                        navigation.findViewById<View>(R.id.polkaswapGraph).importantForAccessibility)
                    assertTrue(navigation.background is MaterialShapeDrawable)
                    val selected = mutableListOf<Int>()
                    val reselected = mutableListOf<Int>()
                    navigation.setOnItemSelectedListener { selected += it.itemId; true }
                    navigation.setOnItemReselectedListener { reselected += it.itemId }
                    button.performClick()
                    button.performClick()
                    assertEquals(listOf(R.id.polkaswapGraph), selected)
                    assertEquals(listOf(R.id.polkaswapGraph), reselected)

                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)
                    for (index in 0 until navigation.menu.size()) {
                        val item = navigation.findViewById<View>(navigation.menu.getItem(index).itemId)
                        for (labelId in listOf(
                            com.google.android.material.R.id.navigation_bar_item_small_label_view,
                            com.google.android.material.R.id.navigation_bar_item_large_label_view
                        )) {
                            val label = item.findViewById<TextView>(labelId)
                            if (label.visibility == View.VISIBLE) {
                                val bounds = Rect(0, 0, label.width, label.height)
                                (root as ViewGroup).offsetDescendantRectToMyCoords(label, bounds)
                                assertTrue("scale=$scale label=${label.text} bounds=$bounds viewport=${width}x$height",
                                    bounds.left >= 0 && bounds.right <= width && bounds.top >= navigation.top && bounds.bottom <= height)
                                assertTrue("scale=$scale label=${label.text} text height=${label.layout.height} view height=${label.height}",
                                    label.layout.height <= label.height)
                                assertEquals(label.text.length, label.layout.getLineEnd(label.layout.lineCount - 1))
                                for (line in 0 until label.layout.lineCount) assertEquals(0, label.layout.getEllipsisCount(line))
                            }
                        }
                    }

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    val directory = target.getExternalFilesDir("ux-evidence")!!
                    directory.mkdirs()
                    File(directory, "polkaswap-320dp-font${(scale * 100).toInt()}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
    }
}
