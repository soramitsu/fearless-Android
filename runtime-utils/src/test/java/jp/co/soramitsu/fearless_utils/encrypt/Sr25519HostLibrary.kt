package jp.co.soramitsu.fearless_utils.encrypt

import java.io.File
import java.lang.reflect.InvocationTargetException
import org.junit.Assume

/**
 * The host (desktop JVM) build of libsr25519java that Gradle builds from
 * runtime-utils/native/sr25519 and passes to unit tests through
 * `java.library.path`. Tests that need it are skipped with the reason when the
 * Rust toolchain is missing; release unit tests fail instead (see
 * runtime-utils/build.gradle).
 */
object Sr25519HostLibrary {
    const val PATH_PROPERTY = "sr25519.hostLibrary"
    const val UNAVAILABLE_PROPERTY = "sr25519.hostLibrary.unavailable"
    const val LEGACY_PATH_PROPERTY = "sr25519.legacyLibrary"

    fun file(): File? = System.getProperty(PATH_PROPERTY)?.let(::File)?.takeIf { it.isFile }

    fun legacyFile(): File? = System.getProperty(LEGACY_PATH_PROPERTY)?.let(::File)?.takeIf { it.isFile }

    /** Skips the calling test, with the reason, unless the host library was built. */
    fun assumeAvailable() {
        Assume.assumeTrue(
            "sr25519 host library unavailable: " +
                (System.getProperty(UNAVAILABLE_PROPERTY) ?: "run the tests through Gradle"),
            file() != null
        )
    }
}

/**
 * A private copy of the [Sr25519] JNI class bound to one library file, so two
 * builds of libsr25519java can run side by side in one JVM. Each instance
 * loads its own copy of the library file in its own class loader.
 */
class IsolatedSr25519(library: File) {

    private val type: Class<*>

    init {
        val copy = File.createTempFile("sr25519-", "-" + library.name)
        copy.deleteOnExit()
        library.copyTo(copy, overwrite = true)
        val classBytes = requireNotNull(javaClass.classLoader?.getResourceAsStream(CLASS_RESOURCE)) {
            "Compiled Sr25519 class is missing from the test classpath"
        }.use { it.readBytes() }
        val loader = object : ClassLoader(null) {
            override fun findClass(name: String): Class<*> {
                if (name != CLASS_NAME) throw ClassNotFoundException(name)
                return defineClass(name, classBytes, 0, classBytes.size)
            }

            override fun findLibrary(libname: String): String? =
                if (libname == LIBRARY_NAME) copy.absolutePath else null
        }
        type = Class.forName(CLASS_NAME, true, loader)
    }

    fun keypairFromSeed(seed: ByteArray?): ByteArray? = call("keypairFromSeed", seed) as ByteArray?

    fun deriveKeypairHard(keypair: ByteArray?, chainCode: ByteArray?): ByteArray? =
        call("deriveKeypairHard", keypair, chainCode) as ByteArray?

    fun deriveKeypairSoft(keypair: ByteArray?, chainCode: ByteArray?): ByteArray? =
        call("deriveKeypairSoft", keypair, chainCode) as ByteArray?

    fun derivePublicSoft(publicKey: ByteArray?, chainCode: ByteArray?): ByteArray? =
        call("derivePublicSoft", publicKey, chainCode) as ByteArray?

    fun sign(publicKey: ByteArray?, secret: ByteArray?, message: ByteArray?): ByteArray? =
        call("sign", publicKey, secret, message) as ByteArray?

    fun verify(signature: ByteArray?, message: ByteArray?, publicKey: ByteArray?): Boolean =
        call("verify", signature, message, publicKey) as Boolean

    fun toEd25519Bytes(secret: ByteArray?): ByteArray? = call("toEd25519Bytes", secret) as ByteArray?

    fun fromEd25519Bytes(expanded: ByteArray?): ByteArray? = call("fromEd25519Bytes", expanded) as ByteArray?

    private fun call(name: String, vararg arguments: ByteArray?): Any? {
        val method = type.getMethod(name, *Array(arguments.size) { ByteArray::class.java })
        try {
            return method.invoke(null, *arguments)
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
    }

    private companion object {
        const val CLASS_NAME = "jp.co.soramitsu.fearless_utils.encrypt.Sr25519"
        const val CLASS_RESOURCE = "jp/co/soramitsu/fearless_utils/encrypt/Sr25519.class"
        const val LIBRARY_NAME = "sr25519java"
    }
}
