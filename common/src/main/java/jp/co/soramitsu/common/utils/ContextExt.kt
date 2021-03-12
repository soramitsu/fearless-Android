package jp.co.soramitsu.common.utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.R

fun Context.getDrawableCompat(@DrawableRes drawableRes: Int) =
    ContextCompat.getDrawable(this, drawableRes)!!

fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"

        putExtra(Intent.EXTRA_TEXT, text)
    }

    startActivity(intent)
}

inline fun postToUiThread(crossinline action: () -> Unit) {
    Handler(Looper.getMainLooper()).post {
        action.invoke()
    }
}

fun Int.dp(context: Context): Int {
    val inPx = context.resources.displayMetrics.density * this

    return inPx.toInt()
}

fun Context.readAssetFile(name: String) = assets.open(name).readText()

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}
@ColorInt
fun Context.getPrimaryColor() = getColorFromAttr(R.attr.colorPrimary)
