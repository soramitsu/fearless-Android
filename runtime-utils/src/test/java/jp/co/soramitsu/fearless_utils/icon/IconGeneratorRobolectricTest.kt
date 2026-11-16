package jp.co.soramitsu.fearless_utils.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.PictureDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconGeneratorRobolectricTest {
    private val generator = IconGenerator()

    @Test
    fun substrateIdenticonsAreVisibleDeterministicAndDependOnTheAccount() {
        val zero = ByteArray(32)
        val first = render(generator.getSvgImage(zero, 64))
        val repeat = render(generator.getSvgImage(zero, 64))
        val other = render(generator.getSvgImage(ByteArray(32) { (it + 1).toByte() }, 64))
        assertTrue(first.sameAs(repeat))
        assertFalse(first.sameAs(other))
        assertTrue(pixels(first).any { it ushr 24 != 0 })
    }

    @Test
    fun alternativeLayoutAndEthereumIconsRenderAtTheirRequestedSize() {
        val account = ByteArray(32) { (it * 7).toByte() }
        val normal = render(generator.getSvgImage(account, 48))
        val alternative = render(generator.getSvgImage(account, 48, true, 0x123456))
        assertFalse(normal.sameAs(alternative))
        for (lastByte in listOf(-128, 0, 127)) {
            account[31] = lastByte.toByte()
            val drawable = generator.generateEthereumAddressIcon(account, 64)
            assertEquals(64, drawable.intrinsicWidth)
            assertEquals(64, drawable.intrinsicHeight)
            val ethereum = render(drawable)
            assertTrue(pixels(ethereum).any { it ushr 24 != 0 })
            assertTrue(ethereum.sameAs(render(generator.generateEthereumAddressIcon(account, 64))))
        }
    }

    @Test
    fun drawableFitsItsBoundsAndRestoresTheCanvas() {
        val drawable = generator.getSvgImage(ByteArray(32) { it.toByte() }, 64)
        val target = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        val initialSaveCount = canvas.saveCount
        drawable.bounds = Rect(8, 12, 40, 60)
        drawable.draw(canvas)
        assertEquals(initialSaveCount, canvas.saveCount)
        assertTrue((12 until 60).any { y -> (8 until 40).any { x -> target.getPixel(x, y) ushr 24 != 0 } })
        for (y in 0 until 80) {
            for (x in 0 until 80) {
                if (!drawable.bounds.contains(x, y)) assertEquals(0, target.getPixel(x, y))
            }
        }
    }

    @Test
    fun emptyBoundsAndAbsentPictureLeaveTheDestinationUntouched() {
        val drawable = generator.getSvgImage(ByteArray(32), 32)
        val target = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        drawable.draw(canvas)
        assertTrue(pixels(target).all { it == 0 })
        drawable.setBounds(0, 0, 32, 32)
        drawable.picture = null
        drawable.draw(canvas)
        assertTrue(pixels(target).all { it == 0 })
    }

    private fun render(drawable: PictureDrawable): Bitmap {
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
}
