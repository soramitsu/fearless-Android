package jp.co.soramitsu.common.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import jp.co.soramitsu.common.R

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
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
        }
    }

}