package jp.co.soramitsu.coredb

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Production code must never be able to drop the user's database.
 *
 * Master, develop and 4.2.0 still call `fallbackToDestructiveMigration()`, so a
 * missing upgrade step or an installed newer schema wipes every wallet row. This
 * tree removed it; Room now fails closed and the startup gate keeps the data.
 * The scan covers every shipped source set of every module, not only core-db,
 * so a second database or a reset helper cannot reintroduce the wipe.
 */
class NoDestructiveMigrationSourceTest {

    @Test
    fun `shipped sources never drop the database`() {
        val root = repositoryRoot()
        val sources = shippedSources(root)

        assertTrue(
            "The scan no longer reaches AppDatabase.kt; fix the source walk",
            sources.any { it.invariantPath(root) == APP_DATABASE_SOURCE }
        )

        val violations = sources.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                FORBIDDEN_CALLS.firstOrNull { it in line }?.let { call ->
                    "${source.invariantPath(root)}:${index + 1} uses $call"
                }
            }
        }

        assertTrue(
            "Destructive database calls in shipped code; add an explicit " +
                "migration instead:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    private fun shippedSources(root: File): List<File> {
        return root.walkTopDown()
            .onEnter { directory ->
                directory == root ||
                    (directory.name !in SKIPPED_DIRECTORIES && !directory.isTestSourceSet())
            }
            .filter { it.isFile && it.extension in SOURCE_EXTENSIONS }
            .filter { "/src/" in "/${it.invariantPath(root)}" }
            .toList()
    }

    private fun File.isTestSourceSet(): Boolean {
        return parentFile?.name == "src" &&
            TEST_SOURCE_SET_PREFIXES.any { name.startsWith(it) }
    }

    private fun File.invariantPath(root: File): String {
        return relativeTo(root).invariantSeparatorsPath
    }

    private fun repositoryRoot(): File {
        return generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }.firstOrNull {
            File(it, "settings.gradle").isFile && File(it, APP_DATABASE_SOURCE).isFile
        } ?: error("Cannot locate the Fearless Android repository root")
    }

    private companion object {
        const val APP_DATABASE_SOURCE =
            "core-db/src/main/java/jp/co/soramitsu/coredb/AppDatabase.kt"

        // Every Room builder variant (OnDowngrade, From) starts with the first token.
        val FORBIDDEN_CALLS = listOf(
            "fallbackToDestructiveMigration",
            "clearAllTables(",
            "deleteDatabase("
        )
        val SOURCE_EXTENSIONS = setOf("kt", "java")
        val TEST_SOURCE_SET_PREFIXES = listOf("test", "androidTest", "sharedTest")
        val SKIPPED_DIRECTORIES = setOf("build", ".cxx", ".git", ".gradle", ".idea", "node_modules")
    }
}
