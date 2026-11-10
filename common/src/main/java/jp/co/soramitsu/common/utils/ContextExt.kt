package jp.co.soramitsu.common.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

/**
 * Google sign-in for Drive backup runs in the browser, so it is offered only when some app can open
 * https links. The app manifest declares this query, which Android 11+ needs to see browsers.
 */
fun Context.canOpenHttpsLinks(): Boolean = packageManager.canOpenHttpsLinks(httpsLinkIntent())

internal fun PackageManager.canOpenHttpsLinks(intent: Intent): Boolean =
    queryIntentActivities(intent, 0).isNotEmpty()

internal fun httpsLinkIntent(): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.google.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
