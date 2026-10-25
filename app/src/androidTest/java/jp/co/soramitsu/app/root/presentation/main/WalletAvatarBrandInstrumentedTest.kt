package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import jp.co.soramitsu.fearless_utils.icon.Circle
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Renderer evidence runs against both the prior and candidate shared library. Public IDs only. */
@RunWith(AndroidJUnit4::class)
class WalletAvatarBrandInstrumentedTest {
    @Test fun addressDerivedPatternsRemainDeterministicAndRenderAtWalletSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val label = InstrumentationRegistry.getArguments().getString("avatarEvidenceLabel") ?: "current"
        require(label.matches(Regex("[a-z0-9-]+")))
        val folder = instrumentation.targetContext.getExternalFilesDir("avatar-brand-$label")!!.apply { mkdirs() }
        val generator = IconGenerator()
        val circlesMethod = IconGenerator::class.java.getDeclaredMethod(
            "generateIconCircles", ByteArray::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val cases = listOf(ByteArray(32), ByteArray(32) { it.toByte() }, ByteArray(32) { (255 - it).toByte() })
        val rows = JSONArray()
        val hashes = mutableSetOf<String>()
        for ((index, id) in cases.withIndex()) for (alternative in listOf(false, true)) {
            @Suppress("UNCHECKED_CAST")
            val circles = circlesMethod.invoke(generator, id, alternative, 0x1C1F3D) as List<Circle>
            val geometry = JSONArray(circles.map { circle -> JSONObject()
                .put("x", circle.x).put("y", circle.y).put("radius", circle.radius).put("color", circle.colorString) })
            for (size in listOf(32, 64)) {
                val name = "id$index-alt$alternative-$size"
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                generator.getSvgImage(id, size, alternative, 0x1C1F3D).apply { setBounds(0, 0, size, size) }.draw(Canvas(bitmap))
                val pixels = IntArray(size * size)
                bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                assertTrue("$name empty avatar", pixels.count { (it ushr 24) > 100 } > size * size / 4)
                val digest = MessageDigest.getInstance("SHA-256")
                pixels.forEach { p -> digest.update(byteArrayOf((p ushr 24).toByte(), (p ushr 16).toByte(), (p ushr 8).toByte(), p.toByte())) }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                hashes.add(hash)
                File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val second = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                generator.getSvgImage(id, size, alternative, 0x1C1F3D).apply { setBounds(0, 0, size, size) }.draw(Canvas(second))
                assertTrue("$name is unstable between renders", bitmap.sameAs(second))
                rows.put(JSONObject().put("name", name).put("size", size).put("circles", geometry).put("pixelSha256", hash))
                bitmap.recycle()
                second.recycle()
            }
        }
        assertEquals("Address patterns must remain distinct", 12, hashes.size)
        File(folder, "observations.json").writeText(JSONObject().put("label", label).put("cases", rows).toString(2))
    }
}
