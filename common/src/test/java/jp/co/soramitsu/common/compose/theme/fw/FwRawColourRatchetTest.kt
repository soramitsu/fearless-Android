package jp.co.soramitsu.common.compose.theme.fw

import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design-system 14.0: exact, counted exceptions; no production palette changes. */
class FwRawColourRatchetTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "design/fw.tokens.json").isFile }
    private val baselinePath = "design/fw.raw-colour-baseline.android.json"
    private val expression = Regex("\\bColor\\s*\\(\\s*0[xX][0-9a-fA-F_]+[uUlL]*")

    private data class Site(val path: String, val source: String)
    private data class Baseline(val sourceCommit: String, val sites: Map<Site, Int>)

    private fun eligible(path: String): Boolean =
        Regex("^[^/]+/src/.+\\.(kt|java)$").matches(path) && "/compose/theme/fw/" !in path

    private fun sites(path: String, text: String): Map<Site, Int> {
        if (!eligible(path)) return emptyMap()
        return expression.findAll(text).map { match ->
            val start = text.lastIndexOf('\n', match.range.first - 1) + 1
            val end = text.indexOf('\n', match.range.last + 1).let { if (it < 0) text.length else it }
            Site(path, text.substring(start, end).trim())
        }.groupingBy { it }.eachCount()
    }

    private fun scan(directory: File): Map<Site, Int> {
        val result = mutableMapOf<Site, Int>()
        for (module in directory.listFiles().orEmpty().filter { it.isDirectory }) {
            val src = File(module, "src")
            if (!src.isDirectory) continue
            require(!Files.isSymbolicLink(module.toPath())) { "Source module symlink: $module" }
            src.walkTopDown().onEnter {
                require(!Files.isSymbolicLink(it.toPath())) { "Source directory symlink: $it" }
                true
            }.forEach { file ->
                require(!Files.isSymbolicLink(file.toPath())) { "Source file symlink: $file" }
                if (file.isFile) {
                    val path = file.relativeTo(directory).invariantSeparatorsPath
                    if (eligible(path)) result.putAll(sites(path, file.readText()))
                }
            }
        }
        return result
    }

    private fun parse(text: String): Baseline {
        val value = JsonParser.parseString(text).asJsonObject
        require(value.get("schemaVersion").asBigDecimal.intValueExact() == 1)
        val commit = value.get("sourceCommit").asString
        require(Regex("[0-9a-f]{40}").matches(commit))
        val result = mutableMapOf<Site, Int>()
        for (entry in value.getAsJsonArray("sites")) {
            val row = entry.asJsonObject
            val site = Site(row.get("path").asString, row.get("source").asString)
            val count = row.get("count").asBigDecimal.intValueExact()
            require(eligible(site.path) && site.path.split('/').none { it == "." || it == ".." })
            require(site.source.isNotEmpty() && site.source.trim() == site.source && count > 0)
            require(sites(site.path, site.source).isNotEmpty())
            require(result.put(site, count) == null) { "Duplicate colour exception: $site" }
        }
        return Baseline(commit, result)
    }

    private fun assertShrinks(current: Baseline, previous: Baseline) {
        assertEquals("Original inventory provenance must not change", previous.sourceCommit, current.sourceCommit)
        val added = current.sites.filter { (site, count) -> count > (previous.sites[site] ?: 0) }
        assertTrue("Raw-colour baseline may only shrink: $added", added.isEmpty())
    }

    private fun git(vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments).directory(root).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "Git colour-baseline check failed: $output" }
        return output.trim()
    }

    @Test fun everySourceLiteralMatchesAnExactCountedLegacyException() {
        val expected = parse(File(root, baselinePath).readText())
        assertEquals("Use theme roles for additions; remove obsolete exceptions", expected.sites, scan(root))
    }

    @Test fun baselineMayOnlyShrinkFromTheActualGitBase() {
        val configured = System.getenv("FW_COLOUR_BASE_REF").orEmpty()
        require(configured.isEmpty() || Regex("[0-9a-f]{40}").matches(configured))
        val ref = configured.takeUnless { it.isEmpty() || it.all { c -> c == '0' } } ?: "HEAD^"
        val commit = git("rev-parse", "--verify", "$ref^{commit}")
        if (git("ls-tree", "--name-only", commit, "--", baselinePath).isEmpty()) {
            println("Raw-colour baseline initial adoption: no earlier allowlist in $commit")
        } else {
            assertShrinks(parse(File(root, baselinePath).readText()), parse(git("show", "$commit:$baselinePath")))
        }
    }

    @Test fun literalScannerCoversWhitespaceMultilineCaseAndDuplicateOccurrences() {
        val path = "feature-new/src/main/kotlin/Example.kt"
        val text = "val a = Color (\n  0xFFaAbBcC)\nval b = Color(0XFF00_0011L); val c = Color(0xff123456)"
        assertEquals(3, sites(path, text).values.sum())
        assertTrue(sites(path, text).keys.any { "Color (\n  0xFFaAbBcC" in it.source })
        val repeated = "val a = Color(0xff123456)"
        assertEquals(2, sites(path, "$repeated\n$repeated").values.single())
    }

    @Test fun exemptionsAreLimitedToTheNamedThemeDirectory() {
        val source = "val a = Color(0xff123456)"
        assertTrue(sites("common/src/main/kotlin/compose/theme/fw/Generated.kt", source).isEmpty())
        assertEquals(1, sites("common/src/main/kotlin/compose/theme/fw-old/Example.kt", source).size)
        assertEquals(1, sites("new-module/src/custom/java/Example.java", source).size)
        assertEquals(1, sites("new-module/src/androidTest/kotlin/Example.kt", source).size)
        assertTrue(sites("module/build/Example.kt", source).isEmpty())
        assertTrue(sites("module/src/main/kotlin/Example.kt", "val a = colors.bgBase").isEmpty())
    }

    @Test fun removalsCannotFundNewColoursMovedSitesOrDuplicateCopies() {
        val path = "module/src/main/kotlin/Example.kt"
        val previous = Baseline("a".repeat(40), sites(path, "val a = Color(0xff123456)\nval b = Color(0xff654321)"))
        val retained = "val a = Color(0xff123456)"
        assertShrinks(Baseline(previous.sourceCommit, sites(path, retained)), previous)
        for (next in listOf(
            sites(path, retained.replace("123456", "123455")),
            sites(path, "$retained\n$retained"),
            sites("module/src/main/kotlin/Moved.kt", retained)
        )) {
            val failure = runCatching { assertShrinks(Baseline(previous.sourceCommit, next), previous) }.exceptionOrNull()
            assertTrue(failure is AssertionError)
        }
    }

    @Test fun retiredExceptionsCannotBeReintroduced() {
        val old = Baseline("a".repeat(40), sites("module/src/main/java/Example.kt", "val a = Color(0xff123456)"))
        val retired = old.copy(sites = emptyMap())
        assertShrinks(retired, old)
        assertTrue(runCatching { assertShrinks(old, retired) }.exceptionOrNull() is AssertionError)
    }

    @Test fun malformedAndDuplicateExceptionsFailClosed() {
        val valid = """{"schemaVersion":1,"sourceCommit":"${"a".repeat(40)}","sites":[{"path":"module/src/main/java/Example.kt","source":"val a = Color(0xff123456)","count":1}]}"""
        assertEquals(1, parse(valid).sites.size)
        for (invalid in listOf(
            valid.replace("\"count\":1", "\"count\":0"),
            valid.replace("\"count\":1", "\"count\":1.5"),
            valid.replace("module/src/main", "module/src/../main"),
            valid.replace("Color(0xff123456)", "*"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.5"),
            valid.replace("a".repeat(40), "HEAD"),
            valid.replace(",\"sites\":[", ",\"sites\":[" + valid.substringAfter("\"sites\":[").substringBeforeLast("]}") + ",")
        )) assertTrue(runCatching { parse(invalid) }.isFailure)
    }

    @Test fun filesystemScanIncludesNewModulesAndRejectsSourceSymlinks() {
        val directory = Files.createTempDirectory("fearless-colours-").toFile()
        try {
            val source = File(directory, "new-module/src/flavour/kotlin/Example.kt")
            requireNotNull(source.parentFile).mkdirs()
            source.writeText("val a = Color(0xff123456)")
            assertEquals(1, scan(directory).size)
            Files.createSymbolicLink(File(source.parentFile, "Linked.kt").toPath(), source.toPath())
            assertTrue(runCatching { scan(directory) }.isFailure)
        } finally {
            directory.deleteRecursively()
        }
    }
}
