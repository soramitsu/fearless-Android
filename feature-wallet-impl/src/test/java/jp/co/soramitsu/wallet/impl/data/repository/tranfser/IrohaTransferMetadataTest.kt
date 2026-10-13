package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IrohaTransferMetadataTest {

    @Test
    fun `wallet smoke factory emits exact canonical all-string contract`() {
        val metadata = IrohaTransferMetadata.walletSmoke(ROUTE_HASH, WALLET_COMMIT)

        assertEquals(
            listOf(
                "evidence_role",
                "route_governance_action_hash",
                "wallet_platform",
                "wallet_commit"
            ),
            metadata.asStringMap().keys.toList()
        )
        assertEquals(
            linkedMapOf(
                "evidence_role" to "wallet-smoke",
                "route_governance_action_hash" to ROUTE_HASH,
                "wallet_platform" to "android",
                "wallet_commit" to WALLET_COMMIT
            ),
            metadata.asStringMap()
        )
        val untypedMetadata: Map<*, *> = metadata.asStringMap()
        assertTrue(untypedMetadata.values.all { it is String })
        assertFalse(metadata.isEmpty())
    }

    @Test
    fun `explicit wallet smoke path preserves ordinary empty metadata and fail closed signer selection`() {
        val ordinary = signingRequest()
        val evidence = ordinary.withWalletSmokeMetadata(ROUTE_HASH, WALLET_COMMIT)

        assertTrue(ordinary.transactionMetadata.isEmpty())
        assertEquals(IrohaTransferMetadata.empty(), ordinary.transactionMetadata)
        assertEquals("wallet-smoke", evidence.transactionMetadata.asStringMap()["evidence_role"])
        assertEquals("android", evidence.transactionMetadata.asStringMap()["wallet_platform"])
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { UnavailableIrohaTransferSigner.buildAndSignTransfer(evidence) }
        }
        assertTrue(error.message.orEmpty().contains("codec is unavailable"))
    }

    @Test
    fun `rejects malformed wallet smoke metadata before signer or torii calls`() {
        var signerCalls = 0
        var toriiCalls = 0

        invalidMetadataCases().forEach { (metadata, expectedMessage) ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                val request = signingRequest().withWalletSmokeMetadata(metadata)
                signerCalls += 1
                check(request.transactionMetadata.asStringMap().isNotEmpty())
                toriiCalls += 1
            }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(expectedMessage))
        }

        validMetadata().keys.forEach { key ->
            val missing = validMetadata().apply { remove(key) }
            val error = assertThrows(IllegalArgumentException::class.java) {
                signingRequest().withWalletSmokeMetadata(missing)
                signerCalls += 1
                toriiCalls += 1
            }
            assertTrue(error.message.orEmpty().contains("exactly"))
        }

        assertEquals(0, signerCalls)
        assertEquals(0, toriiCalls)
    }

    @Test
    fun `metadata snapshots mutable input and returns immutable defensive copies`() {
        val supplied = validMetadata()
        val metadata = IrohaTransferMetadata.walletSmoke(supplied)
        supplied.clear()
        supplied["attacker"] = "injected"

        val first = metadata.asStringMap()
        val second = metadata.asStringMap()
        assertEquals(validMetadata(), first)
        assertNotSame(first, second)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (first as MutableMap<String, String>)["attacker"] = "injected"
        }
        assertEquals(validMetadata(), metadata.asStringMap())
        assertFalse(metadata.toString().contains(ROUTE_HASH))
        assertFalse(metadata.toString().contains(WALLET_COMMIT))
    }

    @Test
    fun `untrusted input order is normalized before codec handoff`() {
        val reversed = linkedMapOf<Any?, Any?>(
            "wallet_commit" to WALLET_COMMIT,
            "wallet_platform" to "android",
            "route_governance_action_hash" to ROUTE_HASH,
            "evidence_role" to "wallet-smoke"
        )

        assertEquals(
            validMetadata().keys.toList(),
            IrohaTransferMetadata.walletSmoke(reversed).asStringMap().keys.toList()
        )
    }

    @Test
    fun `wallet smoke metadata is rejected outside exact Nexus global context`() {
        val tairaError = assertThrows(IllegalArgumentException::class.java) {
            signingRequest().copy(
                network = "taira",
                chainId = "iroha3-taira"
            ).withWalletSmokeMetadata(validMetadata())
        }
        assertTrue(tairaError.message.orEmpty().contains("network=nexus"))

        val wrongChainError = assertThrows(IllegalArgumentException::class.java) {
            signingRequest().copy(
                chainId = "SORA:NEXUS:GLOBAL"
            ).withWalletSmokeMetadata(validMetadata())
        }
        assertTrue(wrongChainError.message.orEmpty().contains("chainId=sora:nexus:global"))
    }

    @Test
    fun `validated wallet smoke attachment cannot downgrade to empty metadata`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            signingRequest().withValidatedWalletSmokeMetadata(IrohaTransferMetadata.empty())
        }

        assertTrue(error.message.orEmpty().contains("must not be empty"))
    }

    private fun invalidMetadataCases(): List<Pair<Map<*, *>, String>> {
        return listOf(
            mutate { put("unexpected", "value") } to "exactly",
            mutate {
                val value = getValue("evidence_role")
                remove("evidence_role")
                put("Evidence_role", value)
            } to "exactly",
            mutate {
                val value = getValue("route_governance_action_hash")
                remove("route_governance_action_hash")
                put(
                    "route_Governance_action_hash",
                    value
                )
            } to "exactly",
            mutate {
                val value = getValue("wallet_platform")
                remove("wallet_platform")
                put("Wallet_platform", value)
            } to "exactly",
            mutate {
                val value = getValue("wallet_commit")
                remove("wallet_commit")
                put("wallet_Commit", value)
            } to "exactly",
            mutate { put("evidence_role", "wallet_smoke") } to "evidence_role",
            mutate { put("evidence_role", "Wallet-Smoke") } to "evidence_role",
            mutate { put("evidence_role", "wallet-smoke\n") } to "evidence_role",
            mutate { put("wallet_platform", "Android") } to "wallet_platform",
            mutate { put("wallet_platform", "ios") } to "wallet_platform",
            mutate { put("wallet_platform", "android\u0000") } to "wallet_platform",
            mutate { put("route_governance_action_hash", "") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "sha256:") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "SHA256:${"a".repeat(64)}") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "a".repeat(64)) } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "sha256:${"a".repeat(63)}") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "sha256:${"a".repeat(65)}") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "sha256:${"A".repeat(64)}") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "sha256:${"g".repeat(64)}") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "sha256:${"0".repeat(64)}") } to "all-zero",
            mutate { put("route_governance_action_hash", "sha256:${"a".repeat(63)}\n") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", " sha256:${"a".repeat(64)}") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "sha256:${"a".repeat(64)} ") } to "route_governance_action_hash",
            mutate { put("route_governance_action_hash", "sha256:${"１".repeat(64)}") } to "route_governance_action_hash",
            mutate { put("wallet_commit", "") } to "wallet_commit",
            mutate { put("wallet_commit", "a".repeat(39)) } to "wallet_commit",
            mutate { put("wallet_commit", "a".repeat(41)) } to "wallet_commit",
            mutate { put("wallet_commit", "A".repeat(40)) } to "wallet_commit",
            mutate { put("wallet_commit", "g".repeat(40)) } to "wallet_commit",
            mutate { put("wallet_commit", "0".repeat(40)) } to "all-zero",
            mutate { put("wallet_commit", "sha256:${"a".repeat(40)}") } to "wallet_commit",
            mutate { put("wallet_commit", "${"a".repeat(39)}\n") } to "wallet_commit",
            mutate { put("wallet_commit", " ${"a".repeat(40)}") } to "wallet_commit",
            mutate { put("wallet_commit", "${"a".repeat(40)} ") } to "wallet_commit",
            mutate { put("wallet_commit", "１".repeat(40)) } to "wallet_commit",
            mutateAny { put("wallet_commit", 7) } to "JSON strings",
            mutateAny { put("wallet_commit", true) } to "JSON strings",
            mutateAny { put("wallet_commit", null) } to "JSON strings",
            mutateAny { put(null, "value") } to "exactly"
        )
    }

    private fun mutate(block: LinkedHashMap<String, String>.() -> Unit): Map<String, String> {
        return validMetadata().apply(block)
    }

    private fun mutateAny(block: LinkedHashMap<Any?, Any?>.() -> Unit): Map<*, *> {
        return LinkedHashMap<Any?, Any?>().apply {
            putAll(validMetadata())
            block()
        }
    }

    private fun validMetadata(): LinkedHashMap<String, String> {
        return linkedMapOf(
            "evidence_role" to "wallet-smoke",
            "route_governance_action_hash" to ROUTE_HASH,
            "wallet_platform" to "android",
            "wallet_commit" to WALLET_COMMIT
        )
    }

    private fun signingRequest(): IrohaTransferSigningRequest {
        return IrohaTransferSigningRequest(
            amount = "1.25",
            assetDefinitionId = "asset#domain",
            authority = "authority",
            chainId = "sora:nexus:global",
            derivationPath = "m/44'/617'/0'/0'",
            destinationAccountId = "destination",
            mnemonicOrSeed = "test mnemonic",
            network = "nexus",
            signingPublicKeyHex = "11".repeat(32),
            sourceAccountId = "authority",
            sourceAssetId = "asset#domain#authority"
        )
    }

    private companion object {
        const val ROUTE_HASH =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val WALLET_COMMIT = "0123456789abcdef0123456789abcdef01234567"
    }
}
