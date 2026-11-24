package jp.co.soramitsu.common.address

import android.graphics.Bitmap
import android.graphics.Canvas
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Renders wallet and address icons with the real [IconGenerator]. 4.3.0-4.3.7 crashed on every open of the
 * home screen for wallets holding only an Ethereum account, because the EVM wallet icon came from a
 * 19-byte seed and the square layout read byte 31.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalletIconRenderingTest {
    private val resources = mockk<ResourceManager>().also {
        every { it.measureInPx(any()) } answers { firstArg<Int>() * 2 }
        every { it.getColor(any()) } returns -1
    }
    private val generator = StatelessAddressIconGenerator(IconGenerator(), resources)

    @Test
    fun shouldDrawTheWalletIconOfEveryEcosystem(): Unit = runBlocking {
        WalletEcosystem.entries.forEach { ecosystem ->
            assertVisible(ecosystem.name, generator.createWalletIcon(ecosystem, AddressIconGenerator.SIZE_MEDIUM))
        }
    }

    @Test
    fun shouldDrawAnEthereumOnlyWalletTheWayTheHomeScreenAsks(): Unit = runBlocking {
        val evmOnly = mapOf(WalletEcosystem.Ethereum to "0x" + "ab".repeat(20))
        val model = generator.createAddressModel(evmOnly, AddressIconGenerator.SIZE_MEDIUM, "EVM wallet")
        assertVisible("EVM-only wallet", model.image)
    }

    @Test
    fun shouldDrawEthereumIconsFromTwentyByteAccountIds(): Unit = runBlocking {
        val accountId = ByteArray(20) { (it * 11).toByte() }
        assertVisible("20-byte account id", generator.createEthereumAddressIcon(accountId, AddressIconGenerator.SIZE_BIG))
    }

    private fun assertVisible(label: String, drawable: android.graphics.drawable.PictureDrawable) {
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(label, pixels.any { it ushr 24 != 0 })
    }
}
