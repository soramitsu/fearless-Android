package jp.co.soramitsu.app.root.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class WalletEntryIntentPolicyTest {

    @Test
    fun `launches without VIEW data carry no entry data`() {
        assertNull(admit(MAIN, null, null))
        assertNull(admit(null, null, null))
        assertNull(admit(VIEW, null, null))
        assertNull(admit(VIEW, null, JSON))
        // Allowlisted data is admitted only on the VIEW action the manifest
        // filters declare; an explicit Intent with another action gets none.
        assertNull(admit(MAIN, uri("fearless://buy-success"), null))
        assertNull(admit(SEND, uri("fearless://buy-success"), null))
        assertNull(
            admit(MAIN, uri("https://fearlesswallet.io/ton-connect/x"), null)
        )
        assertNull(
            admit(MAIN, uri("content://documents/wallet.json"), JSON)
        )
    }

    @Test
    fun `buy provider redirect is admitted with any path or query`() {
        listOf(
            "fearless://buy-success",
            "fearless://buy-success/",
            "fearless://buy-success?transactionId=1&status=completed",
            "fearless://buy-success/moonpay#done"
        ).forEach { link ->
            assertEquals(
                link,
                WalletEntryData.BUY_PROVIDER_REDIRECT,
                admit(VIEW, uri(link), null)
            )
        }
    }

    @Test
    fun `TON Connect link is admitted only on its verified host and path`() {
        assertEquals(
            WalletEntryData.TON_CONNECT_LINK,
            admit(VIEW, uri("https://fearlesswallet.io/ton-connect/"), null)
        )
        assertEquals(
            WalletEntryData.TON_CONNECT_LINK,
            admit(
                VIEW,
                uri("https://fearlesswallet.io/ton-connect/v2?id=1&r=x"),
                null
            )
        )
        listOf(
            "http://fearlesswallet.io/ton-connect/x",
            "https://fearlesswallet.io/ton-connect",
            "https://fearlesswallet.io/ton-connectx/",
            "https://fearlesswallet.io/",
            "https://fearlesswallet.io",
            "https://attacker.example/ton-connect/x",
            "https://fearlesswallet.io.attacker.example/ton-connect/x",
            "https://attacker@fearlesswallet.io/ton-connect/x",
            "https://fearlesswallet.io:8443/ton-connect/x",
            "https://FEARLESSWALLET.IO/ton-connect/x",
            "HTTPS://fearlesswallet.io/ton-connect/x"
        ).forEach { link ->
            assertNull(link, admit(VIEW, uri(link), null))
        }
    }

    @Test
    fun `JSON document needs a content URI and the JSON type`() {
        val document = uri("content://com.example.documents/root/wallet.json")

        assertEquals(
            WalletEntryData.JSON_DOCUMENT,
            admit(VIEW, document, JSON)
        )
        assertNull(admit(VIEW, document, null))
        assertNull(admit(VIEW, document, "text/plain"))
        assertNull(admit(VIEW, document, "application/json; charset=utf-8"))
        assertNull(admit(VIEW, uri("file:///sdcard/Download/wallet.json"), JSON))
        assertNull(admit(VIEW, uri("content:///wallet.json"), JSON))
        // A JSON type never turns another URI into a document; the handoff
        // forwards a type only together with an admitted document.
        assertEquals(
            WalletEntryData.BUY_PROVIDER_REDIRECT,
            admit(VIEW, uri("fearless://buy-success"), JSON)
        )
    }

    @Test
    fun `navigation deep links and look-alike URIs are rejected`() {
        listOf(
            "fearless-wallet-wc://request",
            "fearless://onboarding/importAccountFragment/0",
            "fearless://onboarding/createAccountFragment/SubstrateOrEvm",
            "fearless://staking/setControllerAccountFragment/chain",
            "fearless://defi/polkamarkt/1",
            "FEARLESS://buy-success",
            "fearless://BUY-SUCCESS",
            "fearless://buy%2Dsuccess",
            "fearless://buy-success.attacker",
            "fearless://user@buy-success",
            "fearless://buy-success:1",
            "fearless:buy-success",
            "intent://buy-success#Intent;scheme=fearless;end",
            "javascript://buy-success/%0Aalert(1)"
        ).forEach { link ->
            assertNull(link, admit(VIEW, uri(link), null))
            assertNull(link, admit(VIEW, uri(link), JSON))
        }
    }

    @Test
    fun `no deep link declared in a navigation graph is ever admitted`() {
        val deepLinks = navigationDeepLinks()

        // The app declares these today; the scan must not silently find none.
        assertTrue(deepLinks.any { it.pattern == "fearless-wallet-wc://request" })
        assertTrue(deepLinks.size >= EXPECTED_MINIMUM_DEEP_LINKS)

        deepLinks.forEach { deepLink ->
            // A deep link without a URI matches Intents that carry no data,
            // which is exactly what every non-admitted launch is reduced to.
            assertTrue(
                "${deepLink.source} declares a deepLink without app:uri",
                deepLink.pattern.isNotEmpty()
            )
        }
        deepLinks.forEach { deepLink ->
            candidateUris(deepLink.pattern).forEach { candidate ->
                assertNeverAdmitted(deepLink, candidate)
            }
        }
    }

    private fun assertNeverAdmitted(deepLink: NavigationDeepLink, candidate: WalletEntryUri) {
        val actions = listOf(VIEW, deepLink.action, MAIN, null)
        val types = listOf(null, JSON, deepLink.mimeType)
        actions.flatMap { action -> types.map { type -> action to type } }
            .forEach { (action, type) ->
                assertNull(
                    "${deepLink.source}: $candidate",
                    admit(action, candidate, type)
                )
            }

        // NavDeepLink matches its pattern case-insensitively, so a graph must
        // never declare an admitted origin in any case.
        val origin = Pair(
            candidate.scheme?.lowercase(Locale.ROOT),
            candidate.encodedAuthority?.lowercase(Locale.ROOT)
        )
        assertFalse(
            "${deepLink.source}: ${deepLink.pattern}",
            origin in ADMITTED_ORIGINS || origin.first == DOCUMENT_SCHEME
        )
    }

    private fun admit(
        action: String?,
        uri: WalletEntryUri?,
        type: String?
    ) = WalletEntryIntentPolicy.admit(action = action, uri = uri, type = type)

    // Mirrors android.net.Uri for these fixtures: raw, still-encoded parts.
    private fun uri(value: String): WalletEntryUri {
        val schemeEnd = value.indexOf(':')
        val scheme = value.substring(0, schemeEnd)
        val rest = value.substring(schemeEnd + 1)
        if (!rest.startsWith("//")) {
            return WalletEntryUri(scheme, encodedAuthority = null, encodedPath = rest)
        }
        val hierarchy = rest.substring(2)
        val authorityEnd = hierarchy.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .takeIf { it >= 0 } ?: hierarchy.length
        val afterAuthority = hierarchy.substring(authorityEnd)
        val pathEnd = afterAuthority.indexOfFirst { it == '?' || it == '#' }
            .takeIf { it >= 0 } ?: afterAuthority.length
        return WalletEntryUri(
            scheme = scheme,
            encodedAuthority = hierarchy.substring(0, authorityEnd),
            encodedPath = afterAuthority.substring(0, pathEnd)
        )
    }

    private fun candidateUris(pattern: String): List<WalletEntryUri> {
        val concrete = pattern.replace(Regex("\\{[^}]*\\}"), "1")
        // NavDeepLink accepts http and https for patterns without a scheme.
        val withScheme = if (concrete.contains("://")) {
            listOf(concrete)
        } else {
            listOf("http://$concrete", "https://$concrete")
        }
        return withScheme.map { value ->
            val parsed = URI(value)
            WalletEntryUri(
                scheme = parsed.scheme,
                encodedAuthority = parsed.rawAuthority,
                encodedPath = parsed.rawPath
            )
        }
    }

    private fun navigationDeepLinks(): List<NavigationDeepLink> {
        val root = repositoryRoot()
        // Every module's source sets, e.g. app/src/main/res/navigation/*.xml.
        val graphs = root.listFiles().orEmpty()
            .flatMap { module -> File(module, "src").listFiles().orEmpty().toList() }
            .map { sourceSet -> File(sourceSet, "res/navigation") }
            .filter { it.isDirectory }
            .flatMap { directory ->
                directory.listFiles { file -> file.extension == "xml" }
                    .orEmpty()
                    .toList()
            }
        assertTrue(graphs.isNotEmpty())

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return graphs.flatMap { graph ->
            val document = factory.newDocumentBuilder().parse(graph)
            val nodes = document.getElementsByTagName("deepLink")
            (0 until nodes.length).map { index ->
                val element = nodes.item(index) as Element
                NavigationDeepLink(
                    source = graph.relativeTo(root).path,
                    pattern = element.appAttribute("uri"),
                    action = element.appAttribute("action").ifEmpty { null },
                    mimeType = element.appAttribute("mimeType").ifEmpty { null }
                )
            }
        }
    }

    private fun Element.appAttribute(name: String): String = getAttributeNS(APP_NAMESPACE, name)

    private fun repositoryRoot(): File {
        val workingDirectory = System.getProperty("user.dir")
            ?: error("The test process has no working directory")
        return generateSequence(File(workingDirectory)) {
            it.parentFile
        }.firstOrNull {
            File(it, "settings.gradle").isFile &&
                File(it, "app").isDirectory
        } ?: error("Cannot locate the Fearless Android repository root")
    }

    private data class NavigationDeepLink(
        val source: String,
        val pattern: String,
        val action: String?,
        val mimeType: String?
    )

    private companion object {
        const val MAIN = "android.intent.action.MAIN"
        const val VIEW = "android.intent.action.VIEW"
        const val SEND = "android.intent.action.SEND"
        const val JSON = "application/json"
        const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
        const val DOCUMENT_SCHEME = "content"
        const val EXPECTED_MINIMUM_DEEP_LINKS = 5

        val ADMITTED_ORIGINS = setOf<Pair<String?, String?>>(
            Pair("fearless", "buy-success"),
            Pair("http", "fearlesswallet.io"),
            Pair("https", "fearlesswallet.io")
        )
    }
}
