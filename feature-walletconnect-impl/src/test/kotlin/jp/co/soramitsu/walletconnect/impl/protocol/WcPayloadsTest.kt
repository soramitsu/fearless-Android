package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WcPayloadsTest {

    @Test
    fun `metadata is cut to display limits and keeps only web icons`() {
        val metadata = WcPayloads.metadata(
            WcTestDapp.objectOf(
                """{"name":"Dapp\u0007${"n".repeat(300)}","description":"d","url":" https://app.example.org ",""" +
                    """"icons":["https://a/icon.png","javascript:alert(1)",5],"redirect":{"native":"app://x"}}"""
            )
        )

        assertEquals(WcLimits.MAX_NAME_DISPLAY_LENGTH, metadata.name.length)
        assertFalse(metadata.name.contains('\u0007'))
        assertEquals("https://app.example.org", metadata.url)
        assertEquals(listOf("https://a/icon.png"), metadata.icons)
        assertEquals("app://x", metadata.redirect)
    }

    @Test
    fun `metadata without an object or with a huge url is refused`() {
        assertThrows(WcProtocolException::class.java) { WcPayloads.metadata(null) }
        assertThrows(WcProtocolException::class.java) {
            WcPayloads.metadata(WcTestDapp.objectOf("""{"name":"x","url":"https://${"a".repeat(3000)}"}"""))
        }
    }

    @Test
    fun `namespaces keyed by chain merge into their namespace`() {
        val namespaces = WcPayloads.proposalNamespaces(
            WcTestDapp.objectOf(
                """{"eip155:1":{"methods":["personal_sign"],"events":["chainChanged"]},""" +
                    """"eip155":{"chains":["eip155:56"],"methods":["personal_sign","eth_sign"],"events":[]},""" +
                    """"polkadot":{"chains":["polkadot:91b171bb158e2d3848fa23a9f1c25182"],"methods":["polkadot_signMessage"],"events":[]}}"""
            )
        )

        assertEquals(setOf("eip155", "polkadot"), namespaces.keys)
        assertEquals(listOf("eip155:1", "eip155:56"), namespaces.getValue("eip155").chains)
        assertEquals(listOf("personal_sign", "eth_sign"), namespaces.getValue("eip155").methods)
        assertTrue(WcPayloads.proposalNamespaces(JsonNull).isEmpty())
    }

    @Test
    fun `malformed namespaces are refused with the protocol code`() {
        val cases = mapOf(
            """{"eip155":{"chains":["solana:1"],"methods":[],"events":[]}}""" to WcErrors.UNSUPPORTED_CHAINS,
            """{"eip155":{"chains":["eip155"],"methods":[],"events":[]}}""" to WcErrors.UNSUPPORTED_CHAINS,
            """{"EIP155":{"chains":["eip155:1"],"methods":[],"events":[]}}""" to WcErrors.UNSUPPORTED_NAMESPACE_KEY,
            """{"eip155:x y":{"methods":[],"events":[]}}""" to WcErrors.UNSUPPORTED_NAMESPACE_KEY,
            """{"eip155":{"chains":["eip155:1"],"methods":["has space"],"events":[]}}""" to WcErrors.UNSUPPORTED_METHODS,
            """{"eip155":{"chains":["eip155:1"],"methods":[],"events":[7]}}""" to WcErrors.INVALID_EVENT,
            """{"eip155":"x"}""" to WcErrors.INVALID_REQUEST
        )
        cases.forEach { (json, code) ->
            val error = assertThrows(json, WcProtocolException::class.java) { WcPayloads.proposalNamespaces(WcTestDapp.objectOf(json)) }
            assertEquals(json, code, error.code)
        }
        val many = (1..17).joinToString(",", "{", "}") { """"ns$it":{"chains":[],"methods":[],"events":[]}""" }
        assertEquals(WcErrors.LIMIT_EXCEEDED, assertThrows(WcProtocolException::class.java) { WcPayloads.proposalNamespaces(WcTestDapp.objectOf(many)) }.code)
    }

    @Test
    fun `approval checks every namespace, chain, method, event and account`() {
        val proposal = proposal()
        val good = mapOf("eip155" to WcStoredSessionNamespace(listOf("eip155:1"), listOf("eip155:1:0xabc"), listOf("personal_sign"), listOf("accountsChanged")))
        WcPayloads.checkApproval(proposal, good)

        val bad = listOf(
            good.mapValues { it.value.copy(chains = listOf("eip155:2")) },
            good.mapValues { it.value.copy(methods = listOf("eth_sign")) },
            good.mapValues { it.value.copy(events = listOf("disconnect")) },
            good.mapValues { it.value.copy(accounts = listOf("eip155:2:0xabc")) },
            good.mapValues { it.value.copy(accounts = emptyList()) },
            good.mapValues { it.value.copy(accounts = listOf("not-caip10")) },
            mapOf("polkadot" to good.getValue("eip155")),
            emptyMap()
        )
        bad.forEach { namespaces ->
            assertThrows(namespaces.toString(), WcProtocolException::class.java) { WcPayloads.checkApproval(proposal, namespaces) }
        }
    }

    @Test
    fun `session namespaces are written as settle sends them`() {
        val json = WcPayloads.sessionNamespacesJson(
            mapOf("eip155" to WcStoredSessionNamespace(null, listOf("eip155:1:0xabc"), listOf("personal_sign"), emptyList()))
        )

        val namespace = json.getValue("eip155").jsonObject
        assertNull(namespace["chains"])
        assertEquals("""["eip155:1:0xabc"]""", namespace.getValue("accounts").toString())
        assertEquals("""{"eip155":{"accounts":["eip155:1:0xabc"],"methods":["personal_sign"],"events":[]}}""", json.toString())
    }

    @Test
    fun `approved chains include the chains of approved accounts`() {
        val namespace = WcStoredSessionNamespace(listOf("eip155:1"), listOf("eip155:1:0xabc", "eip155:10:0xabc"), emptyList(), emptyList())

        assertEquals(setOf("eip155:1", "eip155:10"), WcPayloads.approvedChains(namespace))
    }

    @Test
    fun `caip ids and names follow the specification`() {
        assertTrue(WcLimits.isCaip2("eip155:1"))
        assertTrue(WcLimits.isCaip2("polkadot:91b171bb158e2d3848fa23a9f1c25182"))
        assertFalse(WcLimits.isCaip2("eip155:"))
        assertFalse(WcLimits.isCaip2("e:1"))
        assertTrue(WcLimits.isCaip10("eip155:1:0x8ba1f109551bd432803012645ac136ddd64dba72"))
        assertFalse(WcLimits.isCaip10("eip155:1"))
        assertTrue(WcLimits.isName("eth_signTypedData_v4"))
        assertFalse(WcLimits.isName(""))
        assertEquals("ab", WcLimits.displayText("a\u0000b", 10))
        assertTrue(WcLimits.isNamespaceKey("eip155"))
    }

    private fun proposal() = WcStoredProposal(
        id = 1,
        pairingTopic = "p",
        proposerPublicKey = "00".repeat(32),
        proposer = WcStoredMetadata("d", "d", "https://d", emptyList()),
        requiredNamespaces = emptyMap(),
        optionalNamespaces = mapOf("eip155" to WcStoredProposalNamespace(listOf("eip155:1"), listOf("personal_sign"), listOf("accountsChanged"))),
        relayProtocol = "irn",
        expiry = 0,
        verify = WcStoredVerify("UNKNOWN")
    )
}
