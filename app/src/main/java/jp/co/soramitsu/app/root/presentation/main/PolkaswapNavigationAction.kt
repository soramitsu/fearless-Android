package jp.co.soramitsu.app.root.presentation.main

import android.text.Layout
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import jp.co.soramitsu.app.R

/** Keeps the raised button as the single accessible action for the existing center tab. */
internal fun configurePolkaswapNavigationAction(navigation: BottomNavigationView, button: View, content: View? = null) {
    navigation.findViewById<View>(R.id.polkaswapGraph)?.apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = false
    }
    button.setOnClickListener {
        // Assign through the menu so its existing selection and reselection listeners both run.
        navigation.selectedItemId = R.id.polkaswapGraph
    }
    navigation.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        // The content must stop above both the bar and the raised center artwork at every text size.
        val raisedButtonHeight = (button.measuredHeight / 2f - button.translationY).coerceAtLeast(0f).toInt()
        content?.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight,
            navigation.height + raisedButtonHeight)
    }
    val scale = navigation.resources.configuration.fontScale
    if (scale > 1.3f) {
        navigation.minimumHeight = ((56 + 72 * (scale - 1)) * navigation.resources.displayMetrics.density).toInt()
        for (index in 0 until navigation.menu.size()) {
            val item = navigation.findViewById<View>(navigation.menu.getItem(index).itemId)
            for (labelId in listOf(
                com.google.android.material.R.id.navigation_bar_item_small_label_view,
                com.google.android.material.R.id.navigation_bar_item_large_label_view
            )) {
                item.findViewById<TextView>(labelId)?.apply {
                    setSingleLine(false)
                    maxLines = 2
                    ellipsize = null
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    breakStrategy = Layout.BREAK_STRATEGY_BALANCED
                    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
                }
            }
        }
    }

}
