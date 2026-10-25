package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.request.SuccessResult
import java.io.File
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.getImageRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android/Coil rendering, including merged variant resources and asset-file loading. */
@RunWith(AndroidJUnit4::class)
class AndroidBrandAssetsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun mergedBrandResourcesRenderAtShippedIconAndHeroSizes() {
        val resources = listOf(
            Triple("colour-hero", R.drawable.fw_brand_hero, Pair(738, 152)),
            Triple("line-badge-48", R.drawable.fw_brand_badge, Pair(48, 48)),
            Triple("solid-badge-24", R.drawable.fw_brand_badge_small, Pair(24, 24)),
            Triple("compact-mark-24", R.drawable.ic_fearless_bird, Pair(24, 24)),
            Triple("legacy-welcome", R.drawable.drawable_fearless_logo, Pair(738, 152)),
            Triple("neutral-artwork", R.drawable.drawable_fearless_bird, Pair(112, 110))
        )
        resources.forEach { (name, id, size) ->
            val drawable = AppCompatResources.getDrawable(context, id)!!
            val bitmap = render(drawable, size.first, size.second)
            assertTrue("$name is empty", visiblePixels(bitmap) > size.first)
            if (name.contains("badge")) {
                assertTrue("$name has no white mark", whitePixels(bitmap) > 10)
            }
            save(name, bitmap)
        }
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue("Merged launcher is not adaptive", icon is AdaptiveIconDrawable)
        save("adaptive-launcher-108", render(icon, 324, 324))
        val foreground = (icon as AdaptiveIconDrawable).foreground
        val mark = render(foreground, 108, 108)
        assertTrue("Adaptive foreground mark is empty", visiblePixels(mark) > 150)
        for (y in 0 until 108) for (x in 0 until 108) {
            if (Color.alpha(mark.getPixel(x, y)) > 200) {
                assertTrue("Foreground leaves adaptive safe circle at $x/$y", (x - 54) * (x - 54) + (y - 54) * (y - 54) <= 33 * 33)
            }
        }
        save("adaptive-foreground-safe-zone", mark)
        if (Build.VERSION.SDK_INT >= 33) {
            val monochrome = render(icon.monochrome!!, 108, 108)
            assertTrue("Themed launcher mark is empty", visiblePixels(monochrome) > 150)
            save("adaptive-monochrome", monochrome)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val theme = ContextThemeWrapper(context, jp.co.soramitsu.app.R.style.Theme_Soramitsu_Fearless_Starting)
            val attributes = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowSplashScreenAnimatedIcon))
            try {
                assertEquals(R.drawable.fw_brand_badge, attributes.getResourceId(0, 0))
                save("android12-starting-icon", render(attributes.getDrawable(0)!!, 288, 288))
            } finally { attributes.recycle() }
        }
    }

    @Test fun nativeNetworkMonogramsLoadThroughProductionCoilRequestWithoutNetwork() = runBlocking {
        val loader = ImageLoader(context)
        try {
            for (name in listOf("bitcoin", "solana")) {
                val request = getImageRequest(context, "file:///android_asset/native-icons/$name.svg")
                    .newBuilder().allowHardware(false).size(128).build()
                val result = loader.execute(request)
                assertTrue("$name local asset did not load: $result", result is SuccessResult)
                val bitmap = render((result as SuccessResult).drawable, 128, 128)
                val bright = (0 until 128).sumOf { y -> (0 until 128).count { x ->
                    val pixel = bitmap.getPixel(x, y)
                    Color.alpha(pixel) > 200 && Color.red(pixel) > 200 && Color.green(pixel) > 200 && Color.blue(pixel) > 200
                } }
                assertTrue("$name monogram text is missing", bright > 150)
                save("offline-$name-monogram", bitmap)
            }
        } finally { loader.shutdown() }
    }

    private fun render(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun whitePixels(bitmap: Bitmap): Int = (0 until bitmap.height).sumOf { y ->
        (0 until bitmap.width).count { x ->
            val pixel = bitmap.getPixel(x, y)
            Color.alpha(pixel) > 200 && Color.red(pixel) > 200 && Color.green(pixel) > 200 && Color.blue(pixel) > 200
        }
    }

    private fun visiblePixels(bitmap: Bitmap): Int = (0 until bitmap.height).sumOf { y ->
        (0 until bitmap.width).count { x -> Color.alpha(bitmap.getPixel(x, y)) > 100 }
    }

    private fun save(name: String, bitmap: Bitmap) {
        val folder = context.getExternalFilesDir("brand-assets-evidence")!!.apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
