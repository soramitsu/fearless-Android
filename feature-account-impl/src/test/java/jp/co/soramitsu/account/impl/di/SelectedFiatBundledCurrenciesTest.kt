package jp.co.soramitsu.account.impl.di

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.SelectedFiat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's own wiring over the repository's real currency list, in the layout `bundleRemoteConfigSnapshot` puts into
 * the assets. If this breaks, money falls back to reading `usd0.09` with no crash, so nothing else would say so.
 */
class SelectedFiatBundledCurrenciesTest {
    private val production = "production/fiat/fiats.json"
    private val development = "development/fiat/fiats.json"

    private fun repositoryFile(path: String) = File("../remote-config/$path")

    private fun sha256(content: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

    private fun assets(pinned: Map<String, ByteArray>, stored: Map<String, ByteArray> = pinned): AssetManager {
        val index = pinned.entries.joinToString(",", "{\"files\":{", "}}") { (path, content) ->
            "\"$path\":\"${sha256(content)}\""
        }
        return mockk {
            every { open(any()) } answers {
                val name = firstArg<String>()
                val content = when {
                    name == "remote-config-snapshot/index.json" -> index.toByteArray()
                    name.startsWith("remote-config-snapshot/files/") -> stored[name.removePrefix("remote-config-snapshot/files/")]
                    else -> null
                }
                content?.inputStream() ?: throw FileNotFoundException(name)
            }
        }
    }

    private fun selectedFiat(assets: AssetManager): SelectedFiat {
        val context = mockk<Context> { every { this@mockk.assets } returns assets }
        return AccountFeatureModule().provideSelectedFiatUseCase(mockk<Preferences>(relaxed = true), context)
    }

    private fun bundled(vararg paths: String) =
        selectedFiat(assets(paths.associateWith { repositoryFile(it).readBytes() }))

    @Test fun everyCurrencyOnTheBundledListIsKnownWithItsSymbol() {
        val selected = bundled(production, development)
        val listed = JsonParser.parseString(repositoryFile(production).readText()).asJsonArray
        assertTrue(listed.size() > 0)
        listed.map { it.asJsonObject }.forEach { currency ->
            val id = currency.get("id").asString
            assertEquals(id, currency.get("symbol").asString, selected.metadata(id)?.symbol)
        }
        assertEquals("$", selected.metadata("usd")?.symbol)
        assertEquals("€", selected.metadata("eur")?.symbol)
        assertEquals("¥", selected.metadata("jpy")?.symbol)
    }

    @Test fun theListsTheWiringReadsAreOnesTheBuildBundles() {
        val bundledPaths = File("../common/build.gradle").readText()
        assertTrue(bundledPaths.contains("'$production'"))
        assertTrue(bundledPaths.contains("'$development'"))
    }

    @Test fun aBuildWithOnlyTheDevelopmentListStillKnowsTheSymbols() {
        assertEquals("€", bundled(development).metadata("eur")?.symbol)
    }

    @Test fun aCurrencyNotOnTheListIsNotKnown() {
        assertNull(bundled(production).metadata("not-a-currency"))
    }

    @Test fun aMissingSnapshotOrListMeansNoSymbolsNotACrash() {
        assertNull(bundled().metadata("usd"))
        val absent = mockk<AssetManager> { every { open(any()) } throws FileNotFoundException("absent") }
        assertNull(selectedFiat(absent).metadata("usd"))
    }

    @Test fun aFileThatDoesNotMatchItsPinIsNotTrusted() {
        val real = repositoryFile(production).readBytes()
        val changed = real.toString(Charsets.UTF_8).replace("\"$\"", "\"€\"").toByteArray()
        val selected = selectedFiat(assets(mapOf(production to real), mapOf(production to changed)))
        assertNull(selected.metadata("usd"))
    }

    @Test fun appStartReadsNoFileUntilASymbolIsNeeded() {
        val assets = assets(mapOf(production to repositoryFile(production).readBytes()))
        val selected = selectedFiat(assets)
        verify(exactly = 0) { assets.open(any()) }
        assertEquals("$", selected.metadata("usd")?.symbol)
        assertEquals("€", selected.metadata("eur")?.symbol)
        verify(exactly = 2) { assets.open(any()) }
    }
}
