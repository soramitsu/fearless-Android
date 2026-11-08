package jp.co.soramitsu.common.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.fw.FwColors
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwNavBarDrawable

@SuppressLint("RestrictedApi")
class BottomNavigationViewWithFAB @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr) {

    init {
        val viewAttrs = context.theme.obtainStyledAttributes(
            attrs, R.styleable.FabBottomNavigationView, 0, 0
        )

        val fabSize = viewAttrs.getDimension(
            R.styleable.FabBottomNavigationView_fab_size, 0f
        )

        val fabCradleMargin = viewAttrs.getDimension(
            R.styleable.FabBottomNavigationView_fab_cradle_margin, 0f
        )

        val cradleVerticalOffset = viewAttrs.getDimension(
            R.styleable.FabBottomNavigationView_cradle_vertical_offset, 0f
        )

        val topCurvedEdgeTreatment = BottomNavigationWithFABTopEdgeTreatment(
            fabSize,
            fabCradleMargin,
            cradleVerticalOffset
        )

        val shapeAppearanceModel = ShapeAppearanceModel.Builder()
            .setTopEdge(topCurvedEdgeTreatment)
            .build()

        background = MaterialShapeDrawable(shapeAppearanceModel).apply {
            fillColor = this@BottomNavigationViewWithFAB.backgroundTintList
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
        }
        viewAttrs.recycle()

    }

    private var sunlit: FwNavBarDrawable? = null
    private var wellItemId: Int = View.NO_ID
    private var wellHidden = true

    /**
     * Sun-lit navigation (FW Navigation surface): a raised floating bar with a light well that slides
     * under the selected tab. Items keep their ids, labels and accessibility; only paint changes.
     */
    fun applySunlitStyle(fx: FwEffects, colors: FwColors) {
        val density = resources.displayMetrics.density
        val sideMargin = SUNLIT_SIDE_MARGIN * density
        val bar = FwNavBarDrawable(colors, fx, density, sideMargin = sideMargin, verticalMargin = 6f * density, corner = 26f * density)
        sunlit = bar
        background = bar
        backgroundTintList = null
        elevation = 0f
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val tint = ColorStateList(states, intArrayOf(colors.textBrand.toArgb(), colors.textTertiary.toArgb()))
        itemIconTintList = tint
        itemTextColor = tint
        itemRippleColor = null
        isItemActiveIndicatorEnabled = false
        // The tabs share the bar's width, so the outer labels and the light well stay inside its ends.
        setPadding(sideMargin.toInt(), (SUNLIT_VERTICAL_PADDING * density).toInt(), sideMargin.toInt(), paddingBottom)
        applySunlitLabelFont()
    }

    /** Only the typeface changes, so the labels keep following the system text size. */
    private fun applySunlitLabelFont() {
        val inactive = ResourcesCompat.getFont(context, R.font.sora_semibold)
        val active = ResourcesCompat.getFont(context, R.font.sora_bold)
        for (index in 0 until menu.size()) {
            val item = findViewById<View>(menu.getItem(index).itemId) ?: continue
            item.findViewById<TextView>(com.google.android.material.R.id.navigation_bar_item_small_label_view)?.typeface = inactive
            item.findViewById<TextView>(com.google.android.material.R.id.navigation_bar_item_large_label_view)?.typeface = active
        }
    }

    /** Bottom system inset below the bar; the bar floats above it with its own margin. */
    fun setSunlitBottomInset(inset: Int) {
        val bar = sunlit ?: return
        bar.bottomInset = inset
        setPadding(paddingLeft, paddingTop, paddingRight, inset + (SUNLIT_VERTICAL_PADDING * resources.displayMetrics.density).toInt())
    }

    /** Slides the light well under [itemId]; [hidden] tabs (drawn by their own control) get no well. */
    fun moveSunlitWell(itemId: Int, hidden: Boolean, animate: Boolean) {
        wellItemId = itemId
        wellHidden = hidden
        placeWell(animate)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (sunlit != null) placeWell(animate = false)
    }

    private fun placeWell(animate: Boolean) {
        val bar = sunlit ?: return
        val item = findViewById<View>(wellItemId)
        if (wellHidden || item == null || item.width == 0) {
            bar.select(Float.NaN, 0f, animate = false)
            return
        }
        val menu = item.parent as? View ?: return
        val center = menu.left + item.left + item.width / 2f
        val density = resources.displayMetrics.density
        bar.select(center, (item.width - 10f * density).coerceAtMost(76f * density), animate)
    }

    private companion object {
        const val SUNLIT_VERTICAL_PADDING = 10
        const val SUNLIT_SIDE_MARGIN = 14
    }
}