package jp.co.soramitsu.common.data.secrets

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString

/**
 * Golden vectors written by a historical crypto library, as committed under
 * `src/test/resources/crypto-golden/` by
 * `tools/upgrade-fixtures/android/convert_parity_vectors.py`.
 *
 * Every vector file names its library version and provenance; tests report a
 * mismatch as library, function and input so a regression can be traced to
 * the exact historical behaviour it breaks.
 */
internal object CryptoGoldenVectors {

    const val SCHEMA = "fearless-upgrade-matrix/crypto-golden/v1"

    /**
     * Library versions with committed vectors. TM-A2 adds the older
     * fearless-utils and shared-features releases next to this one.
     */
    val LIBRARIES = listOf("shared-features-1.1.1.44-FLW")

    fun crypto(library: String): JsonObject = load("crypto-golden/$library.json")

    fun secretEncodings(library: String): JsonObject =
        load("crypto-golden/secret-encodings-$library.json")

    private fun load(resource: String): JsonObject {
        val stream = CryptoGoldenVectors::class.java.classLoader?.getResourceAsStream(resource)
            ?: error("$resource is missing from test resources")
        val document = stream.use { JsonParser.parseReader(InputStreamReader(it, Charsets.UTF_8)).asJsonObject }
        check(document["schema"].asString == SCHEMA) { "$resource has schema ${document["schema"]}" }
        check(document["synthetic_only"].asBoolean) { "$resource is not marked synthetic-only" }
        return document
    }
}

internal fun JsonObject.objects(member: String): List<JsonObject> =
    getAsJsonArray(member).map(JsonElement::getAsJsonObject)

internal fun JsonObject.string(member: String): String = get(member).asString

internal fun JsonObject.nullableString(member: String): String? =
    get(member)?.takeUnless(JsonElement::isJsonNull)?.asString

internal fun JsonObject.bytes(member: String): ByteArray = string(member).fromHex()

internal fun JsonObject.nullableBytes(member: String): ByteArray? = nullableString(member)?.fromHex()

internal fun JsonArray.strings(): List<String> = map(JsonElement::getAsString)

internal fun ByteArray?.hex(): String? = this?.toHexString(withPrefix = true)

/**
 * Historical exception names are recorded relative to the library root
 * (`encrypt.junction.JunctionDecoder$DecodingError$InvalidStart`), so they
 * compare equal across `shared_utils` and `fearless_utils`.
 */
internal fun Throwable.libraryRelativeName(): String {
    val name = javaClass.name
    return LIBRARY_ROOTS.firstOrNull(name::startsWith)?.let { name.removePrefix(it) } ?: name
}

private val LIBRARY_ROOTS = listOf(
    "jp.co.soramitsu.fearless_utils.",
    "jp.co.soramitsu.shared_utils."
)

/** Collects every mismatch of one vector family and fails once, listing all of them. */
internal class GoldenMismatches(private val library: String, private val family: String) {

    private val mismatches = mutableListOf<String>()
    var checked = 0
        private set

    fun check(function: String, input: String, block: () -> Unit) {
        checked++
        try {
            block()
        } catch (failure: Throwable) {
            mismatches += "$library $family $function($input): ${failure.message ?: failure.javaClass.name}"
        }
    }

    fun assertNone(minimumChecked: Int = 1) {
        if (mismatches.isNotEmpty()) {
            throw AssertionError(
                "${mismatches.size} of $checked $family vectors differ from $library:\n" +
                    mismatches.joinToString("\n")
            )
        }
        if (checked < minimumChecked) {
            throw AssertionError("$library $family: only $checked vectors checked, expected $minimumChecked")
        }
    }
}

internal fun expectEqual(expected: Any?, actual: Any?, what: String) {
    if (expected != actual) {
        throw AssertionError("$what: expected $expected, got $actual")
    }
}
