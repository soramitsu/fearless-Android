package jp.co.soramitsu.common.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.IllegalFormatException
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every translation of a formatted string must format with the arguments the English text takes.
 *
 * Code passes one argument list whatever the language. A translation that asks for one more argument, or names a
 * conversion Java does not know, throws inside `getString`/`getQuantityString` and closes the app for that language
 * only: Japanese "%dネット\n%dネットワーク" closed Manage assets in 4.2.0, and Indonesian "ONE %d Jam\nOTHER %d Jam" closed
 * every staking page that shows hours. Both had `formatted="false"`, which also hid them from lint.
 */
class TranslatedFormatArgumentsTest {

    @Test
    fun `every translation formats with the arguments of the English text`() {
        val modules = resourceDirectories()
        assertTrue("no string resources found from ${File("").absolutePath}", modules.isNotEmpty())

        val failures = mutableListOf<String>()
        var checked = 0
        modules.forEach { res ->
            File(res, "values").listFiles { file -> file.extension == "xml" }.orEmpty().sorted().forEach { defaults ->
                val english = Texts.read(defaults)
                val locales = res.listFiles { dir -> dir.isDirectory && dir.name.startsWith("values-") }.orEmpty().sorted()
                    .map { File(it, defaults.name) }.filter(File::exists)
                (listOf(defaults) + locales).forEach { file ->
                    val texts = if (file == defaults) english else Texts.read(file)
                    texts.entries.forEach { (name, variants) ->
                        // The arguments are those of every English variant together: "%d hour" and "%d hours" take one.
                        val arguments = english.entries[name]?.let(::argumentsOf) ?: return@forEach
                        variants.forEach { (quantity, text) ->
                            checked++
                            formatFailure(text, arguments)?.let {
                                failures += "${file.relativeTo(res.parentFile.parentFile.parentFile.parentFile)}: $name$quantity " +
                                    "\"$text\" does not take ${arguments.toList()}: $it"
                            }
                        }
                    }
                }
            }
        }

        assertTrue("checked too little ($checked): the scan no longer finds the resources", checked > MINIMUM_CHECKED)
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun `the check refuses the translations that reached users`() {
        val oneNumber = argumentsOf(listOf("" to "%d hour", "" to "%d hours"))!!
        assertEquals(null, formatFailure("%d jam", oneNumber))
        assertEquals(null, formatFailure("%1\$d時間", oneNumber))
        // Two numbers where the code passes one.
        assertTrue(formatFailure("ONE %d Jam\\nOTHER %d Jam", oneNumber) != null)
        assertTrue(formatFailure("%dネット\\n%dネットワーク", oneNumber) != null)
        // %i is C, not Java.
        assertTrue(formatFailure("+ %i diğer", oneNumber) != null)
        // Text where English has a number.
        assertTrue(formatFailure("%s jam", arrayOf<Any>(1)) == null) // %s accepts anything
        assertTrue(formatFailure("%d jam", arrayOf<Any>("one")) != null)
        // English without arguments is shown as written, so it is not formatted and not judged.
        assertEquals(null, argumentsOf(listOf("" to "If the price changes more than 0.5% your transaction will revert.")))
        assertEquals(null, argumentsOf(listOf("" to "Elected")))
    }

    /** The module resource folders: this test runs in `common`, its siblings hold the rest. */
    private fun resourceDirectories(): List<File> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle").exists() || File(it, "settings.gradle.kts").exists() }
        return root.listFiles { dir -> dir.isDirectory }.orEmpty().sorted()
            .map { File(it, "src/main/res") }.filter { File(it, "values").isDirectory }
    }

    /**
     * Sample arguments for a string, from its own specifiers, or null when it takes none or is not a format at all
     * (a bare "0.5% your"): such text is read with `getString(id)` and never formatted.
     */
    private fun argumentsOf(variants: List<Pair<String, String>>): Array<Any>? {
        val conversions = sortedMapOf<Int, Char>()
        variants.forEach { (_, text) ->
            var ordinal = 0
            SPECIFIER.findAll(text).forEach { match ->
                val conversion = match.groupValues[2].first().lowercaseChar()
                if (conversion == '%' || conversion == 'n') return@forEach
                val index = match.groupValues[1].toIntOrNull() ?: ++ordinal
                // %s takes anything, so a stricter conversion for the same argument decides its type.
                if (conversions[index].let { it == null || it == 's' }) conversions[index] = conversion
            }
        }
        if (conversions.isEmpty()) return null
        val arguments = Array<Any>(conversions.lastKey()) { index ->
            when (conversions[index + 1]) {
                'd', 'o', 'x' -> 1
                'e', 'f', 'g', 'a' -> 1.0
                'c' -> 'c'
                'b' -> true
                else -> "text"
            }
        }
        return arguments.takeIf { args -> variants.all { formatFailure(it.second, args) == null } }
    }

    private fun formatFailure(text: String, arguments: Array<Any>): String? = try {
        String.format(Locale.ROOT, text, *arguments)
        null
    } catch (failure: IllegalFormatException) {
        failure.toString()
    }

    /** A resource file's strings and plurals: name to its (quantity label, text) variants. */
    private class Texts(val entries: Map<String, List<Pair<String, String>>>) {
        companion object {
            fun read(file: File): Texts {
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
                val entries = linkedMapOf<String, List<Pair<String, String>>>()
                val children = document.childNodes
                for (index in 0 until children.length) {
                    val node = children.item(index) as? Element ?: continue
                    val name = node.getAttribute("name")
                    when (node.tagName) {
                        "string" -> entries[name] = listOf("" to node.textContent)
                        "plurals" -> {
                            val items = node.getElementsByTagName("item")
                            entries[name] = (0 until items.length).map { items.item(it) as Element }
                                .map { "[${it.getAttribute("quantity")}]" to it.textContent }
                        }
                    }
                }
                return Texts(entries)
            }
        }
    }

    private companion object {
        /** `%[index$][flags][width][.precision]conversion`, as java.util.Formatter reads it. */
        val SPECIFIER = Regex("""%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])""")

        // The app has several thousand translated strings; a scan that finds a handful has lost its way.
        const val MINIMUM_CHECKED = 1_000
    }
}
