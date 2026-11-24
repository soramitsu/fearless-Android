package jp.co.soramitsu.onboarding.impl.testutil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.robolectric.shadows.ShadowSystemClock

/** The English text of a string resource, as the screens show it. */
fun text(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

/** Moves the clock past the buttons' 1.5 s double-tap guard, so the next tap counts. */
fun allowNextTap() = ShadowSystemClock.advanceBy(Duration.ofSeconds(10))

/** Every view of type [T] in this hierarchy, this view included. */
inline fun <reified T : View> View.viewsOfType(): List<T> {
    val found = mutableListOf<T>()
    val pending = ArrayDeque<View>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        val view = pending.removeFirst()
        if (view is T) found += view
        if (view is ViewGroup) for (index in 0 until view.childCount) pending.add(view.getChildAt(index))
    }
    return found
}

/** Draws the hierarchy once into a software canvas, so draw-time code (drawBehind, drawWithCache) runs. */
fun View.drawOnce() {
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    draw(Canvas(bitmap))
}
