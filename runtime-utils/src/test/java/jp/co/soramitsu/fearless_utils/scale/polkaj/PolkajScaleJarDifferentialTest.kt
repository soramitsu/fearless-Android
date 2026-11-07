package jp.co.soramitsu.fearless_utils.scale.polkaj

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

/**
 * Runs the same exercise on the old polkaj-scale-0.2.3.jar, loaded in its own class loader, and on the codec built
 * from source, and requires identical transcripts. Removed with the jar once the golden transcript took over.
 */
class PolkajScaleJarDifferentialTest {

    private val jar = File("libs/polkaj-scale-0.2.3.jar")

    @Test
    fun `source codec matches the jar on random input for every reader and writer`() {
        for ((seed, rounds) in listOf(20261107L to 400, 7L to 300, -42L to 300)) {
            val legacy = runOnJar(seed, rounds)
            val ported = PolkajScaleExercise.run(seed, rounds)
            assertEquals(legacy.size, ported.size)
            legacy.indices.forEach { index -> assertEquals("seed $seed line $index", legacy[index], ported[index]) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun runOnJar(seed: Long, rounds: Int): List<String> {
        assertTrue("missing ${jar.absolutePath}", jar.isFile)
        val exerciseClasses = PolkajScaleExercise::class.java.protectionDomain.codeSource.location
        // Only the bootstrap loader above it: the codec and the exercise come from the jar, never from this module.
        // (ClassLoader.getPlatformClassLoader() is not in the Android API the unit tests compile against.)
        URLClassLoader(arrayOf(jar.toURI().toURL(), exerciseClasses), null).use { loader ->
            val legacyReader = loader.loadClass(ScaleCodecReader::class.java.name)
            assertEquals(jar.toURI().toURL(), legacyReader.protectionDomain.codeSource.location)
            assertNotEquals(ScaleCodecReader::class.java, legacyReader)
            val exercise = loader.loadClass(PolkajScaleExercise::class.java.name)
            assertNotEquals(PolkajScaleExercise::class.java, exercise)
            return exercise.getMethod("run", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(null, seed, rounds) as List<String>
        }
    }
}
