package jp.co.soramitsu.common.view.shape

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.util.StateSet
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import jp.co.soramitsu.common.R

fun Int.toColorStateList() = ColorStateList.valueOf(this)

fun Context.addRipple(drawable: Drawable? = null, mask: Drawable? = null): Drawable {
    val rippleColor = getColor(R.color.colorSelected)

    return RippleDrawable(rippleColor.toColorStateList(), drawable, mask)
}

fun Context.getCutCornersStateDrawable(
    disabledDrawable: Drawable = getDisabledDrawable(),
    focusedDrawable: Drawable = getFocusedDrawable(),
    idleDrawable: Drawable = getIdleDrawable()
): Drawable {
    return StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), disabledDrawable)
        addState(intArrayOf(android.R.attr.state_focused), focusedDrawable)
        addState(StateSet.WILD_CARD, idleDrawable)
    }
}

fun Context.getFocusedDrawable(): Drawable = getCutCornerDrawable(strokeColorRes = R.color.white)
fun Context.getDisabledDrawable(): Drawable = getCutCornerDrawable(fillColorRes = R.color.gray3)
fun Context.getIdleDrawable(): Drawable = getCutCornerDrawable(strokeColorRes = R.color.gray2)

fun Context.getCutCornerDrawable(
    @ColorRes fillColorRes: Int = R.color.black,
    @ColorRes strokeColorRes: Int? = null,
    cornerSizeInDp: Int = 10,
    strokeSizeInDp: Int = 1
): Drawable {
    val fillColor = getColor(fillColorRes)
    val strokeColor = strokeColorRes?.let(this::getColor)

    return getCutCornerDrawableFromColors(fillColor, strokeColor, cornerSizeInDp, strokeSizeInDp)
}

fun Context.getCutCornerDrawableFromColors(
    @ColorInt fillColor: Int = getColor(R.color.black),
    @ColorInt strokeColor: Int? = null,
    cornerSizeInDp: Int = 10,
    strokeSizeInDp: Int = 1
): Drawable {
    val density = resources.displayMetrics.density

    val cornerSizePx = density * cornerSizeInDp
    val strokeSizePx = density * strokeSizeInDp

    return ShapeDrawable(CutCornersShape(cornerSizePx, strokeSizePx, fillColor, strokeColor))
}
