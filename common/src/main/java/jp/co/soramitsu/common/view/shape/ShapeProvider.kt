package jp.co.soramitsu.common.view.shape

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.util.StateSet
import android.util.TypedValue
import androidx.annotation.ColorRes
import jp.co.soramitsu.common.R

fun Int.toColorStateList() = ColorStateList.valueOf(this)

fun Context.addRipple(drawable: Drawable): Drawable {
    val typedValue = TypedValue()

    theme.resolveAttribute(R.attr.colorControlHighlight, typedValue, true)
    val rippleColor = typedValue.data.toColorStateList()

    return RippleDrawable(rippleColor, drawable, null)
}

fun Context.getCutCornersStateDrawable(): Drawable {
    return StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), getDisabledDrawable())
        addState(intArrayOf(android.R.attr.state_focused), getFocusedDrawable())
        addState(StateSet.WILD_CARD, getIdleDrawable())
    }
}

fun Context.getFocusedDrawable(): Drawable = getCutCornerDrawable(R.color.white, CutCornersShape.Type.OUTLINE)
fun Context.getDisabledDrawable(): Drawable = getCutCornerDrawable(R.color.gray3, CutCornersShape.Type.FILL)
fun Context.getIdleDrawable(): Drawable = getCutCornerDrawable(R.color.gray2, CutCornersShape.Type.OUTLINE)

fun Context.getCutCornerDrawable(
    @ColorRes colorRes: Int,
    type: CutCornersShape.Type,
    cornerSizeInDp: Int = 10,
    strokeSizeInDp: Int = 1
): Drawable {
    val density = resources.displayMetrics.density

    val cornerSizePx = density * cornerSizeInDp
    val strokeSizePx = density * strokeSizeInDp
    val color = getColor(colorRes)

    return ShapeDrawable(CutCornersShape(cornerSizePx, strokeSizePx, color, type))
}
